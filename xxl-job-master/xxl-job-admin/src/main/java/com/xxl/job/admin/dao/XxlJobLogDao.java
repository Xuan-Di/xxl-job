package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * job log
 * @author xuxueli 2016-1-12 18:03:06
 */
@Mapper
public interface XxlJobLogDao {

	// exist jobId not use jobGroup, not exist use jobGroup
	public List<XxlJobLog> pageList(@Param("offset") int offset,
									@Param("pagesize") int pagesize,
									@Param("jobGroup") int jobGroup,
									@Param("jobId") int jobId,
									@Param("triggerTimeStart") Date triggerTimeStart,
									@Param("triggerTimeEnd") Date triggerTimeEnd,
									@Param("logStatus") int logStatus);
	public int pageListCount(@Param("offset") int offset,
							 @Param("pagesize") int pagesize,
							 @Param("jobGroup") int jobGroup,
							 @Param("jobId") int jobId,
							 @Param("triggerTimeStart") Date triggerTimeStart,
							 @Param("triggerTimeEnd") Date triggerTimeEnd,
							 @Param("logStatus") int logStatus);


	/**
	 * 从xxl_job_log 根据  id 查询数据
	 *
	 */
	public XxlJobLog load(@Param("id") long id);
	/**
	 * 保存数据  到 xxl_job_log
	 *
	 */
	public long save(XxlJobLog xxlJobLog);
	/**
	 * 失败重试 触发  xxl_job_log
	 */
	public int updateTriggerInfo(XxlJobLog xxlJobLog);
	/**
	 * 更新 日志信息
	 *
	 */
	public int updateHandleInfo(XxlJobLog xxlJobLog);
	/**
	 * 根据任务id 删除 日志
	 *
	 */
	public int delete(@Param("jobId") int jobId);

	/**
	 * 从xxl_job_log 获取一段时间 内的 数据
	 *
	 */
	public Map<String, Object> findLogReport(@Param("from") Date from,
											 @Param("to") Date to);
	/**
	 * 查询   要清除 的日志
	 *
	 */
	public List<Long> findClearLogIds(@Param("jobGroup") int jobGroup,
									  @Param("jobId") int jobId,
									  @Param("clearBeforeTime") Date clearBeforeTime,
									  @Param("clearBeforeNum") int clearBeforeNum,
									  @Param("pagesize") int pagesize);


	/**
	 *  删除失败的  日志
	 * */
	public int clearLog(@Param("logIds") List<Long> logIds);
	/**
	 *  查询 多个 失败的  任务
	 * */
	public List<Long> findFailJobLogIds(@Param("pagesize") int pagesize);

	/**
	 *  更新 日志  报警状态  xxl_job_log
	 *  告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败
	 * */
	public int updateAlarmStatus(@Param("logId") long logId,
								 @Param("oldAlarmStatus") int oldAlarmStatus,
								 @Param("newAlarmStatus") int newAlarmStatus);

	/**
	 *调度记录停留在 "运行中" 状态超过10min 的数据
	 * 当前时间 提前10分钟
	 * */
	public List<Long> findLostJobIds(@Param("losedTime") Date losedTime);

}
