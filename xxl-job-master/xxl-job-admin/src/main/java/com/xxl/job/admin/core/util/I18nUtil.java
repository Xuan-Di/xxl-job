package com.xxl.job.admin.core.util;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * i18n util
 *  读取语言配置的  文件
 * @author xuxueli 2018-01-17 20:39:06
 */
public class I18nUtil {

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(I18nUtil.class);

//    定义 属性  类  ;  配置文件的操作类Properties类
    private static Properties prop = null;



    /**
     * 读取  properties 文件到项目里面
     */
    public static Properties loadI18nProp(){
        if (prop != null) {
            return prop;
        }
        try {
            // build i18n prop
//            获取中文  还是英文
//            XxlJobAdminConfig 这个类里面是从yml里面获取数据
//            到底是要读取中文 ，还是英文
//            XxlJobAdminConfig adminConfig = XxlJobAdminConfig.getAdminConfig();
            String i18n = XxlJobAdminConfig.getAdminConfig().getI18n();

//           根据配置文件里面的英文，还是中文      拿到 i18n 配置的名称
            String i18nFile = MessageFormat.format("i18n/message_{0}.properties", i18n);

            // load prop  加载进  properties 文件
            Resource resource = new ClassPathResource(i18nFile);
//            对加载  进来的文件 进行编码
            EncodedResource encodedResource = new EncodedResource(resource,"UTF-8");

//  解决  读取文件的  中文乱码  的问题
            prop = PropertiesLoaderUtils.loadProperties(encodedResource);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return prop;
    }



    /**
     * get val of i18n key
     * 根据key  从 properties文件里面  获取值
     * @param key
     * @return
     */
    public static String getString(String key) {
        return loadI18nProp().getProperty(key);
    }

    /**
     * get mult val of i18n mult key, as json
     * 获取il8n多键的多值，作为json
     * @param keys
     * @return
     */
    public static String getMultString(String... keys) {
        Map<String, String> map = new HashMap<String, String>();

        Properties prop = loadI18nProp();
        if (keys!=null && keys.length>0) {
            for (String key: keys) {
                map.put(key, prop.getProperty(key));
            }
        } else {
            for (String key: prop.stringPropertyNames()) {
                map.put(key, prop.getProperty(key));
            }
        }

        String json = JacksonUtil.writeValueAsString(map);
        return json;
    }

}
