package com.xxl.job.admin.core.util;

import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.TemplateHashModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ftl util
 *  前端页面   模板工具类
 *  当前作用是将   我们自己的实体类  变为  前端可以识别的实体类
 * @author xuxueli 2018-01-17 20:37:48
 */
public class FtlUtil {

//    定义日志对象
    private static Logger logger = LoggerFactory.getLogger(FtlUtil.class);


//    FreeMarker内部使用的变量的类型都实现了freemarker.template.TemplateModel接口。
//    我们需要把我们刚才的自定义类包装成TemplateModel的子类 TemplateHashModel
//    根据这个类  ，将我们的实体类，变成ftl 页面可以识别的实体类
    private static BeansWrapper wrapper = new BeansWrapperBuilder(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build();     //BeansWrapper.getDefaultInstance();




    /**
     *
     *  将一个类，转为前端  页面可以识别的  类
     */
    public static TemplateHashModel generateStaticModel(String packageName) {
        try {
            TemplateHashModel staticModels = wrapper.getStaticModels();
            TemplateHashModel fileStatics = (TemplateHashModel) staticModels.get(packageName);
            return fileStatics;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
