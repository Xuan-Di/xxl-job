package com.xxl.job.admin.core.model;

import java.util.Date;

/**
 * Created by xuxueli on 16/9/30.
 * 存放每一个app 服务器地址
 */
public class XxlJobRegistry {

    private int id;
    private String registryGroup;  // 注册方式   自动注册，手动录入
    private String registryKey; // app 名称 ， 项目名称
    private String registryValue;// app的地址
    private Date updateTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRegistryGroup() {
        return registryGroup;
    }

    public void setRegistryGroup(String registryGroup) {
        this.registryGroup = registryGroup;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
