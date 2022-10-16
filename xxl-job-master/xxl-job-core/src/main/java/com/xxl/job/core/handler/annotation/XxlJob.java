package com.xxl.job.core.handler.annotation;

import java.lang.annotation.*;

/**
 * annotation for method jobhandler
 *
 * @author xuxueli 2019-12-11 20:50:13
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface XxlJob {

    /**
     * jobhandler name
     * 当前定时的名称
     */
    String value();

    /**
     * init handler, invoked when JobThread init
     * 初始化处理程序，当JobThread初始化时调用
     */
    String init() default "";

    /**
     * destroy handler, invoked when JobThread destroy
     * 销毁处理程序，当JobThread销毁调用
     */
    String destroy() default "";

}
