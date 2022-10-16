package com.xxl.job.admin.controller.interceptor;

import com.xxl.job.admin.core.util.FtlUtil;
import com.xxl.job.admin.core.util.I18nUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;

/**
 * push cookies to model as cookieMap
 *  cookie拦截器，将前端传过来的cookie 放到 cookieMap 里面
 *   将I18n 配置里面的信息  保存到项目里面
 *  类继承  AsyncHandlerInterceptor  自定义拦截器
 * @author xuxueli 2015-12-12 18:09:04
 */
@Component
public class CookieInterceptor implements AsyncHandlerInterceptor {

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

		// cookie  如果前端传过来  cookie
		if (modelAndView!=null && request.getCookies()!=null && request.getCookies().length>0) {
			HashMap<String, Cookie> cookieMap = new HashMap<String, Cookie>();

//			遍历cookie
			for (Cookie ck : request.getCookies()) {
				cookieMap.put(ck.getName(), ck);
			}
//			将cookiemap放到 modelAndView 里面
			modelAndView.addObject("cookieMap", cookieMap);
		}

		// static method  将I18n 文件  读取为 页面以识别的类
		if (modelAndView != null) {
			modelAndView.addObject("I18nUtil", FtlUtil.generateStaticModel(I18nUtil.class.getName()));
		}

	}
	
}
