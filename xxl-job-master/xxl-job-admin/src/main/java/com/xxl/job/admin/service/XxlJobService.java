package com.xxl.job.admin.service;


import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.core.biz.model.ReturnT;

import java.util.Date;
import java.util.Map;

/**
 * core job action for xxl-job
 * 核心工作行动xxl-job
 * @author jing
 */
public interface XxlJobService {

	/**
	 * page list  xxl_job_info 表
	 *   分页查询    任务列表
	 * @param start  开始页
	 * @param length  多少条数据
	 * @param jobGroup  执行器  项目
	 *  @param   triggerStatus   调度状态
	 * @param jobDesc  任务描述
	 * @param executorHandler  方法名称
	 * @param author  作者
	 * @return
	 */
	public Map<String, Object> pageList(int start, int length, int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author);

	/**
	 * add job
	 * 新增任务   xxl_job_info 表
	 *
	 * @param jobInfo
	 * @return
	 */
	public ReturnT<String> add(XxlJobInfo jobInfo);

	/**
	 * update job
	 *更新任务   xxl_job_info 表
	 * @param jobInfo
	 * @return
	 */
	public ReturnT<String> update(XxlJobInfo jobInfo);

	/**
	 * remove job
	 * 删除任务
	 * @param id
	 * @return
	 */
	public ReturnT<String> remove(int id);

	/**
	 * 启动任务
	 */
	public ReturnT<String> start(int id);

	/**
	 * 停止任务
	 */
	public ReturnT<String> stop(int id);

	/**
	 * dashboard info
	 *  获取 运行报表 页面 上面所需要的数据
	 * @return
	 */
	public Map<String,Object> dashboardInfo();

	/**
	 *  首页  获取 调度报表
	 *  前端  默认传  当前日期提前7天
	 */
	public ReturnT<Map<String,Object>> chartInfo(Date startDate, Date endDate);

}
