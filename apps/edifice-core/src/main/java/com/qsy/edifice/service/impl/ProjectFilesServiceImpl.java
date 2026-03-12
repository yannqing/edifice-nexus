package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectFiles;
import com.qsy.edifice.mapper.ProjectFilesMapper;
import com.qsy.edifice.service.ProjectFilesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目文件服务实现类
 */
@Slf4j
@Service
public class ProjectFilesServiceImpl implements ProjectFilesService {

    @Resource
    private ProjectFilesMapper projectFilesMapper;

    @Override
    public ProjectFiles getProjectFilesById(Long projectFileId) {
        return projectFilesMapper.selectById(projectFileId);
    }

    @Override
    public List<ProjectFiles> getProjectFilesByProjectId(String projectId) {
        return projectFilesMapper.selectByProjectId(projectId);
    }

    @Override
    public List<ProjectFiles> getProjectFilesByProjectStageId(Long projectStageId) {
        return projectFilesMapper.selectByProjectStageId(projectStageId);
    }

    @Override
    public Page<ProjectFiles> getProjectFilesPage(Integer current, Integer pageSize) {
        return projectFilesMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectFiles(ProjectFiles projectFiles) {
        return projectFilesMapper.insert(projectFiles) > 0;
    }

    @Override
    public boolean updateProjectFiles(ProjectFiles projectFiles) {
        return projectFilesMapper.updateById(projectFiles) > 0;
    }

    @Override
    public boolean deleteProjectFiles(Long projectFileId) {
        return projectFilesMapper.deleteById(projectFileId) > 0;
    }
}
