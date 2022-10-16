package com.xxl.job.core.enums;

/**
 * Created by jing
 *  任务超时时间
 */
public class RegistryConfig {

    public static final int BEAT_TIMEOUT = 30;
    public static final int DEAD_TIMEOUT = BEAT_TIMEOUT * 3;

//    注册类型，自动注册，手动注册
    public enum RegistType{ EXECUTOR, ADMIN }

}
