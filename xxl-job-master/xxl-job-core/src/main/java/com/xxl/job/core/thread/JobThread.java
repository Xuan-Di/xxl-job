package com.xxl.job.core.thread;

import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;


/**
 * handler thread  任务 线程
 * 执行一个任务的线程对象
 * web 调用我们的项目，进行执行任务的时候，创建这个类的对象
 * 在我们的项目里面进行创建
 * @author xuxueli 2016-1-16 19:52:47
 */
public class JobThread extends Thread{

//	创建日志对象
	private static Logger logger = LoggerFactory.getLogger(JobThread.class);

	private int jobId;  // 任务id
	private IJobHandler handler;//  要执行的方法的对象  加了xxljob注解的方法对象

//	触发器参数list集合
	private LinkedBlockingQueue<TriggerParam> triggerQueue;

//	避免重复触发相同的trigger LOG ID
//	触发日志id集合
	private Set<Long> triggerLogIdSet;		// avoid repeat trigger for the same TRIGGER_LOG_ID

//	停止线程标识
	private volatile boolean toStop = false;  //共享变量用于停止线程
//	停止原因
	private String stopReason;
//  线程运行标识
    private boolean running = false;    // if running job
//	断续器 时间
	private int idleTimes = 0;			// idel times 空闲时间





	/**
	 * 构造器
	 * 根据任务id  ，任务里面的具体要执行的方法的实体类
	 */
	public JobThread(int jobId, IJobHandler handler) {
		this.jobId = jobId;
		this.handler = handler;

//		触发队列
		this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
//		触发日志集合
		this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<Long>());

		// assign job thread name
		this.setName("xxl-job, JobThread-"+jobId+"-"+System.currentTimeMillis());
	}



	/**
	 *   获取 -  要执行的方法的对象  xxljob 注解的方法的实体类对象
	 */
	public IJobHandler getHandler() {
		return handler;
	}

    /**
     * new trigger to queue
	 *队列的新触发器
     * 刷新 触发队列
	 *
	 * 如果触发参数过来，看日志队列里面有没有数据，如果有，直接返回，没有就加入队列
     * @param triggerParam  每一个定时任务 对象
     * @return
     */
	public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
		// avoid repeat  避免重复

//		如果触发日志集合  里面包含 当前任务日志id
		if (triggerLogIdSet.contains(triggerParam.getLogId())) {

//			返回当前任务有重复的
			logger.info(">>>>>>>>>>> repeate trigger job, logId:{}", triggerParam.getLogId());
			return new ReturnT<String>(ReturnT.FAIL_CODE, "repeate trigger job, logId:" + triggerParam.getLogId());
		}

//		将 当前任务的日志id进行存储
		triggerLogIdSet.add(triggerParam.getLogId());
//		将当前的任务  放到队列里面
		triggerQueue.add(triggerParam);
        return ReturnT.SUCCESS;
	}




    /**
     * kill job thread
     *  彻底杀死线程
     * @param stopReason
     */
	public void toStop(String stopReason) {
		/**
		 * Thread.interrupt只支持终止线程的阻塞状态(wait、join、sleep)，
		 * 在阻塞出抛出InterruptedException异常,但是并不会终止运行的线程本身；
		 * 所以需要注意，此处彻底销毁本线程，需要通过共享变量方式；
		 */
		this.toStop = true;
		this.stopReason = stopReason;
	}

    /**
     * is running job
	 * 判断当前任务线程是不是正在运行，任务队列是不是有数据
     * @return
     */
    public boolean isRunningOrHasQueue() {
        return running || triggerQueue.size()>0;
    }

//    当前线程 启动的时候，执行的东西
    @Override
	public void run() {

    	// init
    	try {
//    		先执行  这个方法   之前的方法
			handler.init();  // 当前方法执行之前   的方法的初始化
		} catch (Throwable e) {
    		logger.error(e.getMessage(), e);
		}



		// execute  执行任务
		while(!toStop){
			running = false; //   线程运行标识  默认是false
			idleTimes++;   // idel times 空闲时间

            TriggerParam triggerParam = null;  // 触发参数
            try {
				// to check toStop signal,
				// we need cycle, so wo cannot use queue.take(), instand of poll(timeout)

//				从触发参数  队列 里面 拿出 一个触发参数
				triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);

//				如果触发器的参数不为空
				if (triggerParam!=null) {
					running = true; // 将线程运行标志 改为TRUE
					idleTimes = 0;  //  断续器 时间

//				从  日志队列 里面 	移除  触发日志
					triggerLogIdSet.remove(triggerParam.getLogId());

					// log filename, like "logPath/yyyy-MM-dd/9999.log"

//					从 触发参数里面  获取到触发日志地址
					String logFileName = XxlJobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());


//					创建上下文对象
					XxlJobContext xxlJobContext = new XxlJobContext(
							triggerParam.getJobId(),
							triggerParam.getExecutorParams(),
							logFileName,
							triggerParam.getBroadcastIndex(),
							triggerParam.getBroadcastTotal());

					// init job context
//					子线程  集合   上下文集合里面  添加新的上下文对象
					XxlJobContext.setXxlJobContext(xxlJobContext);

					// execute  记录日志
					XxlJobHelper.log("<br>----------- xxl-job job execute start -----------<br>----------- Param:" + xxlJobContext.getJobParam());



//					如果 设置的  任务超时时间 大于 0
//					如果有超时  任务，那么会开启一个子线程  去执行
					if (triggerParam.getExecutorTimeout() > 0) {
						// limit timeout  定义一个未来线程
						Thread futureThread = null;
						try {

//							FutureTask可用于异步获取执行结果或取消执行任务的场景
							FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {

									// init job context  异步将  上下文内容保存到 上下文线程list里面
									XxlJobContext.setXxlJobContext(xxlJobContext);
									//  执行当前的方法
									handler.execute();
									return true;
								}
							});
							futureThread = new Thread(futureTask);
							futureThread.start();

							Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
						} catch (TimeoutException e) {

							XxlJobHelper.log("<br>----------- xxl-job job execute timeout");
							XxlJobHelper.log(e);

							// handle result
							XxlJobHelper.handleTimeout("job execute timeout ");
						} finally {
							futureThread.interrupt();
						}
					}



					else {

//						没有设置  超时时间，直接执行
						// just execute
						handler.execute();
					}




					// valid execute handle data
//					如果 处理结果状态代码  小于 0
					if (XxlJobContext.getXxlJobContext().getHandleCode() <= 0) {
						XxlJobHelper.handleFail("job handle result lost.");
					}

					else {
//						获取到  处理结果
						String tempHandleMsg = XxlJobContext.getXxlJobContext().getHandleMsg();

//						截取   处理结果
						tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000)
								?tempHandleMsg.substring(0, 50000).concat("...")
								:tempHandleMsg;

//						设置处理结果
						XxlJobContext.getXxlJobContext().setHandleMsg(tempHandleMsg);
					}

//					记录日志
					XxlJobHelper.log("<br>----------- xxl-job job execute end(finish) -----------<br>----------- Result: handleCode="
							+ XxlJobContext.getXxlJobContext().getHandleCode()
							+ ", handleMsg = "
							+ XxlJobContext.getXxlJobContext().getHandleMsg()
					);

				}


//				如果触发队列 里面  没有触发参数
				else {

//					如果续断时间  大于 30秒
					if (idleTimes > 30) {

//						如果 触发队列 为空
						if(triggerQueue.size() == 0) {	// avoid concurrent trigger causes jobId-lost

//     线程  map 移除  当前任务
							XxlJobExecutor.removeJobThread(jobId, "excutor idel times over limit.");
						}
					}
				}
			}

            catch (Throwable e) {
				if (toStop) {
//					如果线程停止
					XxlJobHelper.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
				}

				// handle result
				StringWriter stringWriter = new StringWriter();
				e.printStackTrace(new PrintWriter(stringWriter));
				String errorMsg = stringWriter.toString();

//				将失败的东西 写到上下文里面
				XxlJobHelper.handleFail(errorMsg);

				XxlJobHelper.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- xxl-job job execute end(error) -----------");
			}

            finally {
//            	一直需要执行的

//            	触发参数 不为空
                if(triggerParam != null) {
                    // callback handler info

//					线程也没有停止
                    if (!toStop) {
                        // commonm

//						调用回调函数  处理线程
                        TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                        		triggerParam.getLogId(),
								triggerParam.getLogDateTime(),
								XxlJobContext.getXxlJobContext().getHandleCode(),
								XxlJobContext.getXxlJobContext().getHandleMsg() )
						);
                    }
//                    线程停止了
                    else {
                        // is killed  调用回调函数  处理线程
                        TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
                        		triggerParam.getLogId(),
								triggerParam.getLogDateTime(),
								XxlJobContext.HANDLE_CODE_FAIL,
								stopReason + " [job running, killed]" )
						);
                    }
                }
            }
        }

		// callback trigger request in queue
//		回调触发队列中的请求

//		如果触发队列  不为空
		while(triggerQueue !=null && triggerQueue.size()>0){

//			从触发队列 获取到一个  触发参数
			TriggerParam triggerParam = triggerQueue.poll();
			if (triggerParam!=null) {
				// is killed   调用回调函数  处理线程
				TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
						triggerParam.getLogId(),
						triggerParam.getLogDateTime(),
						XxlJobContext.HANDLE_CODE_FAIL,
						stopReason + " [job not executed, in the job queue, killed.]")
				);
			}
		}




		// destroy    执行后一个方法
		try {

//			执行当前方法之后的  方法
			handler.destroy();
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}

		logger.info(">>>>>>>>>>> xxl-job JobThread stoped, hashCode:{}", Thread.currentThread());
	}
}
