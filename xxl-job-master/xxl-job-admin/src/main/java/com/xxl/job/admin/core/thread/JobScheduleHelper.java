package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.cron.CronExpression;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 主要功能就是进行任务的调度，计算任务下一次执行的有效时间
 * 定时器
 * @author xuxueli 2019-05-21
 */
public class JobScheduleHelper {

//    定义日志对象
    private static Logger logger = LoggerFactory.getLogger(JobScheduleHelper.class);
//  创建 当前类对象
    private static JobScheduleHelper instance = new JobScheduleHelper();
//    获取 当前类对象
    public static JobScheduleHelper getInstance(){
        return instance;
    }



    public static final long PRE_READ_MS = 5000;    // pre read预读  5 秒

// 两个守护线程
    private Thread scheduleThread; // 任务扫描线程
    private Thread ringThread; // 时间轮 执行线程


    private volatile boolean scheduleThreadToStop = false; // 任务扫描线程 是否停止
    private volatile boolean ringThreadToStop = false;// 执行线程 是否停止

//    时间轮  数据   本质是  保存  快要执行的任务
//    key为执行的秒，value为要执行的job的id
//    scheduleThread线程会提前5-10秒将任务放入时间轮的list中。
    private volatile static Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();



    /**
     * @author xuxueli 2019-05-21
     * 一直扫描任务线程，将即将 执行的任务  保存大map集合里面
     */
    public void start(){
        // schedule thread  任务扫描线程
        scheduleThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    睡眠一段时间
                    TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis()%1000 );
                } catch (InterruptedException e) {
                    if (!scheduleThreadToStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
                logger.info(">>>>>>>>> init xxl-job admin scheduler success.");



                // pre-read count: treadpool-size * trigger-qps (each trigger cost 50ms, qps = 1000/50 = 20)
//                预读数据，从数据库中读取当前截止到五秒后内会执行的job信息，并且读取分页大小为preReadCount=6000条数据
//                就是  要读取  多少个任务
                int preReadCount = (XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax() + XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax()) * 20;

//                一直进行扫描
                while (!scheduleThreadToStop) {

                    // Scan Job   开始时间
                    long start = System.currentTimeMillis();
                    //  定义数据库连接对象
                    Connection conn = null;
                    //  数据库  是否 自动提交
                    Boolean connAutoCommit = null;
                    //  执行的sql 语句
                    PreparedStatement preparedStatement = null;
                    //   是否读取成功，默认成功， 就是是否从数据库查询出  还没有执行的数据
                    boolean preReadSuc = true;


                    try {

                        //   获取数据库  连接对象
                        conn = XxlJobAdminConfig.getAdminConfig().getDataSource().getConnection();
                        //   得到数据库  是否需要自动  提交
                        connAutoCommit = conn.getAutoCommit();
                        //   设置数据库  不  自动提交  ，只有不自动提交，才能获取独占锁
                        conn.setAutoCommit(false);
                        //    获取独占锁
                        // 采用 select for update ，是排它锁。说白了 xxl-job 用一张数据库表来当分布式锁了，确保多个 xxl-job admin 节点下，依旧只能同时执行一个调度线程任务
                        preparedStatement = conn.prepareStatement(  "select * from xxl_job_lock where lock_name = 'schedule_lock' for update" );
                        preparedStatement.execute();

                        // tx start

                        // 1、pre read

//                        获得当前时间
                        long nowTime = System.currentTimeMillis();

//                        从数据库中读取截止到目前时间 ，五秒后未执行的 job ，并且读取 preReadCount=6000 条
//                        查询 出 已经启动  的项目
                        List<XxlJobInfo> scheduleList = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);



                        if (scheduleList!=null && scheduleList.size()>0) {
                            // 2、  遍历每一个  已经启动的  任务
                            for (XxlJobInfo jobInfo: scheduleList) {

                                // time-ring jump 如果超时的任务，直接跳过
                                if (nowTime > jobInfo.getTriggerNextTime() + PRE_READ_MS) {
                                    // 2.1、trigger-expire > 5s：pass && make next-trigger-time
                                    logger.warn(">>>>>>>>>>> xxl-job, schedule misfire, jobId = " + jobInfo.getId());

                                    // 1、misfire match
                                    MisfireStrategyEnum misfireStrategyEnum = MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), MisfireStrategyEnum.DO_NOTHING);
//                                    如果失败策略 是  重试一次
                                    if (MisfireStrategyEnum.FIRE_ONCE_NOW == misfireStrategyEnum) {
                                        // FIRE_ONCE_NOW 》 trigger   触发
                                        JobTriggerPoolHelper.trigger(jobInfo.getId(), TriggerTypeEnum.MISFIRE, -1, null, null, null);
                                        logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + jobInfo.getId() );
                                    }

                                    // 2、fresh next 更新下一次的   触发时间
                                    refreshNextValidTime(jobInfo, new Date());

                                }


//  如果  任务的下一次执行之间在当前时间的范围之内
                                else if (nowTime > jobInfo.getTriggerNextTime()) {
                                    // 2.2、trigger-expire < 5s：direct-trigger && make next-trigger-time

                                    // 1、trigger  触发
                                    JobTriggerPoolHelper.trigger(jobInfo.getId(), TriggerTypeEnum.CRON, -1, null, null, null);
                                    logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + jobInfo.getId() );

                                    // 2、fresh next   刷新下一次的执行时间
                                    refreshNextValidTime(jobInfo, new Date());

                                    // next-trigger-time in 5s, pre-read again
//                                    如果 任务启动，并且  下一次的时间在范围之内
                                    if (jobInfo.getTriggerStatus()==1 && nowTime + PRE_READ_MS > jobInfo.getTriggerNextTime()) {

                                        // 1、make ring second  当前时间的秒数
                                        int ringSecond = (int)((jobInfo.getTriggerNextTime()/1000)%60);

                                        // 2、push time ring  将数据放到时间环上面
                                        pushTimeRing(ringSecond, jobInfo.getId());

                                        // 3、fresh next  刷新下一次的执行时间
                                        refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));

                                    }

                                }




                                else {
                                    // 2.3、trigger-pre-read：time-ring trigger && make next-trigger-time

                                    // 1、make ring second  当前时间的秒数
                                    int ringSecond = (int)((jobInfo.getTriggerNextTime()/1000)%60);

                                    // 2、push time ring   将数据放到时间环上面
                                    pushTimeRing(ringSecond, jobInfo.getId());

                                    // 3、fresh next 刷新下一次的执行时间
                                    refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));

                                }

                            }





                            // 3、更新任务
                            for (XxlJobInfo jobInfo: scheduleList) {
                                XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleUpdate(jobInfo);
                            }

                        }


                        else {
                            preReadSuc = false;
                        }

                        // tx stop


                    } catch (Exception e) {
                        if (!scheduleThreadToStop) {
                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#scheduleThread error:{}", e);
                        }
                    }
                    finally {

                        // commit
                        if (conn != null) {
                            try {

                                //   提交  sql 语句
                                conn.commit();
                            } catch (SQLException e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                            try {
                                conn.setAutoCommit(connAutoCommit);
                            } catch (SQLException e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                            try {
                                conn.close();
                            } catch (SQLException e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }

                        // close PreparedStatement
                        if (null != preparedStatement) {
                            try {
                                preparedStatement.close();
                            } catch (SQLException e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }
                    }


//                    扫描 任务 结束 时间
                    long cost = System.currentTimeMillis()-start;


                    // Wait seconds, align second  等待秒，对齐秒
                    if (cost < 1000) {  // scan-overtime, not wait  如果扫描时间  小于一秒
                        try {
                            // pre-read period: success > scan each second; fail > skip this period;
                            //  休眠  1秒 或者  5秒
                            TimeUnit.MILLISECONDS.sleep((preReadSuc?1000:PRE_READ_MS) - System.currentTimeMillis()%1000);
                        } catch (InterruptedException e) {
                            if (!scheduleThreadToStop) {
                                logger.error(e.getMessage(), e);
                            }
                        }
                    }

                }

                logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper#scheduleThread stop");
            }
        });

//        所有为线程服务而不涉及资源的线程都能设置为守护线程；
//        不能操作文件、数据库等资源，避免主线程关闭而未能关闭守护线程的资源，
//        并且它会在任何时候甚至在一个操作的中间发生中断。

        scheduleThread.setDaemon(true);
        scheduleThread.setName("xxl-job, admin JobScheduleHelper#scheduleThread");
        scheduleThread.start();









        // ring thread   触发时间轮中的任务，并且任务完成之后  移除
        ringThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (!ringThreadToStop) {

                    // align second
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
                    } catch (InterruptedException e) {
                        if (!ringThreadToStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                    try {
                        // second data  存放具体的任务id
                        List<Integer> ringItemData = new ArrayList<>();

//                        获取到  当前时间的秒数
                        int nowSecond = Calendar.getInstance().get(Calendar.SECOND);   // 避免处理耗时太长，跨过刻度，向前校验一个刻度；
                        for (int i = 0; i < 2; i++) {
//                            循环提前两秒
                            List<Integer> tmpData = ringData.remove( (nowSecond+60-i)%60 );
                            if (tmpData != null) {
                                ringItemData.addAll(tmpData);
                            }
                        }

                        // ring trigger
                        logger.debug(">>>>>>>>>>> xxl-job, time-ring beat : " + nowSecond + " = " + Arrays.asList(ringItemData) );

//                        如果有具体的数据值
                        if (ringItemData.size() > 0) {
                            // do trigger  进行触发任务
                            for (int jobId: ringItemData) {
                                // do trigger  触发任务
                                JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.CRON, -1, null, null, null);
                            }
                            // clear  清空list
                            ringItemData.clear();
                        }
                    } catch (Exception e) {
                        if (!ringThreadToStop) {
                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#ringThread error:{}", e);
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper#ringThread stop");
            }
        });
        ringThread.setDaemon(true);
        ringThread.setName("xxl-job, admin JobScheduleHelper#ringThread");
        ringThread.start();
    }



    /**
     * 更新下一次的   触发时间
     */

    private void refreshNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        Date nextValidTime = generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setTriggerLastTime(jobInfo.getTriggerNextTime());
            jobInfo.setTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setTriggerStatus(0);
            jobInfo.setTriggerLastTime(0);
            jobInfo.setTriggerNextTime(0);
            logger.warn(">>>>>>>>>>> xxl-job, refreshNextValidTime fail for job: jobId={}, scheduleType={}, scheduleConf={}",
                    jobInfo.getId(), jobInfo.getScheduleType(), jobInfo.getScheduleConf());
        }
    }


//    ringSecond 下一次执行的时间
    private void pushTimeRing(int ringSecond, int jobId){
        // push async ring
        List<Integer> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<Integer>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);

        logger.debug(">>>>>>>>>>> xxl-job, schedule push time-ring : " + ringSecond + " = " + Arrays.asList(ringItemData) );
    }

    public void toStop(){

        // 1、stop schedule
        scheduleThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);  // wait
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
        if (scheduleThread.getState() != Thread.State.TERMINATED){
            // interrupt and wait
            scheduleThread.interrupt();
            try {
                scheduleThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        // if has ring data
        boolean hasRingData = false;
        if (!ringData.isEmpty()) {
            for (int second : ringData.keySet()) {
                List<Integer> tmpData = ringData.get(second);
                if (tmpData!=null && tmpData.size()>0) {
                    hasRingData = true;
                    break;
                }
            }
        }
        if (hasRingData) {
            try {
                TimeUnit.SECONDS.sleep(8);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        // stop ring (wait job-in-memory stop)
        ringThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
        if (ringThread.getState() != Thread.State.TERMINATED){
            // interrupt and wait
            ringThread.interrupt();
            try {
                ringThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper stop");
    }


    // ---------------------- tools ----------------------


    /**
     * 根据任务的调度类型 ，从当前时间的后5秒开始，获取到当前任务下一次调度的时间
     * @param jobInfo
     * @param fromTime
     */
    public static Date generateNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {

//        查询出当前  任务的调度类型
        ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);

        if (ScheduleTypeEnum.CRON == scheduleTypeEnum) {
            Date nextValidTime = new CronExpression(jobInfo.getScheduleConf()).getNextValidTimeAfter(fromTime);
            return nextValidTime;
        }

        else if (ScheduleTypeEnum.FIX_RATE == scheduleTypeEnum /*|| ScheduleTypeEnum.FIX_DELAY == scheduleTypeEnum*/) {
            return new Date(fromTime.getTime() + Integer.valueOf(jobInfo.getScheduleConf())*1000 );
        }
        return null;
    }

}
