package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.mapper.ProjectTypeMapper;
import com.qsy.edifice.service.ProjectTypeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目类型服务实现类
 */
@Slf4j
@Service
public class ProjectTypeServiceImpl implements ProjectTypeService {

    @Resource
    private ProjectTypeMapper projectTypeMapper;

    @Override
    public ProjectType getProjectTypeById(Long projectTypeId) {
        return projectTypeMapper.selectById(projectTypeId);
    }

    @Override
    public ProjectType getProjectTypeByCode(String projectTypeCode) {
        return projectTypeMapper.selectByProjectTypeCode(projectTypeCode);
    }

    @Override
    public List<ProjectType> getAllEnabledProjectTypes() {
        return projectTypeMapper.selectAllEnabled();
    }

    @Override
    public Page<ProjectType> getProjectTypePage(Integer current, Integer pageSize) {
        return projectTypeMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectType(ProjectType projectType) {
        return projectTypeMapper.insert(projectType) > 0;
    }

    @Override
    public boolean updateProjectType(ProjectType projectType) {
        return projectTypeMapper.updateById(projectType) > 0;
    }

    @Override
    public boolean deleteProjectType(Long projectTypeId) {
        return projectTypeMapper.deleteById(projectTypeId) > 0;
    }
}
