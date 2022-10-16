package com.xxl.job.admin.core.alarm;

import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;

/**
 * 告警信息
 * @author xuxueli 2020-01-19
 */
public interface JobAlarm {

    /**
     * job  alarm
     * @param info  任务信息
     * @param jobLog  日志信息
     * @return
     */
    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog);

}
