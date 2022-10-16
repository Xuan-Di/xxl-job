package com.xxl.job.core.context;

import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * helper for xxl-job
 *  对日志的处理
 *  日志系统逻辑
 * @author jing
 */
public class XxlJobHelper {

    // ---------------------- base info ----------------------

    /**
     * current JobId
     *  获取到当前的任务id
     * @return
     */
    public static long getJobId() {

//        根据上下文 获取到  当前的上下文对象
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return -1;
        }

        return xxlJobContext.getJobId();
    }

    /**
     * current JobParam
     * 获取当前的任务参数
     * @return
     */
    public static String getJobParam() {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return null;
        }

        return xxlJobContext.getJobParam();
    }

    // ---------------------- for log ----------------------

    /**
     * current JobLogFileName
     *  获取到当前的  任务的日志文件名称
     * @return
     */
    public static String getJobLogFileName() {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return null;
        }

        return xxlJobContext.getJobLogFileName();
    }

    // ---------------------- for shard ----------------------

    /**
     * current ShardIndex
     *  获取到当前的任务的   分片索引
     * @return
     */
    public static int getShardIndex() {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return -1;
        }

        return xxlJobContext.getShardIndex();
    }

    /**
     * current ShardTotal
     * 获取到当前任务的  分片的总数
     * @return
     */
    public static int getShardTotal() {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return -1;
        }

        return xxlJobContext.getShardTotal();
    }

    // ---------------------- tool for log ----------------------

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger("xxl-job logger");

    /**
     * append log with pattern
     * 用模式追加日志
     * @param appendLogPattern  like "aaa {} bbb {} ccc"
     * @param appendLogArguments    like "111, true"    String[] str2={"rrrr","yyyyy"};
     */
    public static boolean log(String appendLogPattern, Object ... appendLogArguments) {
        // 使用slf4j解析器格式化日志内容
        FormattingTuple ft = MessageFormatter.arrayFormat(appendLogPattern, appendLogArguments);
        String appendLog = ft.getMessage();  // aaa rrrr bbb yyyyy ccc

        /*appendLog = appendLogPattern;
        if (appendLogArguments!=null && appendLogArguments.length>0) {
            appendLog = MessageFormat.format(appendLogPattern, appendLogArguments);
        }*/
        // 获得栈帧信息
//        这是获得调用栈帧方法，索引0为当前栈帧，
//        1为调用栈帧，以此类推，此处获得的是索引1，
//        也就是说获得的是调用该方法的栈帧信息，
//        可以通过StackTraceElement获得调用类名，方法名，行数等信息
        StackTraceElement callInfo = new Throwable().getStackTrace()[1];

//        记录 日志
        return logDetail(callInfo, appendLog);
    }


    /**
     * append exception stack
     * 添加异常堆栈
     * @param e
     */
    public static boolean log(Throwable e) {

        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
//        将异常变成  字符串
        String appendLog = stringWriter.toString();

        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLog);
    }

//    public static void main(String[] args) {
//
//        String source ="aaa {} bbb {} ccc";
//        String[] str2={"rrrr","yyyyy"};
//        FormattingTuple ft = MessageFormatter.arrayFormat(source, str2);
//        String appendLog = ft.getMessage();
//        System.out.println(appendLog);
//        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
//        System.out.println(callInfo);
//
//    }


    /**
     * append log
     *  追加 日志
     * @param callInfo  哪个方法调用这个log方法，就把哪个方法的全部的信息 保存到StackTraceElement里
     * @param appendLog  我们要记录的日志
     */
    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        // 获得当前上下文对象
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }

        /*// "yyyy-MM-dd HH:mm:ss [ClassName]-[MethodName]-[LineNumber]-[ThreadName] log";
        StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
        StackTraceElement callInfo = stackTraceElements[1];*/


        // 拼接格式化日志信息
//        就是拼接  哪个方法记录了哪个  日志
        StringBuffer stringBuffer = new StringBuffer();

        stringBuffer.append(DateUtil.formatDateTime(new Date())).append(" ")
                .append("["+ callInfo.getClassName() + "#" + callInfo.getMethodName() +"]").append("-")
                .append("["+ callInfo.getLineNumber() +"]").append("-")
                .append("["+ Thread.currentThread().getName() +"]").append(" ")
                .append(appendLog!=null?appendLog:"");

//       最后的拼接的日志信息  里面包含 哪个方法记录哪个日志
        String formatAppendLog = stringBuffer.toString();

        // appendlog  // 获得日志文件路径
        String logFileName = xxlJobContext.getJobLogFileName();

        if (logFileName!=null && logFileName.trim().length()>0) {

            // 流的形式将日志写入本地文件
//            根据日志文件路径  ，将拼接的东西写进去
            XxlJobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        } else {
            logger.info(">>>>>>>>>>> {}", formatAppendLog);
            return false;
        }
    }

    // ---------------------- tool for handleResult ----------------------

    /**
     * handle success
     *  将成功 保存到上下文里面
     * @return
     */
    public static boolean handleSuccess(){
        return handleResult(XxlJobContext.HANDLE_CODE_SUCCESS, null);
    }

    /**
     * handle success with log msg
     * 将成功的信息保存到上下文里面
     * @param handleMsg
     * @return
     */
    public static boolean handleSuccess(String handleMsg) {
        return handleResult(XxlJobContext.HANDLE_CODE_SUCCESS, handleMsg);
    }

    /**
     * handle fail
     *  将失败  保存到上下文里面
     * @return
     */
    public static boolean handleFail(){
        return handleResult(XxlJobContext.HANDLE_CODE_FAIL, null);
    }

    /**
     * handle fail with log msg
     * 将失败的  日志信息保存到上下文里面
     * @param handleMsg
     * @return
     */
    public static boolean handleFail(String handleMsg) {
        return handleResult(XxlJobContext.HANDLE_CODE_FAIL, handleMsg);
    }

    /**
     * handle timeout
     * 将超时  保存到上下文里面
     * @return
     */
    public static boolean handleTimeout(){
        return handleResult(XxlJobContext.HANDLE_CODE_TIMEOUT, null);
    }

    /**
     * handle timeout with log msg
     * 将超时的  日志信息  保存 到  上下文里面
     * @param handleMsg   日志信息
     * @return
     */
    public static boolean handleTimeout(String handleMsg){
        return handleResult(XxlJobContext.HANDLE_CODE_TIMEOUT, handleMsg);
    }

    /**
     * 判断 当前 上次文 TRUE  or  FALSE  ，并且把错误信息保存到  上下文里面
     * @param handleCode
     *
     *      200 : success
     *      500 : fail
     *      502 : timeout
     *
     * @param handleMsg
     * @return
     */
    public static boolean handleResult(int handleCode, String handleMsg) {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }

        xxlJobContext.setHandleCode(handleCode);
        if (handleMsg != null) {
            xxlJobContext.setHandleMsg(handleMsg);
        }
        return true;
    }


}
