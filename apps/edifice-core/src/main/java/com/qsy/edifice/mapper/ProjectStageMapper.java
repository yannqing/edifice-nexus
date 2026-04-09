package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.ProjectStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目阶段 Mapper 接口
 */
@Mapper
public interface ProjectStageMapper extends BaseMapper<ProjectStage> {

    /**
     * 根据项目id查询项目阶段列表
     * @param projectId 项目id
     * @return 项目阶段列表
     */
    List<ProjectStage> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据项目id和阶段状态查询项目阶段列表
     * @param projectId 项目id
     * @param stageStatus 阶段状态
     * @return 项目阶段列表
     */
    List<ProjectStage> selectByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("stageStatus") Integer stageStatus);
}
