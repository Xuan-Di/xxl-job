package com.xxl.job.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * date util
 *  日期工具类
 * @author xuxueli 2018-08-19 01:24:11
 */
public class DateUtil {

    // ---------------------- format parse ----------------------

//    创建日志对象
    private static Logger logger = LoggerFactory.getLogger(DateUtil.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";


//    创建本地线程
    private static final ThreadLocal<Map<String, DateFormat>> dateFormatThreadLocal = new ThreadLocal<Map<String, DateFormat>>();


    /**
     *
     *  根据转换格式  ，获取 new SimpleDateFormat(pattern)对象
     * @author jing
     */
    private static DateFormat getDateFormat(String pattern) {
        if (pattern==null || pattern.trim().length()==0) {
            throw new IllegalArgumentException("pattern cannot be empty.");
        }
//  使用本地线程，创建一个map对象
        Map<String, DateFormat> dateFormatMap = dateFormatThreadLocal.get();
//        判断这个本地线程 的map 里面是否有当前的 key
        if(dateFormatMap!=null && dateFormatMap.containsKey(pattern)){
            return dateFormatMap.get(pattern);
        }

//      如果 本地线程 里面没有  这个key
//        dateFormatThreadLocal 锁
        synchronized (dateFormatThreadLocal) {
//            如果 本地线程获取的map为空，就是还没有创建
            if (dateFormatMap == null) {
//                在  本地  线程里面  创建这个map对象
                dateFormatMap = new HashMap<String, DateFormat>();
            }

//           将这个解析  放进去
            dateFormatMap.put(pattern, new SimpleDateFormat(pattern));

//            将map 放进去
            dateFormatThreadLocal.set(dateFormatMap);
        }

//        获取到
        return dateFormatMap.get(pattern);
    }

    /**
     * format datetime. like "yyyy-MM-dd"
     *  将  date  类型日期  转为 yyyy-MM-dd 类型的String字符串
     * @param date
     * @return
     * @throws ParseException
     */
    public static String formatDate(Date date) {
        return format(date, DATE_FORMAT);
    }

    /**
     * format date. like "yyyy-MM-dd HH:mm:ss"
     *将  date  类型日期  转为 yyyy-MM-dd HH:mm:ss 类型的String字符串
     * @param date
     * @return
     * @throws ParseException
     */
    public static String formatDateTime(Date date) {
        return format(date, DATETIME_FORMAT);
    }

    /**
     * format date
     * 将  date  类型日期  转为 patten 类型的String字符串
     * @param date
     * @param patten
     * @return
     * @throws ParseException
     */
    public static String format(Date date, String patten) {
        return getDateFormat(patten).format(date);
    }

    /**
     * parse date string, like "yyyy-MM-dd HH:mm:s"
     * 将字符串 类型的日期   转为  Date 类型
     * @param dateString
     * @return
     * @throws ParseException
     */
    public static Date parseDate(String dateString){
        return parse(dateString, DATE_FORMAT);
    }

    /**
     * parse datetime string, like "yyyy-MM-dd HH:mm:ss"
     *将字符串 类型的日期   转为  Date 类型
     * @param dateString
     * @return
     * @throws ParseException
     */
    public static Date parseDateTime(String dateString) {
        return parse(dateString, DATETIME_FORMAT);
    }

    /**
     * parse date
     * 将字符串 类型的日期   转为  Date 类型，这个格式类型是pattern
     * @param dateString
     * @param pattern
     * @return
     * @throws ParseException
     */
    public static Date parse(String dateString, String pattern) {
        try {
            Date date = getDateFormat(pattern).parse(dateString);
            return date;
        } catch (Exception e) {
            logger.warn("parse date error, dateString = {}, pattern={}; errorMsg = {}", dateString, pattern, e.getMessage());
            return null;
        }
    }






    // ---------------------- add date   新增 减少 日期  ----------------------

    /**
     *
     * date 类型日期 加年份
     *
     */
    public static Date addYears(final Date date, final int amount) {
        return add(date, Calendar.YEAR, amount);
    }
    /**
     *
     * date 类型日期 加月份
     *
     */
    public static Date addMonths(final Date date, final int amount) {
        return add(date, Calendar.MONTH, amount);
    }
    /**
     *
     * date 类型日期 加天数
     *
     */
    public static Date addDays(final Date date, final int amount) {
        return add(date, Calendar.DAY_OF_MONTH, amount);
    }


    /**
     *
     * date 类型日期 加 小时
     *
     */
    public static Date addHours(final Date date, final int amount) {
        return add(date, Calendar.HOUR_OF_DAY, amount);
    }

    /**
     *
     * date 类型日期 加分钟
     *
     */
    public static Date addMinutes(final Date date, final int amount) {
        return add(date, Calendar.MINUTE, amount);
    }



    /**
     *
     * date 类型日期 自定义 加年月日
     *
     */
    private static Date add(final Date date, final int calendarField, final int amount) {
        if (date == null) {
            return null;
        }
        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(calendarField, amount);
        return c.getTime();
    }

}