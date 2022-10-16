package com.xxl.job.admin.core.model;

import java.util.Date;
/**
 *
 *
 * @author xuxueli 2016-5-19 17:57:46
 */
public class XxlJobLogReport {

    private int id;

    private Date triggerDay; // 调度 时间

    private int runningCount;// 运行中日志数量
    private int sucCount; // 执行成功 - 日志数量
    private int failCount;// 执行失败 - 日志数量

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getTriggerDay() {
        return triggerDay;
    }

    public void setTriggerDay(Date triggerDay) {
        this.triggerDay = triggerDay;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(int runningCount) {
        this.runningCount = runningCount;
    }

    public int getSucCount() {
        return sucCount;
    }

    public void setSucCount(int sucCount) {
        this.sucCount = sucCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }
}
