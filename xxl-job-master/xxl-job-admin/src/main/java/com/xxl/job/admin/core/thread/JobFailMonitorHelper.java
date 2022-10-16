package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * job monitor instance
 * 任务 失败 监控助手
 *  目的就是  对失败的日志信息进行监控发送邮件
 *  更新  日志的监控状态字段
 * @author xuxueli 2015-9-1 18:05:56
 */
public class JobFailMonitorHelper {
//	定义日志对象
	private static Logger logger = LoggerFactory.getLogger(JobFailMonitorHelper.class);
//	创建当前类   对象
	private static JobFailMonitorHelper instance = new JobFailMonitorHelper();
//	获取静态对象
	public static JobFailMonitorHelper getInstance(){
		return instance;
	}

	// ---------------------- monitor   监控----------------------

//	定义监控  线程
	private Thread monitorThread;
	private volatile boolean toStop = false;



	/**
	 *   创建 失败监控 线程，并且启动
	 */
	public void start(){
		monitorThread = new Thread(new Runnable() {


			@Override
			public void run() {
				// monitor
				while (!toStop) {
					try {
//						查询  1000个 失败的日志 记录  xxl_job_log
						List<Long> failLogIds = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findFailJobLogIds(1000);
						if (failLogIds!=null && !failLogIds.isEmpty()) {
							for (long failLogId: failLogIds) {
								// lock log     更新报警状态 xxl_job_log
//								更新 日志  报警状态  xxl_job_log
//								告警状态：0-默认、-1 = 锁定状态、1-无需告警、2-告警成功、3-告警失败
//								将默认的0  变成 -1
								int lockRet = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateAlarmStatus(failLogId, 0, -1);
								if (lockRet < 1) {
//									没有更新成功
									continue;
								}

//								根据 执行失败 日志id  查询具体数据  xxl_job_log
								XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(failLogId);
//								根据  任务id  找到  具体的任务  xxl_job_info
								XxlJobInfo info = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().loadById(log.getJobId());

								// 1、fail retry monitor  失败重试监控

//								执行失败 重试 次数  大于0
								if (log.getExecutorFailRetryCount() > 0) {

									//  开始调度  任务
									JobTriggerPoolHelper.trigger(log.getJobId(), TriggerTypeEnum.RETRY, (log.getExecutorFailRetryCount()-1), log.getExecutorShardingParam(), log.getExecutorParam(), null);
									//  失败重试 触发
									String retryMsg = "<br><br><span style=\"color:#F39C12;\" > >>>>>>>>>>>"+ I18nUtil.getString("jobconf_trigger_type_retry") +"<<<<<<<<<<< </span><br>";
									log.setTriggerMsg(log.getTriggerMsg() + retryMsg);
//									将重试消息  更新到 xxl_job_log
									XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTriggerInfo(log);
								}



								// 2、fail alarm monitor  失败报警监控
								int newAlarmStatus = 0;		// 告警状态：0-默认、-1=锁定状态、1-无需告警、2-告警成功、3-告警失败

//								如果  任务  不为空
								if (info != null) {
//									 将 一个 任务的 重试 信息   进行发送email警告

//									发送告警的方法
									boolean alarmResult = XxlJobAdminConfig.getAdminConfig().getJobAlarmer().alarm(info, log);
//									根据是否发送成功，判断   告警成功还是失败
									newAlarmStatus = alarmResult?2:3;
								} else {
//									如果没有当前任务，就无须告警
									newAlarmStatus = 1;
								}

//								在日志  里面   更新 告警状态，从 -1  到 newAlarmStatus
								XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateAlarmStatus(failLogId, -1, newAlarmStatus);
							}
						}

					} catch (Exception e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job fail monitor thread error:{}", e);
						}
					}

                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                }

				logger.info(">>>>>>>>>>> xxl-job, job fail monitor thread stop");

			}
		});
		monitorThread.setDaemon(true);
		monitorThread.setName("xxl-job, admin JobFailMonitorHelper");
		monitorThread.start();
	}


	/**
	 *  停止  线程
	 */
	public void toStop(){
		toStop = true;
		// interrupt and wait
		monitorThread.interrupt();
		try {
			monitorThread.join();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}

}
