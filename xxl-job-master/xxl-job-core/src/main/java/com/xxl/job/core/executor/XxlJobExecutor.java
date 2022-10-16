package com.xxl.job.core.executor;

import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.client.AdminBizClient;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.server.EmbedServer;
import com.xxl.job.core.thread.JobLogFileCleanThread;
import com.xxl.job.core.thread.JobThread;
import com.xxl.job.core.thread.TriggerCallbackThread;
import com.xxl.job.core.util.IpUtil;
import com.xxl.job.core.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by jing
 * 定时任务执行器
 */
public class XxlJobExecutor  {

//    创建日志对象
    private static final Logger logger = LoggerFactory.getLogger(XxlJobExecutor.class);

    // ---------------------- param ----------------------
    private String adminAddresses;   // 用户地址
    private String accessToken; // token令牌
    private String appname;  // APP名称
    private String address; // 服务地址
    private String ip; // IP
    private int port; // 端口
    private String logPath; // 日志地址
    private int logRetentionDays; // 日志保留天数

    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    public void setAppname(String appname) {
        this.appname = appname;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }


    // ---------------------- start + stop ----------------------

    /**
     * Created by jing
     * 开始执行 任务
     */
    public void start() throws Exception {

        // init logpath  先初始化保存日志路径
//        这个是   自定义的日志路径
        XxlJobFileAppender.initLogPath(logPath);

        // init invoker, admin-client
        // 初始化 web 服务器的地址 与 令牌
        initAdminBizList(adminAddresses, accessToken);


        // init JobLogFileCleanThread
        // 开始删除 超过保留日期的  全部日志
        JobLogFileCleanThread.getInstance().start(logRetentionDays);

        // init TriggerCallbackThread
//        初始化  日志回调函数信息
//        该线程主要是任务执行器执行完后 将结果 回调给 调度中心
        TriggerCallbackThread.getInstance().start();

        // init executor-server 将当前项目的地址注册到  web项目里面
//        任务执行器核心代码，基于Netty编写Server端，与调度中心进行通信
        initEmbedServer(address, ip, port, appname, accessToken);
    }


    /**
     * Created by jing
     * 停止执行 任务
     */
    public void destroy(){
        // destroy executor-server  销毁执行器
        stopEmbedServer();

        // destroy jobThreadRepository
        if (jobThreadRepository.size() > 0) {
            for (Map.Entry<Integer, JobThread> item: jobThreadRepository.entrySet()) {
                JobThread oldJobThread = removeJobThread(item.getKey(), "web container destroy and kill the job.");
                // wait for job thread push result to callback queue
                if (oldJobThread != null) {
                    try {
                        oldJobThread.join();
                    } catch (InterruptedException e) {
                        logger.error(">>>>>>>>>>> xxl-job, JobThread destroy(join) error, jobId:{}", item.getKey(), e);
                    }
                }
            }
            jobThreadRepository.clear();
        }
        jobHandlerRepository.clear();


        // destroy JobLogFileCleanThread
        JobLogFileCleanThread.getInstance().toStop();

        // destroy TriggerCallbackThread
        TriggerCallbackThread.getInstance().toStop();

    }







    // ---------------------- admin-client (rpc invoker  调用程序)) ----------------------

//    服务器地址 和  令牌 创建的对象集合
    private static List<AdminBiz> adminBizList;


    /**
     * 初始化服务器 地址 与令牌
     * web项目
     */
    private void initAdminBizList(String adminAddresses, String accessToken) throws Exception {
        if (adminAddresses!=null && adminAddresses.trim().length()>0) {
//            遍历服务器  地址
            for (String address: adminAddresses.trim().split(",")) {
                if (address!=null && address.trim().length()>0) {

//                   根据服务器 地址  和  令牌创建对象
                    AdminBiz adminBiz = new AdminBizClient(address.trim(), accessToken);

                    if (adminBizList == null) {
                        adminBizList = new ArrayList<AdminBiz>();
                    }
//                    往list 里面存储 服务器地址与令牌的  对象
                    adminBizList.add(adminBiz);
                }
            }
        }
    }


//    获取服务器地址与令牌的  list集合
    public static List<AdminBiz> getAdminBizList(){
        return adminBizList;
    }

    // ---------------------- executor-server (rpc provider) ----------------------
    private EmbedServer embedServer = null;







    /**
     * 初始化服务器
     */
    private void initEmbedServer(String address, String ip, int port, String appname, String accessToken) throws Exception {

        // fill ip port   获取 IP 和  端口
        port = port>0?port: NetUtil.findAvailablePort(9999);
        ip = (ip!=null&&ip.trim().length()>0)?ip: IpUtil.getIp();

        // generate address  获取到当前项目的服务器地址  http://{ip_port}/
        if (address==null || address.trim().length()==0) {
//            根据IP  和  端口，拼接为地址
            String ip_port_address = IpUtil.getIpPort(ip, port);   // registry-address：default use address to registry , otherwise use ip:port if address is null
            address = "http://{ip_port}/".replace("{ip_port}", ip_port_address);
        }

        // accessToken
        if (accessToken==null || accessToken.trim().length()==0) {
            logger.warn(">>>>>>>>>>> xxl-job accessToken is empty. To ensure system security, please set the accessToken.");
        }

        // start  进行注册
        embedServer = new EmbedServer();
        embedServer.start(address, port, appname, accessToken);
    }



    /**
     * 停止 嵌入的  执行器
     */
    private void stopEmbedServer() {
        // stop provider factory
        if (embedServer != null) {
            try {
                embedServer.stop();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }


    // ---------------------- job handler repository ----------------------
//    xxl-job  注解的值   map对象
    private static ConcurrentMap<String, IJobHandler> jobHandlerRepository = new ConcurrentHashMap<String, IJobHandler>();

//   根据 注解的值名称
    public static IJobHandler loadJobHandler(String name){

//      从  ConcurrentMap<String, IJobHandler>  里面获取处理器
        return jobHandlerRepository.get(name);
    }


//    注册  到 map 里面 ，key为注解名称，value为 方法实体类
    public static IJobHandler registJobHandler(String name, IJobHandler jobHandler){
        logger.info(">>>>>>>>>>> xxl-job register jobhandler success, name:{}, jobHandler:{}", name, jobHandler);
        return jobHandlerRepository.put(name, jobHandler);
    }
    /**
     * xxlJob 是注解   bean是类对象  executeMethod 方法对象
     * 每一个  bean对象 里面的每一个方法
     */
    protected void registJobHandler(XxlJob xxlJob, Object bean, Method executeMethod){
//        如果 注解为空，直接返回
        if (xxlJob == null) {
            return;
        }

//        获取注解的值，注解的名称
        String name = xxlJob.value();
//        制定并简化变量，因为它们以后会被多次调用
        //make and simplify the variables since they'll be called several times later
//       获取类对象
        Class<?> clazz = bean.getClass();
//        获取方法的名称
        String methodName = executeMethod.getName();
        if (name.trim().length() == 0) {
//            如果注解的名称为空   ，名称无效，直接返回报错
            throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
//        根据 注解的值名称 获取处理器
        if (loadJobHandler(name) != null) {
//          如果在map 里面可以获取到以当前注解名称为key 的值，那么  命名冲突，直接返回报错
            throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
        }

        // execute method
        /*if (!(method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isAssignableFrom(String.class))) {
            throw new RuntimeException("xxl-job method-jobhandler param-classtype invalid, for[" + bean.getClass() + "#" + method.getName() + "] , " +
                    "The correct method format like \" public ReturnT<String> execute(String param) \" .");
        }
        if (!method.getReturnType().isAssignableFrom(ReturnT.class)) {
            throw new RuntimeException("xxl-job method-jobhandler return-classtype invalid, for[" + bean.getClass() + "#" + method.getName() + "] , " +
                    "The correct method format like \" public ReturnT<String> execute(String param) \" .");
        }*/


//        方法可通过
        executeMethod.setAccessible(true);

        // init and destroy
        Method initMethod = null;  // 先初始化的方法
        Method destroyMethod = null; // 销毁的方法

//        在当前方法之前，先初始化其他方法
        if (xxlJob.init().trim().length() > 0) {
            try {
                initMethod = clazz.getDeclaredMethod(xxlJob.init());
                initMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }


//        在当前方法之前，先销毁其他的  方法
        if (xxlJob.destroy().trim().length() > 0) {
            try {
                destroyMethod = clazz.getDeclaredMethod(xxlJob.destroy());
                destroyMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        // registry jobhandler   注册表作业处理程序。
//        name 是注解的名称，
        registJobHandler(name, new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod));

    }







    // ---------------------- job thread repository 工作线程库  和 JobThread 类相关的  ----------------------

    //    创建一个JobThread   map对象
    private static ConcurrentMap<Integer, JobThread> jobThreadRepository = new ConcurrentHashMap<Integer, JobThread>();
    /**
     *  注册任务线程， web端执行任务的时候，调用到我们自己的项目，如果第一次，就注册任务
     */
    public static JobThread registJobThread(int jobId, IJobHandler handler, String removeOldReason){

//      在我们的项目里    创建 任务  线程
        JobThread newJobThread = new JobThread(jobId, handler);
//        启动线程
        newJobThread.start();
        logger.info(">>>>>>>>>>> xxl-job regist JobThread success, jobId:{}, handler:{}", new Object[]{jobId, handler});

//        将当前的任务线程  放到 map集合里,返回 旧的线程
        JobThread oldJobThread = jobThreadRepository.put(jobId, newJobThread);	// putIfAbsent | oh my god, map's put method return the old value!!!
        if (oldJobThread != null) {
//            杀死旧 线程
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }

        return newJobThread;
    }
    /**
     * Created by jing
     * ConcurrentMap  里面 移除任务线程
     * 调度中心  删除 线程
     */
    public static JobThread removeJobThread(int jobId, String removeOldReason){
        //  从 map 里面删除线程
        JobThread oldJobThread = jobThreadRepository.remove(jobId);
        if (oldJobThread != null) {
            //  将线程的标识  stop 变成  true
            oldJobThread.toStop(removeOldReason);
            //  中断线程
            oldJobThread.interrupt();
            return oldJobThread;
        }
        return null;
    }
    /**
     * Created by jing
     * 根据  jobid 从ConcurrentMap  获取线程任务
     */
    public static JobThread loadJobThread(int jobId){
        return jobThreadRepository.get(jobId);
    }
}
