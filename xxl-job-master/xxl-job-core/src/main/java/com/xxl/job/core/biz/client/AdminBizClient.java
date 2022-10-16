package com.xxl.job.core.biz.client;

import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.RegistryParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.XxlJobRemotingUtil;

import java.util.List;

/**
 * admin api test
 * 客户端调用服务端的接口定义 实现类
 * 就是我们的  项目 调用  web服务端的   实现类
 * 在我们项目里面操作的
 * @author xuxueli 2017-07-28 22:14:52
 */
public class AdminBizClient implements AdminBiz {

    public AdminBizClient() {
    }

//   根据 web服务器端的  地址与令牌  的构造函数
    public AdminBizClient(String addressUrl, String accessToken) {
        this.addressUrl = addressUrl;
        this.accessToken = accessToken;

        // valid  有效的
        if (!this.addressUrl.endsWith("/")) {
            this.addressUrl = this.addressUrl + "/";
        }
    }

    private String addressUrl ;  // 服务器的地址
    private String accessToken; // 令牌
    private int timeout = 3; // 超时时间



    /**
     * 客户端调用服务端的   回调函数
     */
    @Override
    public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList) {
//            返回的是  调用远程之后得到的结果数据
        return XxlJobRemotingUtil.postBody(addressUrl+"api/callback", accessToken, timeout, callbackParamList, String.class);
    }


    /**
     * 客户端调用服务端的   注册
     * 将我们的项目   注册  到 web里面
     */
    @Override
    public ReturnT<String> registry(RegistryParam registryParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "api/registry", accessToken, timeout, registryParam, String.class);
    }
    /**
     * 我们项目调用 web  注册移除
     */
    @Override
    public ReturnT<String> registryRemove(RegistryParam registryParam) {
        return XxlJobRemotingUtil.postBody(addressUrl + "api/registryRemove", accessToken, timeout, registryParam, String.class);
    }

}
