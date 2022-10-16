package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.complete.XxlJobCompleter;
import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

/**
 * job lose-monitor instance
 *  任务完成线程
 *  处理任务结果的线程   回调监听器
 *  就是  将 超过10分钟还在执行的  任务状态  改为 失败
 * @author xuxueli 2015-9-1 18:05:56
 */
public class JobCompleteHelper {

//	定义日志类
	private static Logger logger = LoggerFactory.getLogger(JobCompleteHelper.class);
//	当前类静态对象
	private static JobCompleteHelper instance = new JobCompleteHelper();
//	获取当前类静态对象
	public static JobCompleteHelper getInstance(){
		return instance;
	}

	// ---------------------- monitor  监控----------------------

//	定义回调函数  线程池
	private ThreadPoolExecutor callbackThreadPool = null;
//	定义监控线程
	private Thread monitorThread;
//	定义当前线程是否停止
	private volatile boolean toStop = false;



	/**
	 *  开始当前线程
	 */
	public void start(){

		// for callback  创建回调函数  线程池
		callbackThreadPool = new ThreadPoolExecutor(
				2,
				20,
				30L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(3000),
				new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "xxl-job, admin JobLosedMonitorHelper-callbackThreadPool-" + r.hashCode());
					}
				},
				new RejectedExecutionHandler() {
					@Override
					public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
						r.run();
						logger.warn(">>>>>>>>>>> xxl-job, callback too fast, match threadpool rejected handler(run now).");
					}
				});


		// for monitor   定义监控线程
		monitorThread = new Thread(new Runnable() {

			@Override
			public void run() {

				// wait for JobTriggerPoolHelper-init
				//	等触发线程池创建之后，监控线程池才创建
//				所以休眠一段时间

				try {
					TimeUnit.MILLISECONDS.sleep(50);
				} catch (InterruptedException e) {
					if (!toStop) {
						logger.error(e.getMessage(), e);
					}
				}

				// monitor  监控 日志
				while (!toStop) {
					try {
						// 任务结果丢失处理：调度记录停留在 "运行中" 状态超过10min，
						// 且对应执行器心跳注册失败不在线，则将本地调度主动标记失败；
//						当前时间提前  10 分钟
						Date losedTime = DateUtil.addMinutes(new Date(), -10);
//						从  xxl-job-log 表中获取数据，是触发成功，但是正在执行的日志
						//
						List<Long> losedJobIds  = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findLostJobIds(losedTime);

						if (losedJobIds!=null && losedJobIds.size()>0) {
//							遍历每一个日志
							for (Long logId: losedJobIds) {

								XxlJobLog jobLog = new XxlJobLog();
								jobLog.setId(logId);

								jobLog.setHandleTime(new Date()); // 执行时间是当前时间
								jobLog.setHandleCode(ReturnT.FAIL_CODE);// 执行-状态  是失败
								jobLog.setHandleMsg( I18nUtil.getString("joblog_lost_fail") ); //执行-日志 失败

								XxlJobCompleter.updateHandleInfoAndFinish(jobLog);
							}

						}
					}

					catch (Exception e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job fail monitor thread error:{}", e);
						}
					}

                    try {
                        TimeUnit.SECONDS.sleep(60);
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                }

				logger.info(">>>>>>>>>>> xxl-job, JobLosedMonitorHelper stop");

			}
		});



		monitorThread.setDaemon(true);
		monitorThread.setName("xxl-job, admin JobLosedMonitorHelper");
		monitorThread.start();
	}

	public void toStop(){
		toStop = true;

		// stop registryOrRemoveThreadPool
		callbackThreadPool.shutdownNow();

		// stop monitorThread (interrupt and wait)
		monitorThread.interrupt();
		try {
			monitorThread.join();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}


	// ---------------------- helper ----------------------
	/**
	 *  我们项目  调用 web 实现 回调功能
	 */
	public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList) {

//		我们项目初始化的时候  就创建这个线程池
		callbackThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				for (HandleCallbackParam handleCallbackParam: callbackParamList) {
//					遍历  每一个回调的  信息
					ReturnT<String> callbackResult = callback(handleCallbackParam);
					logger.debug(">>>>>>>>> JobApiController.callback {}, handleCallbackParam={}, callbackResult={}",
							(callbackResult.getCode()== ReturnT.SUCCESS_CODE?"success":"fail"), handleCallbackParam, callbackResult);
				}
			}
		});

		return ReturnT.SUCCESS;
	}
	/**
	 * 对每一个 进行处理 handleCallbackParam
	 */
	private ReturnT<String> callback(HandleCallbackParam handleCallbackParam) {
		// valid log item  从xxl_job_log  获取当前的日志信息
		XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(handleCallbackParam.getLogId());
		if (log == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "log item not found.");
		}
		if (log.getHandleCode() > 0) {
//			日志重复回调。E
//			避免重复回调，触发子任务等
			return new ReturnT<String>(ReturnT.FAIL_CODE, "log repeate callback.");     // avoid repeat callback, trigger child job etc
		}

		// handle msg
		StringBuffer handleMsg = new StringBuffer();
		if (log.getHandleMsg()!=null) {
			handleMsg.append(log.getHandleMsg()).append("<br>");
		}
		if (handleCallbackParam.getHandleMsg() != null) {
			handleMsg.append(handleCallbackParam.getHandleMsg());
		}

		// success, save log
		log.setHandleTime(new Date()); // 任务执行时间
		log.setHandleCode(handleCallbackParam.getHandleCode()); // 执行状态代码
		log.setHandleMsg(handleMsg.toString()); // 执行  结果

//		更新日志
		XxlJobCompleter.updateHandleInfoAndFinish(log);

		return ReturnT.SUCCESS;
	}



}
