package com.xxl.job.core.handler.impl;

import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.util.ScriptUtil;

import java.io.File;

/**
 * Created by jing
 *  运行模式是 脚本的  执行模式
 */
public class ScriptJobHandler extends IJobHandler {

    private int jobId;  // 任务 id
    private long glueUpdatetime; // 日志 更新时间
    private String gluesource; // 运行代码
    private GlueTypeEnum glueType;  // 运行模式，一般是bean ，还有其他java脚本


//    构造函数  并且删除旧的脚本
    public ScriptJobHandler(int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType){
        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // clean old script file  清理旧脚本文件

//        获取日志文件路径
        File glueSrcPath = new File(XxlJobFileAppender.getGlueSrcPath());
        if (glueSrcPath.exists()) {
//            获取到每一个文件
            File[] glueSrcFileList = glueSrcPath.listFiles();
            if (glueSrcFileList!=null && glueSrcFileList.length>0) {
                for (File glueSrcFileItem : glueSrcFileList) {
//                    如果是 以 jobid 开头的，就删除
                    if (glueSrcFileItem.getName().startsWith(String.valueOf(jobId)+"_")) {
                        glueSrcFileItem.delete();
                    }
                }
            }
        }

    }


//    获取 日志更新时间
    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }




    /**
     * Created by jing
     *   执行
     */
    @Override
    public void execute() throws Exception {

        if (!glueType.isScript()) {
//            如果不是脚本
            XxlJobHelper.handleFail("glueType["+ glueType +"] invalid.");
            return;
        }

        // cmd  获取编译器名称
        String cmd = glueType.getCmd();

        // make script file  创建脚本文件路径
        String scriptFileName = XxlJobFileAppender.getGlueSrcPath()
                .concat(File.separator)
                .concat(String.valueOf(jobId))
                .concat("_")
                .concat(String.valueOf(glueUpdatetime))
                .concat(glueType.getSuffix());

//        创建脚本文件
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
//            如果不存在，就使用代码，写入到当前文件
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
        }

        // log file  从上下文list里面，获取文件名称，随机获取一个日志名称
        String logFileName = XxlJobContext.getXxlJobContext().getJobLogFileName();

        // script params：0=param、1=分片序号、2=分片总数
        String[] scriptParams = new String[3];
        scriptParams[0] = XxlJobHelper.getJobParam(); // 任务参数
        scriptParams[1] = String.valueOf(XxlJobContext.getXxlJobContext().getShardIndex());
        scriptParams[2] = String.valueOf(XxlJobContext.getXxlJobContext().getShardTotal());



        // invoke

//        保存日志
        XxlJobHelper.log("----------- script file:"+ scriptFileName +" -----------");


//        执行脚本文件
        int exitValue = ScriptUtil.execToFile(cmd, scriptFileName, logFileName, scriptParams);

//        成功
        if (exitValue == 0) {
            XxlJobHelper.handleSuccess();
            return;
        } else {
//            执行失败
            XxlJobHelper.handleFail("script exit value("+exitValue+") is failed");
            return ;
        }

    }

}
