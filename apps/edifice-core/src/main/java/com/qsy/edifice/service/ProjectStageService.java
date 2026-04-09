package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectStage;

import java.util.List;

/**
 * 项目阶段服务接口
 */
public interface ProjectStageService {

    /**
     * 根据项目阶段id查询项目阶段
     * @param projectStageId 项目阶段id
     * @return 项目阶段信息
     */
    ProjectStage getProjectStageById(Long projectStageId);

    /**
     * 根据项目id查询项目阶段列表
     * @param projectId 项目id
     * @return 项目阶段列表
     */
    List<ProjectStage> getProjectStagesByProjectId(Long projectId);

    /**
     * 根据项目id和阶段状态查询项目阶段列表
     * @param projectId 项目id
     * @param stageStatus 阶段状态
     * @return 项目阶段列表
     */
    List<ProjectStage> getProjectStagesByProjectIdAndStatus(Long projectId, Integer stageStatus);

    /**
     * 分页查询项目阶段列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<ProjectStage> getProjectStagePage(Integer current, Integer pageSize);

    /**
     * 保存项目阶段
     * @param projectStage 项目阶段信息
     * @return 是否成功
     */
    boolean saveProjectStage(ProjectStage projectStage);

    /**
     * 更新项目阶段
     * @param projectStage 项目阶段信息
     * @return 是否成功
     */
    boolean updateProjectStage(ProjectStage projectStage);

    /**
     * 删除项目阶段
     * @param projectStageId 项目阶段id
     * @return 是否成功
     */
    boolean deleteProjectStage(Long projectStageId);
}
