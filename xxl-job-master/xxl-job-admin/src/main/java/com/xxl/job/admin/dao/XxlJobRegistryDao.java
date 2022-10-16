package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * Created by xuxueli on 16/9/30.
 */
@Mapper
public interface XxlJobRegistryDao {

    /**
     * xxl_job_registry
     * 查询  超时的数据
     */
    public List<Integer> findDead(@Param("timeout") int timeout,
                                  @Param("nowTime") Date nowTime);
    /**
     * xxl_job_registry
     * 根据id ，删除超时的数据
     */
    public int removeDead(@Param("ids") List<Integer> ids);
    /**
     * xxl_job_registry
     * 查询   没有超时的数据
     */
    public List<XxlJobRegistry> findAll(@Param("timeout") int timeout,
                                        @Param("nowTime") Date nowTime);
    /**
     * xxl_job_registry
     *  更新时间，也就是维持心跳
     */
    public int registryUpdate(@Param("registryGroup") String registryGroup,
                              @Param("registryKey") String registryKey,
                              @Param("registryValue") String registryValue,
                              @Param("updateTime") Date updateTime);
    /**
     *  我们项目  调用 web 实现注册功能
     */
    public int registrySave(@Param("registryGroup") String registryGroup,
                            @Param("registryKey") String registryKey,
                            @Param("registryValue") String registryValue,
                            @Param("updateTime") Date updateTime);
    /**
     *  我们项目  调用 web 实现移除功能
     */
    public int registryDelete(@Param("registryGroup") String registryGroup,
                          @Param("registryKey") String registryKey,
                          @Param("registryValue") String registryValue);

}
