package com.xxl.job.admin.core.scheduler;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.thread.*;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.client.ExecutorBizClient;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 任务 调度器
 * @author xuxueli 2018-10-28 00:18:17
 */

public class XxlJobScheduler  {

//    定义日志类
    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);


    /**
     *
     * 初始化方法，初始化各个线程
     */
    public void init() throws Exception {
        // init i18n  初始化 国际化
        initI18n();   // 初始化语言配置（application.properties中的xxl.job.i18n）
        // admin trigger pool start 配置任务触发线程池
//        相当于初始化 两个线程池
       JobTriggerPoolHelper.toStart();

        // admin registry monitor run
//        保证 任务执行的时候拿到的执行器列表 都是运行的
//        每30秒查询数据库里面自动注册的执行器
//        查询 90s内未再次注册的执行器
//        清除90s内未再次注册的执行器  register表（默认心跳保活时间30s）
//        更新 group表的  addressList
        JobRegistryHelper.getInstance().start();

        // admin fail-monitor run
//        任务失败重试处理  每10ms 执行一次
//        更新日志的  监控状态
        JobFailMonitorHelper.getInstance().start();

        // admin lose-monitor run ( depend on JobTriggerPoolHelper )
//       执行器执行任务10min内没有给出结果回复，终止该任务
//        任务结果丢失处理：调度记录停留在“运行中”状态超过10分钟，并且对应执行器心跳注册失败不在线，
//        则将本地调度主动标记 失败

//       就是  将 超过10分钟还在执行的  任务状态  改为 失败
        JobCompleteHelper.getInstance().start();

        // admin log report start
//        运行报表数据显示  归总日志，清除日志（只是清除数据库）
        JobLogReportHelper.getInstance().start();

        // start-schedule  ( depend on JobTriggerPoolHelper )
        // /   一直扫描，将即将触发的任务放到时间轴，
//        从时间轴获取到  当前秒 需要执行的任务 ，进行执行
        JobScheduleHelper.getInstance().start();

        logger.info(">>>>>>>>> init xxl-job admin success.");
    }



    /**
     *
     * 销毁各个线程
     */
    public void destroy() throws Exception {

        // stop-schedule
        JobScheduleHelper.getInstance().toStop();

        // admin log report stop
        JobLogReportHelper.getInstance().toStop();

        // admin lose-monitor stop
        JobCompleteHelper.getInstance().toStop();

        // admin fail-monitor stop
        JobFailMonitorHelper.getInstance().toStop();

        // admin registry stop
        JobRegistryHelper.getInstance().toStop();

        // admin trigger pool stop
        JobTriggerPoolHelper.toStop();

    }



    // ---------------------- I18n ----------------------
    /**
     *  初始化   阻塞策略 的  中文名称
     */
    private void initI18n(){
//        遍历全部的  阻塞策略
        for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {

            item.setTitle(I18nUtil.getString("jobconf_block_".concat(item.name())));
        }
    }

    // ---------------------- executor-client  执行器客户端----------------------

    /**
     * ExecutorBiz 客户端的  map 集合
     * 调用  我们部署项目  的 ExecutorBiz 远程实体类
     */
    private static ConcurrentMap<String, ExecutorBiz> executorBizRepository = new ConcurrentHashMap<String, ExecutorBiz>();



    /**
     *  根据地址获取  远程地址服务 对象
     *  如果获取不到，就创建一个远程对象，放到map里面
     */
    public static ExecutorBiz getExecutorBiz(String address) throws Exception {
        // valid
        if (address==null || address.trim().length()==0) {
            return null;
        }

        // load-cache
        address = address.trim();

//       executorBizRepository  是  ExecutorBiz 客户端的  map 集合
        ExecutorBiz executorBiz = executorBizRepository.get(address);
        if (executorBiz != null) {
            return executorBiz;
        }

        // set-cache   创建项目对象
        executorBiz = new ExecutorBizClient(address, XxlJobAdminConfig.getAdminConfig().getAccessToken());

//        将项目对象放到仓库
        executorBizRepository.put(address, executorBiz);
        return executorBiz;
    }

}
