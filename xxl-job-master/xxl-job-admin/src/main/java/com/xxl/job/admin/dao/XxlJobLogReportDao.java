package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobLogReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * job log
 * @author xuxueli 2019-11-22
 */

@Mapper
public interface XxlJobLogReportDao {


	/**
	 * 新增  每天  的 日志 报表 记录
	 */
	public int save(XxlJobLogReport xxlJobLogReport);

	/**
	 * 刷新  3 天之内的日志报表  信息
	 */
	public int update(XxlJobLogReport xxlJobLogReport);
	/**
	 * 根据开始时间  结束时间  list 查询 xxl_job_log_report
	 *
	 */
	public List<XxlJobLogReport> queryLogReport(@Param("triggerDayFrom") Date triggerDayFrom,
												@Param("triggerDayTo") Date triggerDayTo);
	/**
	 * xxl_job_log_report
	 * 查询整个表  全部的数据
	 */
	public XxlJobLogReport queryLogReportTotal();

}
