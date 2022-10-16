package com.xxl.job.admin.core.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * local cache tool
 * 本地缓存工具
 * @author xuxueli 2018-01-22 21:37:34
 */
public class LocalCacheUtil {

//    定义  缓存  仓库 实体类
    private static ConcurrentMap<String, LocalCacheData> cacheRepository = new ConcurrentHashMap<String, LocalCacheData>();   // 类型建议用抽象父类，兼容性更好；


//    内部类，本地缓存数据类
    private static class LocalCacheData{
        private String key;
        private Object val;
        private long timeoutTime; // 超时时间

        public LocalCacheData() {
        }

        public LocalCacheData(String key, Object val, long timeoutTime) {
            this.key = key;
            this.val = val;
            this.timeoutTime = timeoutTime;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Object getVal() {
            return val;
        }

        public void setVal(Object val) {
            this.val = val;
        }

        public long getTimeoutTime() {
            return timeoutTime;
        }

        public void setTimeoutTime(long timeoutTime) {
            this.timeoutTime = timeoutTime;
        }
    }


    /**
     * set cache
     *  往 map里面  缓存数据
     * @param key
     * @param val
     * @param cacheTime
     * @return
     */
    public static boolean set(String key, Object val, long cacheTime){

        // clean timeout cache, before set new cache (avoid cache too much)
//        在设置新缓存之前清除超时缓存(避免缓存过多)
        cleanTimeoutCache();

        // set new cache
        if (key==null || key.trim().length()==0) {
            return false;
        }
        if (val == null) {
            remove(key);
        }
        if (cacheTime <= 0) {
            remove(key);
        }
//        获取到 当前时间  加上  缓存时间
        long timeoutTime = System.currentTimeMillis() + cacheTime;
//        设置数据
        LocalCacheData localCacheData = new LocalCacheData(key, val, timeoutTime);
        cacheRepository.put(localCacheData.getKey(), localCacheData);
        return true;
    }

    /**
     * remove cache
     * 从map仓库里面 删除数据
     * @param key
     * @return
     */
    public static boolean remove(String key){
        if (key==null || key.trim().length()==0) {
            return false;
        }
        cacheRepository.remove(key);
        return true;
    }

    /**
     * get cache
     * 根据key 从 map里面获取数据
     * @param key
     * @return
     */
    public static Object get(String key){
        if (key==null || key.trim().length()==0) {
            return null;
        }
        LocalCacheData localCacheData = cacheRepository.get(key);
        if (localCacheData!=null && System.currentTimeMillis()<localCacheData.getTimeoutTime()) {
            return localCacheData.getVal();
        } else {
            remove(key);
            return null;
        }
    }

    /**
     * clean timeout cache
     * 清除  超时 缓存
     * @return
     */
    public static boolean cleanTimeoutCache(){
//        如果  缓存仓库 有数据
        if (!cacheRepository.keySet().isEmpty()) {
//            遍历缓存 仓库
            for (String key: cacheRepository.keySet()) {
//                从  缓存 仓库  获取每一个  数据
                LocalCacheData localCacheData = cacheRepository.get(key);
//                如果 当前时间  大于 缓存时间 ，就从map 里面删除
                if (localCacheData!=null && System.currentTimeMillis()>=localCacheData.getTimeoutTime()) {
                    cacheRepository.remove(key);
                }
            }
        }
        return true;
    }

}
