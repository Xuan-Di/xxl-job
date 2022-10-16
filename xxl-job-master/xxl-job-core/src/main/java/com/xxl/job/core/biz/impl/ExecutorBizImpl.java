package com.xxl.job.core.biz.impl;

import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.model.*;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.impl.GlueJobHandler;
import com.xxl.job.core.handler.impl.ScriptJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.thread.JobThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Created by jing
 * web项目  调用  我们项目，在我们的项目里面  进行执行   的实现类
 */
public class ExecutorBizImpl implements ExecutorBiz {

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(ExecutorBizImpl.class);


    /**
     *  心跳检测
     */
    @Override
    public ReturnT<String> beat() {
        return ReturnT.SUCCESS;
    }

    /**
     *  任务id实体类
     *  判断 任务id 的线程  是不是运行  或者在  队列里
     *  忙碌检测
     *  IdleBeatParam  里面只有一个任务id 字段
     *  根据 jobId 从任务线程仓库 ConcurrentMap<Integer, JobThread> jobThreadRepository 中获取对应的任务线程；
     * 如果任务线程对象不为空且正在运行或者正在队列中则认为处于忙碌状态。
     */
    @Override
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam) {

        // isRunningOrHasQueue  是正在运行 或者 在队里面
        boolean isRunningOrHasQueue = false;

        // 从map 里面获取任务id的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(idleBeatParam.getJobId());
        if (jobThread != null && jobThread.isRunningOrHasQueue()) {
//          线程  正在运行 或者 在队里面
            isRunningOrHasQueue = true;
        }

//      如果  正在运行 或者 在队里面
        if (isRunningOrHasQueue) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "job thread is running or has trigger queue.");
        }
        return ReturnT.SUCCESS;
    }


    /**
     *  TriggerParam 为触发器 对象，就是哪个方法需要触发
     *  触发任务
     *
     *  web  端传过来的 run 方法，就是触发方法
     *  在我们的项目里面执行
     */
    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
        // load old：jobHandler + jobThread
        // 根据任务id  从map里面获取到任务线程
        // 从 我们的项目 里面 获取到   任务线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(triggerParam.getJobId());
        // 当个xxljob注解  注释的方法的对象

//        加了xxljob注解的方法对象   第一次执行为空
        IJobHandler jobHandler = jobThread!=null?jobThread.getHandler():null;


        String removeOldReason = null;

        // valid：jobHandler + jobThread

//        运行模式， 根据 web端  传过来的信息
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());
        if (GlueTypeEnum.BEAN == glueTypeEnum) {

            // new jobhandler
//            从集合里面  获取到  单个方法的实体类
            IJobHandler newJobHandler = XxlJobExecutor.loadJobHandler(triggerParam.getExecutorHandler());

            // valid old jobThread
            if (jobThread!=null && jobHandler != newJobHandler) {
                // change handler, need kill old thread
//                更改处理程序，需要杀死旧线程
                removeOldReason = "change jobhandler or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                jobHandler = newJobHandler;
                if (jobHandler == null) {
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "job handler [" + triggerParam.getExecutorHandler() + "] not found.");
                }
            }

        }

        else if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {

            // valid old jobThread
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof GlueJobHandler
                        && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                // change handler or gluesource updated, need kill old thread
                removeOldReason = "change job source or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                try {
                    IJobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
                    jobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    return new ReturnT<String>(ReturnT.FAIL_CODE, e.getMessage());
                }
            }
        }

        else if (glueTypeEnum!=null && glueTypeEnum.isScript()) {

            // valid old jobThread
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof ScriptJobHandler
                            && ((ScriptJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                // change script or gluesource updated, need kill old thread
                removeOldReason = "change job source or glue type, and terminate the old job thread.";

                jobThread = null;
                jobHandler = null;
            }

            // valid handler
            if (jobHandler == null) {
                jobHandler = new ScriptJobHandler(triggerParam.getJobId(), triggerParam.getGlueUpdatetime(), triggerParam.getGlueSource(), GlueTypeEnum.match(triggerParam.getGlueType()));
            }
        }

        else {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "glueType[" + triggerParam.getGlueType() + "] is not valid.");
        }




        // executor block strategy
//        如果  jobThread  线程不为空
        if (jobThread != null) {
            ExecutorBlockStrategyEnum blockStrategy = ExecutorBlockStrategyEnum.match(triggerParam.getExecutorBlockStrategy(), null);
            if (ExecutorBlockStrategyEnum.DISCARD_LATER == blockStrategy) {
                // discard when running
                if (jobThread.isRunningOrHasQueue()) {
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "block strategy effect："+ExecutorBlockStrategyEnum.DISCARD_LATER.getTitle());
                }
            } else if (ExecutorBlockStrategyEnum.COVER_EARLY == blockStrategy) {
                // kill running jobThread
                if (jobThread.isRunningOrHasQueue()) {
                    removeOldReason = "block strategy effect：" + ExecutorBlockStrategyEnum.COVER_EARLY.getTitle();

                    jobThread = null;
                }
            } else {
                // just queue trigger
            }
        }
        // replace thread (new or exists invalid)
        //    替换线程(新的或存在的无效)
        if (jobThread == null) {
            jobThread = XxlJobExecutor.registJobThread(triggerParam.getJobId(), jobHandler, removeOldReason);
        }

        // push data to queue  将触发参数  放到 触发队列里面
        ReturnT<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        return pushResult;
    }





    /**
     *  终止任务， 这个是在  我们项目里面  调用的
     */
    @Override
    public ReturnT<String> kill(KillParam killParam) {
        // kill handlerThread, and create new one
        // 杀死handlerThread，并创建一个新的

        // 根据 任务id  从任务集合   获取到   任务
        JobThread jobThread = XxlJobExecutor.loadJobThread(killParam.getJobId());
        if (jobThread != null) {
//            从集合队列 里面 移除 当前任务的线程  调度中心kill job。
            XxlJobExecutor.removeJobThread(killParam.getJobId(), "scheduling center kill job.");
            return ReturnT.SUCCESS;
        }

//        获取不到任务，那么就已经杀死了
        return new ReturnT<String>(ReturnT.SUCCESS_CODE, "job thread already killed.");
    }



    /**
     * Created by jing
     * 根据日志参数  调用远方获取日志信息
     *  查看执行日志
     */
    @Override
    public ReturnT<LogResult> log(LogParam logParam) {
        // log filename: logPath/yyyy-MM-dd/9999.log
//        根据日志触发时间  和 任务id ，创建日志名称
        String logFileName = XxlJobFileAppender.makeLogFileName(new Date(logParam.getLogDateTim()), logParam.getLogId());

//        从  第几行开始读取  信息
        LogResult logResult = XxlJobFileAppender.readLog(logFileName, logParam.getFromLineNum());
        return new ReturnT<LogResult>(logResult);
    }

}
