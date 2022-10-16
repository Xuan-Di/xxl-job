package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对执行器的操作 ，也就是对项目的操作
 * Created by xuxueli on 16/9/30.
 */
@Mapper
public interface XxlJobGroupDao {
    /**
     *  查询全部的  执行器表
     *
     */
    public List<XxlJobGroup> findAll();
    /**
     * 根据地址注册类型，查询执行器（项目）
     *
     */
    public List<XxlJobGroup> findByAddressType(@Param("addressType") int addressType);
    /**
     * 新增执行器（项目）
     *
     */
    public int save(XxlJobGroup xxlJobGroup);
    /**
     * 更新执行器（项目）
     *
     */
    public int update(XxlJobGroup xxlJobGroup);
    /**
     * 根据id  删除执行器（项目）
     *
     */
    public int remove(@Param("id") int id);


    /**
     *  根据id  查询执行器表 xxl_job_group
     */
    public XxlJobGroup load(@Param("id") int id);
    /**
     *  分页  查询执行器表 xxl_job_group
     */
    public List<XxlJobGroup> pageList(@Param("offset") int offset,
                                      @Param("pagesize") int pagesize,
                                      @Param("appname") String appname,
                                      @Param("title") String title);
    /**
     *  分页  查询执行器表 xxl_job_group  个数
     */
    public int pageListCount(@Param("offset") int offset,
                             @Param("pagesize") int pagesize,
                             @Param("appname") String appname,
                             @Param("title") String title);

}
