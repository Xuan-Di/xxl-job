package com.xxl.job.admin.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by jing
 * 执行器  实体类  就是配置项目的实体类
 */
public class XxlJobGroup {

    private int id;
    private String appname; // 项目名称，这个是在项目yml里面配置的名称
    private String title; // 项目中文名称
    private int addressType;        // 注册方式   执行器地址类型：0=自动注册、1=手动录入
    private String addressList;     // 机器地址    执行器地址列表，多地址逗号分隔(手动录入)
    private Date updateTime; // 更新时间

    // registry list
    private List<String> registryList;  // 执行器地址列表(系统注册)


//    获取手动注入的  项目的  list地址
    public List<String> getRegistryList() {
        if (addressList!=null && addressList.trim().length()>0) {
            registryList = new ArrayList<String>(Arrays.asList(addressList.split(",")));
        }
        return registryList;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getAddressType() {
        return addressType;
    }

    public void setAddressType(int addressType) {
        this.addressType = addressType;
    }

    public String getAddressList() {
        return addressList;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public void setAddressList(String addressList) {
        this.addressList = addressList;
    }

}
