package com.xxl.job.admin.service;

import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.CookieUtil;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.core.util.JacksonUtil;
import com.xxl.job.admin.dao.XxlJobUserDao;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;

/**
 * 配置类，关于登录相关的
 * @author jing  identity
 */
@Configuration
public class LoginService {

//    登录身份 key
    public static final String LOGIN_IDENTITY_KEY = "XXL_JOB_LOGIN_IDENTITY";

//    按名称注入
    @Resource
    private XxlJobUserDao xxlJobUserDao;


    /**
     * 获取token
     * 将 用户的信息 转为  16进制的 加密信息
     * @param xxlJobUser  用户的信息（账号，密码等）
     */
    private String makeToken(XxlJobUser xxlJobUser){
//        将用户信息实体类 转为   字符串 json

//     tokenJson  =    {"id":0,"username":"qqq","password":"1111","role":0,"permission":null}
        String tokenJson = JacksonUtil.writeValueAsString(xxlJobUser);
//  getBytes()  都是将一个string类型的字符串转换成byte类型并且存入一个byte数组中
        //      getBytes()  都是将一个string类型的字符串转换成byte类型并且存入一个byte数组中
//        将用户信息转为  16  进制
        String tokenHex = new BigInteger(tokenJson.getBytes()).toString(16);
        return tokenHex;
    }

    /**
     *  将16 进制的加密信息  解析为实体类
     * @param tokenHex
     */
    private XxlJobUser parseToken(String tokenHex){
        XxlJobUser xxlJobUser = null;
        if (tokenHex != null) {
            String tokenJson = new String(new BigInteger(tokenHex, 16).toByteArray());      // username_password(md5)
            xxlJobUser = JacksonUtil.readValue(tokenJson, XxlJobUser.class);
        }
        return xxlJobUser;
    }


    /**
     *  登录接口 ：
     *  用户名  密码，记住我
     *
     *  登录成功之后，将用户信息 保存到 cookie里面
     *
     */
    public ReturnT<String> login(HttpServletRequest request, HttpServletResponse response, String username, String password, boolean ifRemember){

        // param
        if (username==null || username.trim().length()==0 || password==null || password.trim().length()==0){
            return new ReturnT<String>(500, I18nUtil.getString("login_param_empty"));
        }

        // valid passowrd  根据用户名 查询数据库信息
        XxlJobUser xxlJobUser = xxlJobUserDao.loadByUserName(username);
        if (xxlJobUser == null) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }

//  对前端传过来的  密码进行加密
        String passwordMd5 = DigestUtils.md5DigestAsHex(password.getBytes());
//        判断数据库密码和前端的一样不一样
        if (!passwordMd5.equals(xxlJobUser.getPassword())) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }

//        根据用户信息 实体类  获取token信息。就是变成16进制信息
        String loginToken = makeToken(xxlJobUser);

        // do login  往cookie里面设置东西
        CookieUtil.set(response, LOGIN_IDENTITY_KEY, loginToken, ifRemember);
        return ReturnT.SUCCESS;
    }



    /**
     * logout
     *  退出登录  就是删除cookie
     * @param request
     * @param response
     */
    public ReturnT<String> logout(HttpServletRequest request, HttpServletResponse response){
        CookieUtil.remove(request, response, LOGIN_IDENTITY_KEY);
        return ReturnT.SUCCESS;
    }

    /**
     *  判断是否登录
     * @param request
     * @return
     */
    public XxlJobUser ifLogin(HttpServletRequest request, HttpServletResponse response){
//        获取cookie
        String cookieToken = CookieUtil.getValue(request, LOGIN_IDENTITY_KEY);
        if (cookieToken != null) {
            XxlJobUser cookieUser = null;
            try {
//                将用户信息  转为 实体类
                cookieUser = parseToken(cookieToken);
            } catch (Exception e) {
                logout(request, response);
            }
            if (cookieUser != null) {
//                从 数据库查询 cookie里面的用户信息
                XxlJobUser dbUser = xxlJobUserDao.loadByUserName(cookieUser.getUsername());
                if (dbUser != null) {
//                    cookie里面的用户与查询出来的一样
                    if (cookieUser.getPassword().equals(dbUser.getPassword())) {
                        return dbUser;
                    }
                }
            }
        }
        return null;
    }


}
