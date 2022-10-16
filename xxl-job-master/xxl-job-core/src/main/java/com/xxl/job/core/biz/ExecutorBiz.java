package com.xxl.job.core.biz;

import com.xxl.job.core.biz.model.*;

/**
 * Created by jing
 * 服务端调用客户端的接口定义
 * 执行器代理ExecutorBiz实现很简单：就是发送http请求
 * web项目------到------我们部署的项目
 */
public interface ExecutorBiz {

    /**
     * beat 心跳检测
     * @return
     */
    public ReturnT<String> beat();

    /**
     * idle beat
     *  空闲  心跳  检查
     * @param idleBeatParam
     * @return
     */
    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam);

    /**
     * run
     * @param triggerParam
     * @return
     */
    public ReturnT<String> run(TriggerParam triggerParam);

    /**
     * kill  终止 任务
     * @param killParam
     * @return
     */
    public ReturnT<String> kill(KillParam killParam);

    /**
     * log  根据日志参数  调用远方，获取到日志结果
     * @param logParam
     * @return
     */
    public ReturnT<LogResult> log(LogParam logParam);

}
