package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;
import com.qsy.edifice.mapper.ProjectStageTemplateMapper;
import com.qsy.edifice.service.ProjectStageTemplateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目阶段模版服务实现类
 */
@Slf4j
@Service
public class ProjectStageTemplateServiceImpl implements ProjectStageTemplateService {

    @Resource
    private ProjectStageTemplateMapper projectStageTemplateMapper;

    @Override
    public ProjectStageTemplate getProjectStageTemplateById(Long stageId) {
        return projectStageTemplateMapper.selectById(stageId);
    }

    @Override
    public List<ProjectStageTemplate> getAllEnabledProjectStageTemplates() {
        return projectStageTemplateMapper.selectAllEnabled();
    }

    @Override
    public List<ProjectStageTemplate> getEnabledByProjectTypeId(Long projectTypeId) {
        return projectStageTemplateMapper.selectEnabledByProjectTypeId(projectTypeId);
    }

    @Override
    public Page<ProjectStageTemplate> getProjectStageTemplatePage(Integer current, Integer pageSize) {
        return projectStageTemplateMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectStageTemplate(ProjectStageTemplate projectStageTemplate) {
        return projectStageTemplateMapper.insert(projectStageTemplate) > 0;
    }

    @Override
    public boolean updateProjectStageTemplate(ProjectStageTemplate projectStageTemplate) {
        return projectStageTemplateMapper.updateById(projectStageTemplate) > 0;
    }

    @Override
    public boolean deleteProjectStageTemplate(Long stageId) {
        return projectStageTemplateMapper.deleteById(stageId) > 0;
    }
}
