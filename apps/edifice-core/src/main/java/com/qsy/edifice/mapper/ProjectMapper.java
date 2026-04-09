package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.vo.ProjectStatisticsVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目 Mapper 接口
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 根据项目编码查询项目
     * @param projectCode 项目编码
     * @return 项目信息
     */
    Project selectByProjectCode(@Param("projectCode") String projectCode);

    /**
     * 根据项目状态查询项目列表
     * @param projectStatus 项目状态
     * @return 项目列表
     */
    List<Project> selectByProjectStatus(@Param("projectStatus") Integer projectStatus);

    /**
     * 根据项目类型查询项目列表
     * @param projectType 项目类型
     * @return 项目列表
     */
    List<Project> selectByProjectType(@Param("projectType") Long projectType);

    /**
     * 根据用户id查询参与的项目id列表
     * @param userId 用户id
     * @return 项目id列表
     */
    List<Long> selectProjectIdsByUserId(@Param("userId") Long userId);

    /**
     * 获取项目统计信息
     * @return 统计信息
     */
    ProjectStatisticsVo selectProjectStatistics();
}
