package com.xxl.job.core.enums;

/**
 * Created by jing
 * 阻塞处理策略，  有单行串行，丢弃后续调度，覆盖之前调度
 */
public enum ExecutorBlockStrategyEnum {

//    串行
    SERIAL_EXECUTION("Serial execution"),
    /*CONCURRENT_EXECUTION("并行"),*/
//    丢弃
    DISCARD_LATER("Discard Later"),
//    覆盖
    COVER_EARLY("Cover Early");

    private String title;  // 中文，这个是从语言配置  里面 获取的
    private ExecutorBlockStrategyEnum (String title) {
        this.title = title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    public String getTitle() {
        return title;
    }



    public static ExecutorBlockStrategyEnum match(String name, ExecutorBlockStrategyEnum defaultItem) {
        if (name != null) {
            for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
                if (item.name().equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }
}
