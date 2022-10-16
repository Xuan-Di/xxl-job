package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobRegistry;
import com.xxl.job.core.biz.model.RegistryParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.enums.RegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;

/**
 * job registry instance
 * 任务注册线程池
 * @author xuxueli 2016-10-02 19:10:24
 */
public class JobRegistryHelper {

//	定义 日志对象
	private static Logger logger = LoggerFactory.getLogger(JobRegistryHelper.class);
// 创建当前类的静态对象
	private static JobRegistryHelper instance = new JobRegistryHelper();
//	获取当前的静态对象
	public static JobRegistryHelper getInstance(){
		return instance;
	}


//	定义  注册或者移除 线程池
	private ThreadPoolExecutor registryOrRemoveThreadPool = null;
//	注册 监控线程
	private Thread registryMonitorThread;
//
	private volatile boolean toStop = false;


	/**
	 *   创建注册线程池
	 *   创建并且启动注册  监控线程
	 */
	public void start(){

		// for registry or remove  创建线程池
		registryOrRemoveThreadPool = new ThreadPoolExecutor(
				2,
				10,
				30L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(2000),
				new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "xxl-job, admin JobRegistryMonitorHelper-registryOrRemoveThreadPool-" + r.hashCode());
					}
				},
				new RejectedExecutionHandler() {
					@Override
					public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
						r.run();
						logger.warn(">>>>>>>>>>> xxl-job, registry or remove too fast, match threadpool rejected handler(run now).");
					}
				});

		// for monitor  创建线程    当前线程的  目的 就是 更新xxl_job_group表里自动注册的执行器
//		并且删除  xxl_job_registry 里面过时的数据
		registryMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!toStop) {
					try {
						//  查询数据库全部的  注册类型为  自动注册的 执行器（项目） xxl_job_group
						List<XxlJobGroup> groupList = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().findByAddressType(0);
						if (groupList!=null && !groupList.isEmpty()) {

							//查询出 超时的地址  超时时间是项目自己配置的，和当前时间比较
//							比如当前时间是5点，配置的时间间隔是1小时，那么 5-1=4，所以这个表里面，在4点之前的都查询出来

//							也就是查询  xxl_job_registry  ，将过时的  都查询出来
							List<Integer> ids = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().findDead(RegistryConfig.DEAD_TIMEOUT, new Date());
							if (ids!=null && ids.size()>0) {

//								删除超时的数据 xxl_job_registry
								XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().removeDead(ids);
							}


//							刷新  最新的自动注册的  地址
							// fresh online address (admin/executor)
							HashMap<String, List<String>> appAddressMap = new HashMap<String, List<String>>();


//							查询出没有超时的  数据  xxl_job_registry
							List<XxlJobRegistry> list = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().findAll(RegistryConfig.DEAD_TIMEOUT, new Date());

//							下面这个 if 里面 的逻辑  就是 往appAddressMap 集合里面存放数据
//							从查询出没有超时的  全部数据  xxl_job_registry，整理之后放到appAddressMap 集合
							if (list != null) {
								for (XxlJobRegistry item: list) {


//									如果  是  自动注册
									if (RegistryConfig.RegistType.EXECUTOR.name().equals(item.getRegistryGroup())) {
										String appname = item.getRegistryKey();// 获取项目名称
										List<String> registryList = appAddressMap.get(appname);
										if (registryList == null) {
											registryList = new ArrayList<String>();
										}

										if (!registryList.contains(item.getRegistryValue())) {
											registryList.add(item.getRegistryValue());
										}
										appAddressMap.put(appname, registryList);
									}
								}
							}





							// fresh group address
//  遍历 从 xxl_job_group  这个里面查询出来的全部的  自动注册的数据，要更新他们的地址
//							groupList  查询数据库全部的  注册类型为  自动注册的 执行器（项目） xxl_job_group
							for (XxlJobGroup group: groupList) {
//								根据app名称 获取 最新的地址
								List<String> registryList = appAddressMap.get(group.getAppname());
								String addressListStr = null;

//								如果不为空
								if (registryList!=null && !registryList.isEmpty()) {
									Collections.sort(registryList);
									StringBuilder addressListSB = new StringBuilder();
									for (String item:registryList) {
										addressListSB.append(item).append(",");
									}
									addressListStr = addressListSB.toString();
									addressListStr = addressListStr.substring(0, addressListStr.length()-1);
								}
								group.setAddressList(addressListStr);
								group.setUpdateTime(new Date());

								XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().update(group);
							}
						}
					} catch (Exception e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job registry monitor thread error:{}", e);
						}
					}
					try {
						TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
					} catch (InterruptedException e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job registry monitor thread error:{}", e);
						}
					}
				}
				logger.info(">>>>>>>>>>> xxl-job, job registry monitor thread stop");
			}
		});
		registryMonitorThread.setDaemon(true);
		registryMonitorThread.setName("xxl-job, admin JobRegistryMonitorHelper-registryMonitorThread");
		registryMonitorThread.start();
	}

	/**
	 *  关闭线程池，和停止注册线程
	 */
	public void toStop(){
		toStop = true;

		// stop registryOrRemoveThreadPool
		registryOrRemoveThreadPool.shutdownNow();

		// stop monitir (interrupt and wait)
		registryMonitorThread.interrupt();
		try {
			registryMonitorThread.join();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}




	// ---------------------- helper 我们的项目调用  web项目 ----------------------
	/**
	 * 我们项目 调用  web项目  实现注册功能
	 */
	public ReturnT<String> registry(RegistryParam registryParam) {

		// valid
		if (!StringUtils.hasText(registryParam.getRegistryGroup())
				|| !StringUtils.hasText(registryParam.getRegistryKey())
				|| !StringUtils.hasText(registryParam.getRegistryValue())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "Illegal Argument.");
		}

		// async execute   异步执行 注册功能
//		初始化注册  的线程池
		registryOrRemoveThreadPool.execute(new Runnable() {
			@Override
			public void run() {

//				根据  项目名称，项目地址  先更新时间，维持心跳
				int ret = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().registryUpdate(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());
				if (ret < 1) {
//					如果数据库没有   就新增
					XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().registrySave(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());

					// fresh   这个是源码里面的，我们先注释掉，因为里面没东西
//					freshGroupRegistryInfo(registryParam);
				}
			}
		});

		return ReturnT.SUCCESS;
	}


	/**
	 *  我们项目  调用 web 实现注册功能
	 */
	public ReturnT<String> registryRemove(RegistryParam registryParam) {

		// valid
		if (!StringUtils.hasText(registryParam.getRegistryGroup())
				|| !StringUtils.hasText(registryParam.getRegistryKey())
				|| !StringUtils.hasText(registryParam.getRegistryValue())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "Illegal Argument.");
		}

		// async execute  初始化  的线程池
		registryOrRemoveThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				int ret = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().registryDelete(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
				if (ret > 0) {
					// fresh
//					freshGroupRegistryInfo(registryParam);
				}
			}
		});

		return ReturnT.SUCCESS;
	}

	private void freshGroupRegistryInfo(RegistryParam registryParam){
		// Under consideration, prevent affecting core tables
//		在考虑中，防止影响核心表
	}


}
