package com.xxl.job.core.biz;

import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.RegistryParam;
import com.xxl.job.core.biz.model.ReturnT;

import java.util.List;

/**
 * @author jing
 * 客户端调用服务端的接口定义
 * 我们 项目 调用  web 的相关的接口
提供 callback (回调)、registry (注册)
以及 registryRemove (注册移除) 到调度中心的方法。

 我们项目里面操作的
 */
public interface AdminBiz {


    // ---------------------- callback ----------------------

    /**
     * callback
     *  我们项目 调用  web 项目  回调的方法
     * @param callbackParamList
     * @return
     */
    public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList);


    // ---------------------- registry ----------------------

    /**
     * registry
     *我们项目 调用  web 项目  注册的方法
     * @param registryParam
     * @return
     */
    public ReturnT<String> registry(RegistryParam registryParam);

    /**
     * registry remove
     * 我们项目 调用  web 项目  删除注册的方法
     *
     * @param registryParam
     * @return
     */
    public ReturnT<String> registryRemove(RegistryParam registryParam);


    // ---------------------- biz (custome) ----------------------
    // group、job ... manage

}
