package com.xxl.job.core.thread;

import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * job file clean thread
 *  我们项目里面调用的
 *  日志文件清除   线程，就是日志目录里面的日志如果超过设置的  保留时间，就删除
 * @author
 */
public class JobLogFileCleanThread {

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(JobLogFileCleanThread.class);

//    静态创建当前类对象  ，也就是一加载这个项目，就有这个对象
    private static JobLogFileCleanThread instance = new JobLogFileCleanThread();
//   对外暴露一个方法，获取静态创建的对象
    public static JobLogFileCleanThread getInstance(){
        return instance;
    }

//    定义一个线程
    private Thread localThread;
//    定义一个停止变量
    private volatile boolean toStop = false;

    /**
     *
     * 开始删除
     *
     *
     *  根据日志保留时间  进行删除日志目录里面，已经超时的日志
     * @param logRetentionDays  日志保留时间
     */
    public void start(final long logRetentionDays){

        // limit min value
        if (logRetentionDays < 3 ) {
            return;
        }

//        创建线程  经常删除
        localThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!toStop) {
//                    如果没有停止当前线程
                    try {
                        // clean log dir, over logRetentionDays
//                        遍历  基础日志目录下的  日志文件
                        File[] childDirs = new File(XxlJobFileAppender.getLogPath()).listFiles();
                        if (childDirs!=null && childDirs.length>0) {

                            // today  获取当前的日期
                            Calendar todayCal = Calendar.getInstance();
                            todayCal.set(Calendar.HOUR_OF_DAY,0);
                            todayCal.set(Calendar.MINUTE,0);
                            todayCal.set(Calendar.SECOND,0);
                            todayCal.set(Calendar.MILLISECOND,0);

                            Date todayDate = todayCal.getTime();

                            //  遍历每一个日志文件
                            for (File childFile: childDirs) {

                                // valid
                                if (!childFile.isDirectory()) {
                                    continue;
                                }
                                if (childFile.getName().indexOf("-") == -1) {
                                    continue;
                                }

                                // file create date  日志文件创建时间
                                Date logFileCreateDate = null;
                                try {
                                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                    logFileCreateDate = simpleDateFormat.parse(childFile.getName());
                                } catch (ParseException e) {
                                    logger.error(e.getMessage(), e);
                                }

//                                logFileCreateDate  是 每一个文件的日志创建时间
                                if (logFileCreateDate == null) {
                                    continue;
                                }

//                                如果今天时间和日志创建时间之差  大于设置的日志保留时间
                                if ((todayDate.getTime()-logFileCreateDate.getTime()) >= logRetentionDays * (24 * 60 * 60 * 1000) ) {
//                                    递归删除文件
                                    FileUtil.deleteRecursively(childFile);
                                }

                            }
                        }

                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }

                    }

                    try {
                        TimeUnit.DAYS.sleep(1);
                    } catch (InterruptedException e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, executor JobLogFileCleanThread thread destroy.");

            }
        });
        localThread.setDaemon(true);
        localThread.setName("xxl-job, executor JobLogFileCleanThread");
        localThread.start();
    }


    /**
     *  停止删除日志信息
     *
     */
    public void toStop() {
        toStop = true;

        if (localThread == null) {
            return;
        }

        // interrupt and wait
//        中断当前的线程
        localThread.interrupt();
        try {
//            将当前线程加到队列里面
            localThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

}
