package com.xxl.job.core.glue.impl;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.glue.GlueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 *  spring的  运行模式 工厂
 *  将 一个实体类里面 注入的字段属性，变成bean对象
 * @author jing
 */
public class SpringGlueFactory extends GlueFactory {

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);


    /**
     * inject action of spring
     * 根据string字符串  创建为java对象
     * 对传入的  bean  对象  执行一些事情
     * @param instance
     */
    @Override
    public void injectService(Object instance){
        if (instance==null) {
            return;
        }

//        判断是否有  spring的上下文对象
        if (XxlJobSpringExecutor.getApplicationContext() == null) {
            return;
        }

//       传入的方法   获取全部的  字段属性
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {

//            参数包含static修饰符，则返回true，否则返回false。
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object fieldBean = null;
            // with bean-id,
            // bean could be found by both @Resource and @Autowired,
            // or bean could only be found by @Autowired


//            字段  是否被 @Resource  注释
            if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
                try {
//                    得到  @Resource 注解里面所有的信息
                    Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
                    if (resource.name()!=null && resource.name().length()>0){
//                        根据注解名称  从 上下文   获取bean对象
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(resource.name());
                    } else {
//                        根据字段名称， 从 上下文   获取bean对象
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getName());
                    }
                } catch (Exception e) {
                }


//                如果  字段获取到的bean 对象  为空
                if (fieldBean==null ) {
//                    直接根据  字段类型 获取bean对象
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }
            //            字段  是否被 @Autowired  注释
            else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
                Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
                if (qualifier!=null && qualifier.value()!=null && qualifier.value().length()>0) {

//                    根据值 获取bean对象
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
                } else {
//                    根据字段类型 获取bean对象
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }


//            如果 根据字段 属性  获取到的bean对象不为空
            if (fieldBean!=null) {
                field.setAccessible(true);
                try {

//                      obj：是应该修改其字段的对象，并且
//
//                      value：这是要修改的obj字段的新值。
                    field.set(instance, fieldBean);
                } catch (IllegalArgumentException e) {
                    logger.error(e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

}
