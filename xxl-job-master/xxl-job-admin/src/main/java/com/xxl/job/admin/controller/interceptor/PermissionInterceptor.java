package com.xxl.job.admin.controller.interceptor;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.service.LoginService;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 权限拦截
 *
 * @author xuxueli 2015-12-12 18:09:04
 */
@Component
public class PermissionInterceptor implements AsyncHandlerInterceptor {

	@Resource
	private LoginService loginService;


//	handler  是将请求的方法进行封装之后的  对象
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		
		if (!(handler instanceof HandlerMethod)) {
			return true;	// proceed with the next interceptor  继续下一个拦截器
		}

		// if need login
		boolean needLogin = true;   // 是否需要登录，默认需要
		boolean needAdminuser = false;// 是否需要管理员权限，  默认不需要
		HandlerMethod method = (HandlerMethod)handler;  // 适配器

//		从当前 的  方法上面  获取   权限限制  的注解
		PermissionLimit permission = method.getMethodAnnotation(PermissionLimit.class);
		if (permission!=null) {
//			如果注解不为空
			needLogin = permission.limit();// 登录拦截 (默认拦截)
			needAdminuser = permission.adminuser(); // 要求管理员权限 (默认否)
		}

		if (needLogin) {
//			登录拦截

//			根据cookie  判断是否登录，并且返回登陆者的信息
			XxlJobUser loginUser = loginService.ifLogin(request, response);
			if (loginUser == null) {
				response.setStatus(302);
				response.setHeader("location", request.getContextPath()+"/toLogin");
				return false;
			}
			if (needAdminuser && loginUser.getRole()!=1) {
				throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
			}

//			XXL_JOB_LOGIN_IDENTITY  登录身份 key    用户信息
			request.setAttribute(LoginService.LOGIN_IDENTITY_KEY, loginUser);
		}

		return true;	// proceed with the next interceptor  进入写一个拦截器
	}
	
}
