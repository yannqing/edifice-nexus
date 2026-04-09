package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;

import java.util.List;

/**
 * 项目阶段模版服务接口
 */
public interface ProjectStageTemplateService {

    /**
     * 根据阶段模版id查询阶段模版
     * @param stageId 阶段模版id
     * @return 阶段模版信息
     */
    ProjectStageTemplate getProjectStageTemplateById(Long stageId);

    /**
     * 查询所有启用的阶段模版
     * @return 阶段模版列表
     */
    List<ProjectStageTemplate> getAllEnabledProjectStageTemplates();

    /**
     * 根据项目类型id查询启用的阶段模版
     * @param projectTypeId 项目类型id
     * @return 阶段模版列表
     */
    List<ProjectStageTemplate> getEnabledByProjectTypeId(Long projectTypeId);

    /**
     * 分页查询阶段模版列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<ProjectStageTemplate> getProjectStageTemplatePage(Integer current, Integer pageSize);

    /**
     * 保存阶段模版
     * @param projectStageTemplate 阶段模版信息
     * @return 是否成功
     */
    boolean saveProjectStageTemplate(ProjectStageTemplate projectStageTemplate);

    /**
     * 更新阶段模版
     * @param projectStageTemplate 阶段模版信息
     * @return 是否成功
     */
    boolean updateProjectStageTemplate(ProjectStageTemplate projectStageTemplate);

    /**
     * 删除阶段模版
     * @param stageId 阶段模版id
     * @return 是否成功
     */
    boolean deleteProjectStageTemplate(Long stageId);
}
