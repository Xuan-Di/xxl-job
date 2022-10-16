package com.xxl.job.core.executor.impl;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * XxlJobExecutor提供注册Job、初始化Server等功能、
 * 核心方法 registJobHandler、initEmbedServer。
 *
 *
 * 对于spring项目的 执行器
 * xxl-job executor (for spring)
 *  我们项目  初始化的时候执行
 * @author xuxueli 2018-11-01 09:24:52
 */
public class XxlJobSpringExecutor extends XxlJobExecutor implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

//   创建日志对象
    private static final Logger logger = LoggerFactory.getLogger(XxlJobSpringExecutor.class);


    // start
    @Override
    public void afterSingletonsInstantiated() {

        // init JobHandler Repository
        /*initJobHandlerRepository(applicationContext);*/

        // init JobHandler Repository (for method)
//        初始化  spring里面的方法的处理器
//        根据上下文 将 里面的写了定时的方法都弄出来
//        我们的项目启动的时候，已经将所有的bean对象都放到applicationContext
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory  刷新运行工厂
        GlueFactory.refreshInstance(1);

        // super start  初始化注册
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // destroy  销毁当前任务执行器
    @Override
    public void destroy() {
        super.destroy();
    }


    /*private void initJobHandlerRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler action
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(JobHandler.class);

        if (serviceBeanMap != null && serviceBeanMap.size() > 0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler) {
                    String name = serviceBean.getClass().getAnnotation(JobHandler.class).value();
                    IJobHandler handler = (IJobHandler) serviceBean;
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                    }
                    registJobHandler(name, handler);
                }
            }
        }
    }*/


    /**
     *  初始化  处理器  上下文
     *  获取 每一个bean里面的方法对象
     * */
    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }
        // init job handler from method 从方法初始化作业处理程序
//        获取到的所有的bean name，对于这个方法，其实有两个参数可以使用，
//        把第二个参数设置成false，就可以只取scope为singleton的bean了，

//        获取所有单列bean  name
        String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
        for (String beanDefinitionName : beanDefinitionNames) {
//            遍历每一个 bean
            Object bean = applicationContext.getBean(beanDefinitionName);


//           从所有的spring管理器  里面   有  xxljob 注解的  所有的方法
            Map<Method, XxlJob> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
            try {
                annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                        new MethodIntrospector.MetadataLookup<XxlJob>() {
                            @Override
//                          检查出 XxlJob为注解的方法
                            public XxlJob inspect(Method method) {
                                return AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
                            }
                        });
            } catch (Throwable ex) {
                logger.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
            }

//            Map<Method, XxlJob>
            if (annotatedMethods==null || annotatedMethods.isEmpty()) {
                continue;
            }


//          Map<Method, XxlJob>
//            遍历每一个  xxljob 注解的方法
            for (Map.Entry<Method, XxlJob> methodXxlJobEntry : annotatedMethods.entrySet()) {
//               每一个执行的方法对象
                Method executeMethod = methodXxlJobEntry.getKey();
//                注解对象
                XxlJob xxlJob = methodXxlJobEntry.getValue();
                // regist

//               xxlJob 是注解   bean是类对象  executeMethod 方法对象
                registJobHandler(xxlJob, bean, executeMethod);
            }
        }
    }

    // ---------------------- applicationContext ----------------------

    //    上下文处理器 内容
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        XxlJobSpringExecutor.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
