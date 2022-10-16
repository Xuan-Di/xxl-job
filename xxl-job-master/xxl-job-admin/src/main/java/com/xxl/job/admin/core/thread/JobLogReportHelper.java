package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobLogReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * job log report helper
 *
 * 清除过期的日志，根据yml里面配置的过期时间
 * @author xuxueli 2019-11-22
 */
public class JobLogReportHelper {

//    定义  日志对象
    private static Logger logger = LoggerFactory.getLogger(JobLogReportHelper.class);
//  定义 静态  当前类对象
    private static JobLogReportHelper instance = new JobLogReportHelper();
//    获取到 静态对象
    public static JobLogReportHelper getInstance(){
        return instance;
    }

//  定义 日志报告  线程
    private Thread logrThread;
//    默认停止
    private volatile boolean toStop = false;


    /**
     * 创建  日志报告线程对象
     *  xxl_job_log_report
     *
     */
    public void start(){
        logrThread = new Thread(new Runnable() {

            @Override
            public void run() {

                // last clean log time
                // 上次清理日志时间
                long lastCleanLogTime = 0;


                while (!toStop) {
//                    当前线程没有停止

                    // 1、log-report refresh: refresh log report in 3 days
//                    日志报表刷新:3天刷新日志报表  xxl_job_log_report
                    try {

//                        遍历  3天，每次遍历一天的数据

                        for (int i = 0; i < 3; i++) {

                            // today  当前系统日期
                            Calendar itemDay = Calendar.getInstance();
                            itemDay.add(Calendar.DAY_OF_MONTH, -i);
                            itemDay.set(Calendar.HOUR_OF_DAY, 0);
                            itemDay.set(Calendar.MINUTE, 0);
                            itemDay.set(Calendar.SECOND, 0);
                            itemDay.set(Calendar.MILLISECOND, 0);
//                            从 一天的 0点开始
                            Date todayFrom = itemDay.getTime();

                            itemDay.set(Calendar.HOUR_OF_DAY, 23);
                            itemDay.set(Calendar.MINUTE, 59);
                            itemDay.set(Calendar.SECOND, 59);
                            itemDay.set(Calendar.MILLISECOND, 999);

//                            最新日期  一天的24 点
                            Date todayTo = itemDay.getTime();

                            // refresh log-report every minute
                            // 每分钟刷新一次日志报告
                            XxlJobLogReport xxlJobLogReport = new XxlJobLogReport();
                            xxlJobLogReport.setTriggerDay(todayFrom); // 任务触发日期
                            xxlJobLogReport.setRunningCount(0);
                            xxlJobLogReport.setSucCount(0);
                            xxlJobLogReport.setFailCount(0);


//                           获取  从一天的0点到24点的数据  xxl_job_log
//                            调度次数，调度成功正在执行的次数，执行成功的次数
                            Map<String, Object> triggerCountMap = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findLogReport(todayFrom, todayTo);
                            if (triggerCountMap!=null && triggerCountMap.size()>0) {
//                                日志个数
                                int triggerDayCount = triggerCountMap.containsKey("triggerDayCount")?Integer.valueOf(String.valueOf(triggerCountMap.get("triggerDayCount"))):0;
//                                运行个数
                                int triggerDayCountRunning = triggerCountMap.containsKey("triggerDayCountRunning")?Integer.valueOf(String.valueOf(triggerCountMap.get("triggerDayCountRunning"))):0;
//                                成功个数
                                int triggerDayCountSuc = triggerCountMap.containsKey("triggerDayCountSuc")?Integer.valueOf(String.valueOf(triggerCountMap.get("triggerDayCountSuc"))):0;
//                                失败个数
                                int triggerDayCountFail = triggerDayCount - triggerDayCountRunning - triggerDayCountSuc;

                                xxlJobLogReport.setRunningCount(triggerDayCountRunning);
                                xxlJobLogReport.setSucCount(triggerDayCountSuc);
                                xxlJobLogReport.setFailCount(triggerDayCountFail);
                            }






                            // do refresh   根据触发时间  更新数据库
                            int ret = XxlJobAdminConfig.getAdminConfig().getXxlJobLogReportDao().update(xxlJobLogReport);
                            if (ret < 1) {
                                XxlJobAdminConfig.getAdminConfig().getXxlJobLogReportDao().save(xxlJobLogReport);
                            }
                        }

                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(">>>>>>>>>>> xxl-job, job log report thread error:{}", e);
                        }
                    }




                    // 2、log-clean: switch open & once each day
//                    日志清理:打开开关，每天一次
//                  getLogretentiondays()  从yml里面 获取自己配置的 日志保留天数
//                    当前时间 距离  上次 清除  时间  大于一天
                    if (XxlJobAdminConfig.getAdminConfig().getLogretentiondays()>0
                            && System.currentTimeMillis() - lastCleanLogTime > 24*60*60*1000) {

                        // expire-time

//                        根据保留天数，获取过期日期
//                        小于 expiredDay 的都删除
                        Calendar expiredDay = Calendar.getInstance();
                        expiredDay.add(Calendar.DAY_OF_MONTH, -1 * XxlJobAdminConfig.getAdminConfig().getLogretentiondays());
                        expiredDay.set(Calendar.HOUR_OF_DAY, 0);
                        expiredDay.set(Calendar.MINUTE, 0);
                        expiredDay.set(Calendar.SECOND, 0);
                        expiredDay.set(Calendar.MILLISECOND, 0);


//                        清除这个日期之前的数据
                        Date clearBeforeTime = expiredDay.getTime();

                        // clean expired log  清理 过期日志
                        List<Long> logIds = null;
                        do {

//                            根据时间  查询要清除的 日志 xxl_job_log
                            logIds = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findClearLogIds(0, 0, clearBeforeTime, 0, 1000);
                            if (logIds!=null && logIds.size()>0) {

//                                清除 日志
                                XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().clearLog(logIds);
                            }
                        } while (logIds!=null && logIds.size()>0);

                        // update clean time  更新 最近清除时间
                        lastCleanLogTime = System.currentTimeMillis();
                    }

                    try {
                        TimeUnit.MINUTES.sleep(1);
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                }



                logger.info(">>>>>>>>>>> xxl-job, job log report thread stop");

            }
        });
        logrThread.setDaemon(true);
        logrThread.setName("xxl-job, admin JobLogReportHelper");
        logrThread.start();
    }



    /**
     *  中断当前线程，并且将线程放到任务队列  里面
     */
    public void toStop(){
        toStop = true;
        // interrupt and wait
        logrThread.interrupt();
        try {
            logrThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

}
