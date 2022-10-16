package com.xxl.job.core.handler;

/**
 * job handler  任务处理触发器
 * 就是  要执行哪些代码
 *
 * @author jing
 */
public abstract class IJobHandler {


	/**
	 * execute handler, invoked when executor receives a scheduling request
	 * 当执行程序接收到调度请求时调用
	 * @throws Exception
	 */
	public abstract void execute() throws Exception;


	/*@Deprecated
	public abstract ReturnT<String> execute(String param) throws Exception;*/

	/**
	 * init handler, invoked when JobThread init
	 * 初始化  触发器
	 */
	public void init() throws Exception {
		// do something
	}


	/**
	 * destroy handler, invoked when JobThread destroy
	 * 执行  当前方法执行之后的  方法
	 */
	public void destroy() throws Exception {
		// do something
	}


}
