package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.*;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目服务实现类
 */
@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ContractService contractService;

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
    public Page<ProjectListVo> getMyProjectPage(Long userId, GetMyProjectListDto dto) {
        //1. 参数校验
        if(userId == null||dto ==null){
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // 处理分页参数默认值
        if (dto.getCurrent() == null || dto.getCurrent() < 1) {
            dto.setCurrent(1);
        }
        if (dto.getPageSize() == null || dto.getPageSize() < 1) {
            dto.setPageSize(10);
        }

        // 2. 先查询用户参与的所有项目id
        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);

        if (projectIds == null || projectIds.isEmpty()) {
            return new Page<>(dto.getCurrent(), dto.getPageSize());
        }

        // 3. 构建查询条件
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getProjectId, projectIds);

        // 关键字搜索
        if (StringUtils.hasText(dto.getKeywords())) {
            wrapper.and(w -> w
                    .like(Project::getProjectName, dto.getKeywords())
                    .or()
                    .like(Project::getProjectCode, dto.getKeywords())
            );
        }

        // 项目状态筛选
        wrapper.eq(dto.getProjectStatus() != null,Project::getProjectStatus, dto.getProjectStatus());


        // 4. 分页查询
        Page<Project> page = projectMapper.selectPage(new Page<>(dto.getCurrent(), dto.getPageSize()), wrapper);

        // 5. 转换为VO
        Page<ProjectListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ProjectListVo> voList = new ArrayList<>();

        for (Project project : page.getRecords()) {
            ProjectListVo vo = ProjectListVo.objToVo(project);
            voList.add(vo);
        }

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ProjectDetailVo getProjectDetailById(Long projectId, Long userId) {
        //1.参数校验
        if (userId == null||projectId == null) {
           throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // 2. 查询项目基本信息
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        // 3. 验证用户是否参与此项目
        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);
        if (projectIds == null || !projectIds.contains(projectId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR);
        }
        return convertToDetailVo(project);
    }

    @Override
    public ProjectStatisticsVo getProjectStatistics() {
        return projectMapper.selectProjectStatistics();
    }

    @Override
    public boolean checkProjectExists(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null;
    }

    /**
     * 转换为列表VO
     */
    private ProjectListVo convertToListVo(Project project) {
        ProjectListVo vo = new ProjectListVo();
        BeanUtils.copyProperties(project,vo);

        // 项目类型
        if (project.getProjectType() != null) {
            ProjectType projectType = projectTypeService.getProjectTypeById(project.getProjectType());
            if (projectType != null) {
                vo.setProjectType(convertToProjectTypeVo(projectType));
            }
        }

        // 项目阶段（取最新的阶段）
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(project.getProjectId());
        if (stages != null && !stages.isEmpty()) {
            // 取最新的一个阶段
            ProjectStage latestStage = stages.get(stages.size() - 1);
            vo.setProjectStage(convertToProjectStageVo(latestStage));
        }
        // 合同金额
        Contract contracts = contractService.getContractById(project.getProjectId());
        if(contracts != null ){
            vo.setContractAmount(convertToContractVo(contracts));
        }


        // 项目成员
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(project.getProjectId());
        if (members != null && !members.isEmpty()) {
            vo.setProjectMemberList(convertToMemberVoList(members));
        }

        return vo;
    }

    /**
     * 转换为详情VO
     */
    private ProjectDetailVo convertToDetailVo(Project project) {
        ProjectDetailVo vo = new ProjectDetailVo();
        BeanUtils.copyProperties(project,vo);

        // 项目类型
        if (project.getProjectType() != null) {
            ProjectType projectType = projectTypeService.getProjectTypeById(project.getProjectType());
            if (projectType != null) {
                vo.setProjectType(convertToProjectTypeVo(projectType));
            }
        }

        // 项目阶段
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(project.getProjectId());
        if (stages != null && !stages.isEmpty()) {
            ProjectStage latestStage = stages.get(stages.size() - 1);
            vo.setProjectStage(convertToProjectStageVo(latestStage));
        }

        // 项目成员
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(project.getProjectId());
        if (members != null && !members.isEmpty()) {
            vo.setProjectMemberList(convertToMemberVoList(members));
        }

        return vo;
    }

    /**
     * 转换项目成员列表VO
     */
    private List<ProjectMemberVo> convertToMemberVoList(List<ProjectMember> members) {
        return members.stream().map(member -> {
            ProjectMemberVo vo = new ProjectMemberVo();
            vo.setUserId(member.getUserId());
            vo.setProjectRoleId(member.getProjectRole());
            // 项目角色名称需要另外查询，这里先留空
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     *转换为ProjectTypeVo
     */
    private ProjectTypeVo convertToProjectTypeVo(ProjectType projectType) {
        if (projectType == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ProjectTypeVo vo = new ProjectTypeVo();
        BeanUtils.copyProperties(projectType,vo);

        return vo;
    }

    /**
     * 转换为ProjectStageVo
     */
    private ProjectStageVo convertToProjectStageVo(ProjectStage projectStage) {
        if (projectStage == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ProjectStageVo vo = new ProjectStageVo();
        BeanUtils.copyProperties(projectStage,vo);

        return vo;
    }
    private ContractVo convertToContractVo(Contract contract) {
        if (contract == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        ContractVo vo = new ContractVo();
        BeanUtils.copyProperties(contract,vo);

        return vo;
    }
}

