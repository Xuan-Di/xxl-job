package com.xxl.job.core.context;

/**
 * xxl-job context
 *  上下文处理
 * @author jing
 * [Dear hj]
 */
public class XxlJobContext {

//    上下文 状态码
    public static final int HANDLE_CODE_SUCCESS = 200;
    public static final int HANDLE_CODE_FAIL = 500;
    public static final int HANDLE_CODE_TIMEOUT = 502;

    // ---------------------- base info ----------------------

    /**
     * job id   任务id
     */
    private final long jobId;

    /**
     * job param  任务参数
     */
    private final String jobParam;

    // ---------------------- for log ----------------------

    /**
     * job log filename  日志文件名称
     */
    private final String jobLogFileName;

    // ---------------------- for shard ----------------------

    /**
     * shard index  分片起始位置
     */
    private final int shardIndex;

    /**
     * shard total  分片总数
     */
    private final int shardTotal;

    // ---------------------- for handle ----------------------

    /**
     * handleCode：The result status of job execution
     * 作业执行的结果状态
     *
     *      200 : success
     *      500 : fail
     *      502 : timeout
     *  执行结果
     */
    private int handleCode;

    /**
     * handleMsg：The simple log msg of job execution
     * 作业执行的简单日志信息
     *
     * 执行日志
     */
    private String handleMsg;

    /**
     * 构造函数
     * @param jobId  任务id
     * @param jobParam  任务参数
     * @param jobLogFileName  任务日志文件名称
     * @param shardIndex  分片下标
     * @param shardTotal  分片总数
     */
    public XxlJobContext(long jobId, String jobParam, String jobLogFileName, int shardIndex, int shardTotal) {
        this.jobId = jobId;
        this.jobParam = jobParam;
        this.jobLogFileName = jobLogFileName;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
//  执行结果  默认成功
        this.handleCode = HANDLE_CODE_SUCCESS;  // default success
    }

    public long getJobId() {
        return jobId;
    }

    public String getJobParam() {
        return jobParam;
    }

    public String getJobLogFileName() {
        return jobLogFileName;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    public void setHandleCode(int handleCode) {
        this.handleCode = handleCode;
    }

    public int getHandleCode() {
        return handleCode;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public String getHandleMsg() {
        return handleMsg;
    }




    // ---------------------- tool ----------------------
//    支持作业处理程序的子线程)
//     子线程  集合
    private static InheritableThreadLocal<XxlJobContext> contextHolder = new InheritableThreadLocal<XxlJobContext>(); // support for child thread of job handler)



    /**
     * 往集合  里面 添加任务内容
     */
    public static void setXxlJobContext(XxlJobContext xxlJobContext){
        contextHolder.set(xxlJobContext);
    }

    /**
     * 从 集合 里面 获取数据 xxlJobContext
     */
    public static XxlJobContext getXxlJobContext(){
        return contextHolder.get();
    }

}