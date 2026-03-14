package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.dto.ProjectCreateDto;
import com.qsy.edifice.domain.dto.ProjectExportDTO;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.enums.ProjectStatusEnum;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ContractMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.ProjectMemberMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.utils.ProjectUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService  {
    private final ContractMapper contractMapper;
    private final ProjectStageMapper stageMapper;
    private final ProjectMemberMapper memberMapper;

    // 默认角色ID（建议配置到 application.yml 或常量类）
    private static final Long PROJECT_MANAGER_ROLE_ID = 101L;
    private static final Long MEMBER_ROLE_ID = 102L;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public Project getProjectById(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    @Override
    public Project getProjectByCode(String projectCode) {
        return projectMapper.selectByProjectCode(projectCode);
    }

    @Override
    public List<Project> getProjectsByStatus(Integer projectStatus) {
        return projectMapper.selectByProjectStatus(projectStatus);
    }

    @Override
    public List<Project> getProjectsByType(Long projectType) {
        return projectMapper.selectByProjectType(projectType);
    }

    @Override
    public Page<Project> getProjectPage(Integer current, Integer pageSize) {
        return projectMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProject(Project project) {
        return projectMapper.insert(project) > 0;
    }

    @Override
    public boolean updateProject(Project project) {
        return projectMapper.updateById(project) > 0;
    }

    @Override
    public boolean deleteProject(Long projectId) {
        return projectMapper.deleteById(projectId) > 0;
    }
    @Override
    public List<ProjectExportDTO> getProjectsForExport(List<Long> projectIds) {
        if(projectIds == null || projectIds.isEmpty()){
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);

        }
        List<Project> projects =this.list(new LambdaQueryWrapper<Project>()
                .in(Project::getProjectId,projectIds)
                .eq(Project::getIsDelete,0)
                .orderByAsc(Project::getCreatedTime));
        return projects.stream().map(project -> {
            ProjectExportDTO dto = new ProjectExportDTO();
            dto.setProjectId(project.getProjectId());
            dto.setProjectName(project.getProjectName());
            dto.setProjectCode(project.getProjectCode());
            dto.setProjectType(project.getProjectType());
            dto.setProjectStatusText(ProjectStatusEnum.getTextByCode(project.getProjectStatus()));
            dto.setIsShowText(ProjectUtils.getIsShowText(project.getIsShow()));
            dto.setProjectStartTime(project.getProjectStartTime());
            dto.setProjectEndTime(project.getProjectEndTime());
            dto.setCreatedTime(project.getCreatedTime());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUser(ProjectCreateDto sysUserCreateDto, Long currentUserId) {
        return false;
    }
}
