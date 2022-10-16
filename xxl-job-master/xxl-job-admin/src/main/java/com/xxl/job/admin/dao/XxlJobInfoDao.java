package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * job info
 * @author xuxueli 2016-1-12 18:03:45
 */
@Mapper
public interface XxlJobInfoDao {

	public List<XxlJobInfo> pageList(@Param("offset") int offset,
									 @Param("pagesize") int pagesize,
									 @Param("jobGroup") int jobGroup,
									 @Param("triggerStatus") int triggerStatus,
									 @Param("jobDesc") String jobDesc,
									 @Param("executorHandler") String executorHandler,
									 @Param("author") String author);

	/**
	 * job info
	 *  分页 查询  xxl_job_info
	 */
	public int pageListCount(@Param("offset") int offset,
							 @Param("pagesize") int pagesize,
							 @Param("jobGroup") int jobGroup,
							 @Param("triggerStatus") int triggerStatus,
							 @Param("jobDesc") String jobDesc,
							 @Param("executorHandler") String executorHandler,
							 @Param("author") String author);
	
	public int save(XxlJobInfo info);

	/**
	 *  根据 id  查询 任务
	 *   xxl_job_info
	 */
	public XxlJobInfo loadById(@Param("id") int id);
	/**
	 *  停止  任务的时候，就是更新任务，将任务的调度状态 改为  停止
	 *   xxl_job_info
	 */
	public int update(XxlJobInfo xxlJobInfo);
	/**
	 *  根据id删除
	 *   xxl_job_info   表里面的数据
	 */
	public int delete(@Param("id") long id);

	public List<XxlJobInfo> getJobsByGroup(@Param("jobGroup") int jobGroup);
	/**
	 *  查询 任务表 的全部的  个数
	 *   xxl_job_info   表里面的数据
	 */
	public int findAllCount();
	/**
	 *  查询多个  下次执行时间的  任务数据
	 *   xxl_job_info
	 */
	public List<XxlJobInfo> scheduleJobQuery(@Param("maxNextTime") long maxNextTime, @Param("pagesize") int pagesize );
	/**
	 *  更新任务信息
	 *   xxl_job_info
	 */
	public int scheduleUpdate(XxlJobInfo xxlJobInfo);


}
