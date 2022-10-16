package com.xxl.job.admin.controller.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * web mvc config
 * 全局配置，springMVC的  配置
 * @author xuxueli 2018-04-02 20:48:20
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

//    权限拦截器
    @Resource
    private PermissionInterceptor permissionInterceptor;
//    cookie 拦截器
    @Resource
    private CookieInterceptor cookieInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //  对全部的  接口 进行  权限拦截
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/**");
        //  对全部的  接口 进行  cookie 拦截
        registry.addInterceptor(cookieInterceptor).addPathPatterns("/**");
    }

}