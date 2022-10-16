package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.trigger.XxlJobTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * job trigger thread pool helper
 * 主要职责：  任务投递和下次执行时间维护
 *
 * 包含两个线程池，
 * 快速触发任务线程池和慢触发任务线程池。
 * 会根据每分钟执行的次数决定任务投递到快速触发任务线程池
 * 还是慢触发任务线程池中 。
 *
 *
 * @author xuxueli 2018-07-03 21:08:07
 */
public class JobTriggerPoolHelper {

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(JobTriggerPoolHelper.class);


    // ---------------------- trigger pool ----------------------

    // fast/slow thread pool
    private ThreadPoolExecutor fastTriggerPool = null;
    private ThreadPoolExecutor slowTriggerPool = null;


    /**
     * 仅仅  创建 两个线程池
     * 在项目初始化的时候，就创建了
     */
    public void start(){

//        创建 快执行线程池
        fastTriggerPool = new ThreadPoolExecutor(
                10,
                XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(1000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobTriggerPoolHelper-fastTriggerPool-" + r.hashCode());
                    }
                });



        //        创建 慢线程池
        slowTriggerPool = new ThreadPoolExecutor(
                10,
                XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(2000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobTriggerPoolHelper-slowTriggerPool-" + r.hashCode());
                    }
                });
    }


    /**
     * 停止 当前的线程池
     */
    public void stop() {
        //triggerPool.shutdown();
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
        logger.info(">>>>>>>>> xxl-job trigger thread pool shutdown success.");
    }


    //System.currentTimeMillis() 获取系统时间ms
//    minTim 表示当前时间的分钟
    private volatile long minTim = System.currentTimeMillis()/60000;     // ms > min

//    jobTimeoutCountMap，存放每个任务的执行慢次数，60秒后自动清空该容器
//    key 是任务id   value 是  次数
    private volatile ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();


    /**
     * add trigger 新增 触发器
     *
     * 执行任务时，首先判断这个任务是否是个慢任务，
     * 如果是个慢任务且慢执行的次数超过了10次将会使用slowTriggerPool慢线程池，
     * 它的统计周期为60秒，这里是个优化点，当有大量的任务被执行时，
     * 为了防止任务被阻塞，尽可能的会先让执行快的任务优先执行。
     *
     *
     * @param jobId  任务id
     * @param triggerType  触发枚举类型
     * @param failRetryCount  失败重试次数  执行失败 重试 次数 - 1
     * @param executorShardingParam   分片参数
     * @param executorParam   方法参数
     * @param addressList  服务器地址列表
     */
    public void  addTrigger(final int jobId,
                           final TriggerTypeEnum triggerType,
                           final int failRetryCount,
                           final String executorShardingParam,
                           final String executorParam,
                           final String addressList) {

        // choose thread pool  选择线程池
        ThreadPoolExecutor triggerPool_ = fastTriggerPool;

//        根据任务id   从 ConcurrentMap 里面获取 任务的慢查询的次数
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);

//        1分钟超时10次
//        c如果发现任务一分钟内有大于10次的慢执行，换slowTriggerPool线程池
        if (jobTimeoutCount!=null && jobTimeoutCount.get() > 10) {      // job-timeout 10 times in 1 min
//            选择慢线程池
            triggerPool_ = slowTriggerPool;
        }



        // trigger  执行触发器线程 线程池执行
        triggerPool_.execute(new Runnable() {
            @Override
            public void run() {

//                获取当前时间
                long start = System.currentTimeMillis();

                try {
                    // do trigger  开始执行任务
                    XxlJobTrigger.trigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam, addressList);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {

                    // check timeout-count-map
//                    // 到达下一个周期则清理上一个周期数据
//                       minTim 表示当前时间的分钟
                    long minTim_now = System.currentTimeMillis()/60000;

                    //  表示线程执行的时间，进行对比
//                    如果相等，就很快执行完成，否则 执行慢，就把
//                    慢查询 次数清空
                    if (minTim != minTim_now) {
                        minTim = minTim_now;
                        jobTimeoutCountMap.clear();
                    }

                    // incr timeout-count-map
                    // 记录 任务执行的时间长度
                    long cost = System.currentTimeMillis()-start;
                    if (cost > 500) {       // ob-timeout threshold 500ms
                        // 执行时间超过500毫秒，则认定为慢任务

//                        根据任务id，将慢查询 日志   放到map里面
                        AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                        if (timeoutCount != null) {
                            // 记录慢任务慢的次数
                            timeoutCount.incrementAndGet();
                        }
                    }

                }

            }
        });
    }

//    public static void main(String[] args) {
//
//        ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();
//
//        //分10个线程，每个线程自增2000次
//        for (int i = 0; i < 10; i++) {
//            new Thread(new Runnable() {
//                public void run() {
//                    for (int i = 0; i < 2000; i++) {
//
//                        AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(22, new AtomicInteger(1));
//                        if (timeoutCount != null) {
//                            // 记录慢任务慢的次数
//                            timeoutCount.incrementAndGet();
//                        }
//                    }
//                }
//            }).start();
//        }
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        System.out.println(jobTimeoutCountMap.get(22));
//
//    }

    // ---------------------- helper   我们的项目调用  web项目----------------------

    private static JobTriggerPoolHelper helper = new JobTriggerPoolHelper();

    public static void toStart() {
        helper.start();
    }
    public static void toStop() {
        helper.stop();
    }

    /**
     * 开始调度  任务
     * @param jobId  任务 id
     * @param triggerType   调度类型
     * @param failRetryCount  执行失败 重试 次数 - 1
     * 			>=0: use this param
     * 			<0: use param from job info config
     * @param executorShardingParam   执行器任务分片参数，格式如 1/2
     * @param executorParam   执行  方法  参数
     *          null: use job param
     *          not null: cover job param
     * @param addressList  服务器地址
     */
    public static void trigger(int jobId, TriggerTypeEnum triggerType, int failRetryCount, String executorShardingParam, String executorParam, String addressList) {

        helper.addTrigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam, addressList);
    }

}
