package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.service.ProjectStageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目阶段服务实现类
 */
@Slf4j
@Service
public class ProjectStageServiceImpl extends ServiceImpl<ProjectStageMapper, ProjectStage> implements ProjectStageService {

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Override
    public ProjectStage getProjectStageById(Long projectStageId) {
        return projectStageMapper.selectById(projectStageId);
    }

    @Override
    public List<ProjectStage> getProjectStagesByProjectId(Long projectId) {
        return projectStageMapper.selectByProjectId(projectId);
    }

    @Override
    public List<ProjectStage> getProjectStagesByProjectIdAndStatus(Long projectId, Integer stageStatus) {
        return projectStageMapper.selectByProjectIdAndStatus(projectId, stageStatus);
    }

    @Override
    public Page<ProjectStage> getProjectStagePage(Integer current, Integer pageSize) {
        return projectStageMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectStage(ProjectStage projectStage) {
        return projectStageMapper.insert(projectStage) > 0;
    }

    @Override
    public boolean updateProjectStage(ProjectStage projectStage) {
        return projectStageMapper.updateById(projectStage) > 0;
    }

    @Override
    public boolean deleteProjectStage(Long projectStageId) {
        return projectStageMapper.deleteById(projectStageId) > 0;
    }
}
