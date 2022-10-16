package com.xxl.job.core.thread;

import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.enums.RegistryConfig;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.util.FileUtil;
import com.xxl.job.core.util.JdkSerializeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by jing
 * 触发回调函数  线程
 * 该线程主要是任务执行器执行完后 将结果 回调给 调度中心
 */
public class TriggerCallbackThread {

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(TriggerCallbackThread.class);
//  创建静态对象
    private static TriggerCallbackThread instance = new TriggerCallbackThread();
//   获取当前对象  方法
    public static TriggerCallbackThread getInstance(){
        return instance;
    }

    /**
     * job results callback queue
     * 本地的缓存队列
     * 作业结果回调队列
     */
    private LinkedBlockingQueue<HandleCallbackParam> callBackQueue = new LinkedBlockingQueue<HandleCallbackParam>();

    /**
     * job results callback queue
     * 将  回调信息 放到 队列里面
     */
    public static void pushCallBack(HandleCallbackParam callback){
        getInstance().callBackQueue.add(callback);
        logger.debug(">>>>>>>>>>> xxl-job, push callback request, logId:{}", callback.getLogId());
    }

    /**
     * callback thread
     */
//    触发线程
    private Thread triggerCallbackThread;
//    触发重试 线程
    private Thread triggerRetryCallbackThread;
//    定义  停止标识
    private volatile boolean toStop = false;

    /**
     *  线程开始，我们的项目一启动，就走这个方法
     */
    public void start() {

        // valid    获取web服务器地址与令牌的  list集合，这个在我们自己项目启动的时候就赋值了
        if (XxlJobExecutor.getAdminBizList() == null) {
            logger.warn(">>>>>>>>>>> xxl-job, executor callback config fail, adminAddresses is null.");
            return;
        }

        // callback  触发  回调函数 线程
        triggerCallbackThread = new Thread(new Runnable() {

            @Override
            public void run() {

                // normal callback   正常 调用
                while(!toStop){
//                    当前线程没有停止
                    try {
//                        take()方法从队列中消费数据，当队列为空是，线程阻塞

//                        消费其中的  一个 回调函数信息
                        HandleCallbackParam callback = getInstance().callBackQueue.take();
                        if (callback != null) {

                            // callback list param  回调参数列表
                            List<HandleCallbackParam> callbackParamList = new ArrayList<HandleCallbackParam>();
                            // take方法是逐一获取队列中的元素，为空就阻塞，
                            // 而drainTo是批量获取，为空不阻塞

                            // drainTo实现将当前阻塞队列中的信息全部转移到List中
                            int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);
                            callbackParamList.add(callback);

                            // callback, will retry if error
//                            回调函数，如果发生错误将重试
                            if (callbackParamList!=null && callbackParamList.size()>0) {
                                doCallback(callbackParamList);
                            }
                        }
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }




                // last callback  当前项目停止的 时候，调用这个
                try {
                    List<HandleCallbackParam> callbackParamList = new ArrayList<HandleCallbackParam>();
                    int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);
                    if (callbackParamList!=null && callbackParamList.size()>0) {
                        doCallback(callbackParamList);
                    }
                } catch (Exception e) {
                    if (!toStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, executor callback thread destroy.");

            }
        });
        triggerCallbackThread.setDaemon(true);
        triggerCallbackThread.setName("xxl-job, executor TriggerCallbackThread");
        triggerCallbackThread.start();






        // retry  重试
        triggerRetryCallbackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!toStop){
                    try {
//                        重试函数
                        retryFailCallbackFile();
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }

                    }
                    try {

//                        休眠事假
                        TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
                    } catch (InterruptedException e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, executor retry callback thread destroy.");
            }
        });
        triggerRetryCallbackThread.setDaemon(true);
        triggerRetryCallbackThread.start();

    }

    /**
     *  触发线程  停止
     *  触发重试 线程  停止
     */
    public void toStop(){
        toStop = true;
        // stop callback, interrupt and wait
        if (triggerCallbackThread != null) {    // support empty admin address
            triggerCallbackThread.interrupt();
            try {
                triggerCallbackThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        // stop retry, interrupt and wait
        if (triggerRetryCallbackThread != null) {
            triggerRetryCallbackThread.interrupt();
            try {
                triggerRetryCallbackThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

    }

    /**
     * do callback, will retry if error
     *  我们的项目里面  任务执行完成，执行错误的话，有错误结果，都在list里面
     * @param callbackParamList
     */
    private void doCallback(List<HandleCallbackParam> callbackParamList){
        boolean callbackRet = false;
        // callback, will retry if error
//         获取web    服务器地址与令牌的  list集合
        for (AdminBiz adminBiz: XxlJobExecutor.getAdminBizList()) {
            try {
//                返回的是  调用远程之后得到的结果数据
                ReturnT<String> callbackResult = adminBiz.callback(callbackParamList);
                if (callbackResult!=null && ReturnT.SUCCESS_CODE == callbackResult.getCode()) {


                    callbackLog(callbackParamList, "<br>----------- xxl-job job callback finish.");
                    callbackRet = true;
                    break;
                } else {
                    callbackLog(callbackParamList, "<br>----------- xxl-job job callback fail, callbackResult:" + callbackResult);
                }
            } catch (Exception e) {
                callbackLog(callbackParamList, "<br>----------- xxl-job job callback error, errorMsg:" + e.getMessage());
            }
        }


        if (!callbackRet) {
            appendFailCallbackFile(callbackParamList);
        }
    }

    /**
     * callback log
     */
    private void callbackLog(List<HandleCallbackParam> callbackParamList, String logContent){
//        遍历每一个回调函数信息
        for (HandleCallbackParam callbackParam: callbackParamList) {
//            创建日志文件名称
            String logFileName = XxlJobFileAppender.makeLogFileName(new Date(callbackParam.getLogDateTim()), callbackParam.getLogId());

//           上下文list  里面 设置信息
            XxlJobContext.setXxlJobContext(new XxlJobContext(
                    -1,
                    null,
                    logFileName,
                    -1,
                    -1));

//            添加信息 到 log 文件里面
            XxlJobHelper.log(logContent);
        }
    }


    // ---------------------- fail-callback file  失败回调文件----------------------

//    /data/applogs/xxl-job/jobhandler\callbacklog\
    private static String failCallbackFilePath = XxlJobFileAppender.getLogPath().concat(File.separator).concat("callbacklog").concat(File.separator);
//    /data/applogs/xxl-job/jobhandler\callbacklog\xxl-job-callback-{x}.log
    private static String failCallbackFileName = failCallbackFilePath.concat("xxl-job-callback-{x}").concat(".log");

//    public static void main(String[] args) {
//        File callbackLogFile = new File(failCallbackFileName.replace("{x}", String.valueOf(System.currentTimeMillis())));
//        System.out.println(callbackLogFile.getPath());
//    }

    /**
     *   添加回调失败信息  到  log文件里面
     */
    private void appendFailCallbackFile(List<HandleCallbackParam> callbackParamList){
        // valid
        if (callbackParamList==null || callbackParamList.size()==0) {
            return;
        }

        // append file    将对象-->byte[]
        byte[] callbackParamList_bytes = JdkSerializeTool.serialize(callbackParamList);

//        \data\applogs\xxl-job\jobhandler\callbacklog\xxl-job-callback-1658216732185.log
        File callbackLogFile = new File(failCallbackFileName.replace("{x}", String.valueOf(System.currentTimeMillis())));
        if (callbackLogFile.exists()) {
            for (int i = 0; i < 100; i++) {
                callbackLogFile = new File(failCallbackFileName.replace("{x}", String.valueOf(System.currentTimeMillis()).concat("-").concat(String.valueOf(i)) ));
                if (!callbackLogFile.exists()) {
                    break;
                }
            }
        }
        FileUtil.writeFileContent(callbackLogFile, callbackParamList_bytes);
    }


    /**
     *   重试  添加回调失败信息  到  log文件里面
     */
    private void retryFailCallbackFile(){

        // valid
        File callbackLogPath = new File(failCallbackFilePath);
        if (!callbackLogPath.exists()) {
            return;
        }
        if (callbackLogPath.isFile()) {
            callbackLogPath.delete();
        }
        if (!(callbackLogPath.isDirectory() && callbackLogPath.list()!=null && callbackLogPath.list().length>0)) {
            return;
        }



        // load and clear file, retry  加载清除文件  ，重试
        for (File callbaclLogFile: callbackLogPath.listFiles()) {
//            读取每一个文件的内容
            byte[] callbackParamList_bytes = FileUtil.readFileContent(callbaclLogFile);

            // avoid empty file   删除空文件
            if(callbackParamList_bytes == null || callbackParamList_bytes.length < 1){
                callbaclLogFile.delete();
                continue;
            }

//            将数组文件  转为  list
            List<HandleCallbackParam> callbackParamList = (List<HandleCallbackParam>) JdkSerializeTool.deserialize(callbackParamList_bytes, List.class);

//            删除
            callbaclLogFile.delete();
            doCallback(callbackParamList);
        }

    }

}
