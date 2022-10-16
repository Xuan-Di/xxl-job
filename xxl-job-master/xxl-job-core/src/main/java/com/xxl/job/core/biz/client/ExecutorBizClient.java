package com.xxl.job.core.biz.client;

import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.model.*;
import com.xxl.job.core.util.XxlJobRemotingUtil;

/**
 *  根据 地址 ，创建对象
 *  web项目里面操作的类
 * @author xuxueli 2017-07-28 22:14:52
 */
public class ExecutorBizClient implements ExecutorBiz {

    public ExecutorBizClient() {
    }


    public ExecutorBizClient(String addressUrl, String accessToken) {
        this.addressUrl = addressUrl;  // 我们项目的地址
        this.accessToken = accessToken;

        // valid
        if (!this.addressUrl.endsWith("/")) {
            this.addressUrl = this.addressUrl + "/";
        }
    }

    private String addressUrl ;
    private String accessToken;
    private int timeout = 3;



    /**
     *  心跳检测   web项目调用  我们项目，看我们项目是否活的
     */
    @Override
    public ReturnT<String> beat() {
        return XxlJobRemotingUtil.postBody(addressUrl+"beat", accessToken, timeout, "", String.class);
    }
    /**
     *  心跳检测   看某一个任务是否在队列里面
     */
    @Override
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam){
        return XxlJobRemotingUtil.postBody(addressUrl+"idleBeat", accessToken, timeout, idleBeatParam, String.class);
    }


    /**
     *  web 端调用我们的项目，运行某一个方法
     */
    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "run", accessToken, timeout, triggerParam, String.class);
    }
    /**
     *  web 端调用我们的项目，暂停某一个方法
     */
    @Override
    public ReturnT<String> kill(KillParam killParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "kill", accessToken, timeout, killParam, String.class);
    }
    /**
     *  web 端调用我们的项目，查询我们的日志
     */
    @Override
    public ReturnT<LogResult> log(LogParam logParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "log", accessToken, timeout, logParam, LogResult.class);
    }

}
