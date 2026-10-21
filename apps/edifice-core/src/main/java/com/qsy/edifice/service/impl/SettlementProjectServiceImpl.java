package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectMember;
import com.qsy.edifice.domain.vo.ProjectListVo;
import com.qsy.edifice.domain.vo.SettlementProjectPageVo;
import com.qsy.edifice.domain.vo.SettlementProjectSummaryVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.service.ProjectMemberService;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.SettlementProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class SettlementProjectServiceImpl implements SettlementProjectService {
    @Resource private ProjectMapper projectMapper;
    @Resource private ProjectService projectService;
    @Resource private ProjectMemberService projectMemberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long parentId, CreateProjectDto dto, Long userId, boolean canManageAll) {
        if (parentId == null || userId == null || dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        // Serialize against parent deletion/archiving, without changing its financial records.
        Project parent = projectMapper.selectByIdForUpdate(parentId);
        requireRootProject(parent);
        if (!canManageAll && !isManager(parentId, userId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "仅主项目经理或项目管理员可以创建结算子项目");
        }
        if (Objects.equals(parent.getArchiveStatus(), 1)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "主项目已归档，不能新增结算子项目");
        }
        if (!StringUtils.hasText(dto.getProjectCode())) {
            dto.setProjectCode("SP" + IdWorker.getIdStr());
        } else if (projectService.getProjectByCode(dto.getProjectCode()) != null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "项目编号已存在，请使用独立的子项目编号");
        }
        Long childId = projectService.createProject(dto, userId);
        Project relation = Project.builder().projectId(childId).parentProjectId(parentId).build();
        if (projectMapper.updateById(relation) != 1) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "保存结算子项目关联失败");
        }
        return childId;
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementProjectPageVo list(Long parentId, GetAllProjectListDto dto, Long userId,
                                        boolean canViewAll, boolean canManageAll) {
        if (parentId == null || userId == null || dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        Project parent = projectMapper.selectById(parentId);
        requireRootProject(parent);
        if (!canViewAll && projectMemberService.getProjectMemberByProjectIdAndUserId(parentId, userId) == null) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权查看该主项目");
        }
        dto.setParentProjectId(parentId);
        dto.setProjectLevel(1);
        dto.setCurrent(dto.getCurrent() == null ? 1 : Math.max(1, dto.getCurrent()));
        dto.setPageSize(dto.getPageSize() == null ? 10 : Math.max(1, Math.min(100, dto.getPageSize())));
        Page<ProjectListVo> page;
        if (canViewAll) {
            page = projectService.getAllProjectPage(dto);
        } else {
            GetMyProjectListDto mine = new GetMyProjectListDto();
            mine.setParentProjectId(parentId);
            mine.setProjectLevel(1);
            mine.setKeywords(dto.getKeywords());
            mine.setProjectStatus(dto.getProjectStatus());
            mine.setArchiveStatus(dto.getArchiveStatus());
            mine.setCurrent(dto.getCurrent());
            mine.setPageSize(dto.getPageSize());
            page = projectService.getMyProjectPage(userId, mine);
        }
        SettlementProjectSummaryVo summary = projectMapper.selectSettlementSummary(parentId, userId, canViewAll);
        boolean canCreate = !Objects.equals(parent.getArchiveStatus(), 1)
                && (canManageAll || isManager(parentId, userId));
        return new SettlementProjectPageVo(page, summary, canCreate);
    }

    private void requireRootProject(Project parent) {
        if (parent == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL, "主项目不存在");
        }
        if (parent.getParentProjectId() != null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "结算子项目不能继续创建下级项目");
        }
    }

    private boolean isManager(Long projectId, Long userId) {
        ProjectMember member = projectMemberService.getProjectMemberByProjectIdAndUserId(projectId, userId);
        return member != null && Objects.equals(member.getProjectRole(), 101L);
    }
}
