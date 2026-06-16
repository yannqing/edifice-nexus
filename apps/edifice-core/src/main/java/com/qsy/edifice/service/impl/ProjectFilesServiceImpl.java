package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateProjectFileDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.domain.vo.ProjectFileVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.ProjectFilesService;
import com.qsy.edifice.service.ProjectMemberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目文件服务实现类（Phase 3 扩展三级审批）
 */
@Slf4j
@Service
public class ProjectFilesServiceImpl implements ProjectFilesService {

    /** 项目经理角色 id（同 OutputValueServiceImpl 约定） */
    private static final Long ROLE_MANAGER_ID = 101L;

    private static final int APPROVAL_IN_PROGRESS = 1;
    private static final int APPROVAL_APPROVED = 2;
    private static final int APPROVAL_REJECTED = 3;
    private static final int RECORD_REJECTED = 2;

    @Resource
    private ProjectFilesMapper projectFilesMapper;

    @Resource
    private FilesMapper filesMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ApprovalFlowService approvalFlowService;

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Resource
    private ProjectMemberService projectMemberService;

    // ==================== 旧接口 ====================

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

    // ==================== 三级审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndSubmit(CreateProjectFileDto dto, Long uploadUserId) {
        if (dto.getProjectId() == null || dto.getFileId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目 / 文件不能为空");
        }
        Project project = projectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        if (Objects.equals(project.getArchiveStatus(), 1)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "项目已归档，不能上传项目文件");
        }
        if (filesMapper.selectById(dto.getFileId()) == null) {
            throw new BusinessException(ErrorType.FILE_NOT_FOUND);
        }

        Long firstApproverId = dto.getFirstApproverId();
        if (firstApproverId == null) {
            firstApproverId = resolveProjectManagerId(dto.getProjectId());
        }
        if (firstApproverId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL,
                    "未找到项目负责人，请显式指定一级审批人");
        }

        ProjectFiles entity = ProjectFiles.builder()
                .projectId(String.valueOf(dto.getProjectId()))
                .projectStageId(dto.getProjectStageId())
                .fileId(dto.getFileId())
                .fileName(dto.getFileName())
                .uploadUserId(uploadUserId)
                .fileCategory(dto.getFileCategory())
                .description(dto.getDescription())
                .approvalStatus(APPROVAL_IN_PROGRESS)
                .build();
        projectFilesMapper.insert(entity);

        // 提交审批链
        SubmitApprovalDto submit = new SubmitApprovalDto(
                ApprovalBizType.FILE.getExt(),
                entity.getProjectFileId(),
                firstApproverId,
                dto.getDescription()
        );
        ApprovalRecords record = approvalFlowService.submit(submit, uploadUserId);

        entity.setCurrentRecordId(record.getApprovalRecordId());
        projectFilesMapper.updateById(entity);

        log.info("项目文件已创建并提交审批 projectFileId={} firstApprover={} recordId={}",
                entity.getProjectFileId(), firstApproverId, record.getApprovalRecordId());
        return entity.getProjectFileId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(ApproveDto dto, Long operatorId) {
        if (dto.getRecordId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批记录id不能为空");
        }

        // 调通用审批流
        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(dto, operatorId);
        if (result.bizType != ApprovalBizType.FILE) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "此审批记录不属于项目文件");
        }

        ProjectFiles entity = projectFilesMapper.selectById(result.bizId);
        if (entity == null) {
            throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        }

        if (result.rejected) {
            entity.setApprovalStatus(APPROVAL_REJECTED);
            entity.setCurrentRecordId(null);
        } else if (result.isFinal) {
            entity.setApprovalStatus(APPROVAL_APPROVED);
            entity.setCurrentRecordId(null);
        } else {
            entity.setApprovalStatus(APPROVAL_IN_PROGRESS);
            entity.setCurrentRecordId(result.nextRecordId);
        }
        projectFilesMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long projectFileId, Long operatorId) {
        if (projectFileId == null || operatorId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目文件 / 操作人不能为空");
        }

        ProjectFiles entity = projectFilesMapper.selectById(projectFileId);
        if (entity == null) {
            throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        }
        if (!operatorId.equals(entity.getUploadUserId())) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只有上传人可以撤销该文件");
        }
        if (!Objects.equals(entity.getApprovalStatus(), APPROVAL_IN_PROGRESS)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "只有审批中的项目文件可以撤销");
        }

        ApprovalRecords pending = approvalFlowService.getCurrentPending(ApprovalBizType.FILE, projectFileId);
        if (pending != null) {
            pending.setInspectionFormStatus(RECORD_REJECTED);
            pending.setApprovalDescription("上传人撤销");
            pending.setNextApproverId(null);
            pending.setUpdatedTime(LocalDateTime.now());
            if (approvalRecordsMapper.updatePendingResult(pending) != 1) {
                throw new BusinessException(ErrorType.OPERATION_FAILED, "该文件审批已被处理，无法撤销");
            }
        }

        entity.setApprovalStatus(APPROVAL_REJECTED);
        entity.setCurrentRecordId(null);
        projectFilesMapper.updateById(entity);
        projectFilesMapper.deleteById(projectFileId);
    }

    @Override
    public List<ProjectFileVo> listProjectFiles(Long projectId, Integer approvalStatus, String keyword) {
        LambdaQueryWrapper<ProjectFiles> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            w.eq(ProjectFiles::getProjectId, String.valueOf(projectId));
        }
        if (approvalStatus != null) {
            w.eq(ProjectFiles::getApprovalStatus, approvalStatus);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(ww -> ww.like(ProjectFiles::getDescription, keyword)
                    .or().like(ProjectFiles::getFileCategory, keyword));
        }
        w.orderByDesc(ProjectFiles::getCreatedTime);
        List<ProjectFiles> list = projectFilesMapper.selectList(w);
        return toVos(list, false);
    }

    @Override
    public ProjectFileVo getDetail(Long projectFileId) {
        ProjectFiles entity = projectFilesMapper.selectById(projectFileId);
        if (entity == null) throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        return toVos(Collections.singletonList(entity), true).get(0);
    }

    @Override
    public List<ProjectFileVo> listMyPending(Long userId) {
        if (userId == null) return Collections.emptyList();
        // 拿到我的所有 pending 审批记录（biz_type=file）
        List<ApprovalRecordVo> myPending = approvalFlowService.listPendingByApprover(userId, ApprovalBizType.FILE);
        if (myPending.isEmpty()) return Collections.emptyList();
        Set<Long> fileIds = myPending.stream()
                .map(ApprovalRecordVo::getBizId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (fileIds.isEmpty()) return Collections.emptyList();
        List<ProjectFiles> list = projectFilesMapper.selectBatchIds(fileIds);
        return toVos(list, false);
    }

    // ==================== helpers ====================

    /** 从项目成员里查 ROLE_MANAGER_ID 的用户（没有则 null） */
    private Long resolveProjectManagerId(Long projectId) {
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(projectId);
        if (members == null || members.isEmpty()) return null;
        return members.stream()
                .filter(m -> ROLE_MANAGER_ID.equals(m.getProjectRole()))
                .map(ProjectMember::getUserId)
                .findFirst()
                .orElse(null);
    }

    private List<ProjectFileVo> toVos(List<ProjectFiles> list, boolean withChain) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> fileIds = list.stream().map(ProjectFiles::getFileId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> stageIds = list.stream().map(ProjectFiles::getProjectStageId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> projectIds = list.stream().map(ProjectFiles::getProjectId)
                .filter(Objects::nonNull)
                .map(this::parseProjectIdSafe)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Files> fileMap = fileIds.isEmpty() ? Collections.emptyMap()
                : filesMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(Files::getFileId, f -> f, (a, b) -> a));
        Map<Long, ProjectStage> stageMap = stageIds.isEmpty() ? Collections.emptyMap()
                : projectStageMapper.selectBatchIds(stageIds).stream()
                .collect(Collectors.toMap(ProjectStage::getProjectStageId, s -> s, (a, b) -> a));
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));

        return list.stream().map(e -> {
            Files f = e.getFileId() == null ? null : fileMap.get(e.getFileId());
            ProjectStage s = e.getProjectStageId() == null ? null : stageMap.get(e.getProjectStageId());
            Long projectIdLong = parseProjectIdSafe(e.getProjectId());
            Project p = projectIdLong == null ? null : projectMap.get(projectIdLong);

            ProjectFileVo.ProjectFileVoBuilder b = ProjectFileVo.builder()
                    .projectFileId(e.getProjectFileId())
                    .projectId(projectIdLong)
                    .projectName(p == null ? null : p.getProjectName())
                    .projectCode(p == null ? null : p.getProjectCode())
                    .projectStageId(e.getProjectStageId())
                    .stageName(s == null ? null : s.getStageName())
                    .fileId(e.getFileId())
                    .fileName(resolveDisplayName(e, f))
                    .fileUrl(f == null ? null : f.getFileUrl())
                    .fileExtension(f == null ? null : f.getFileExtension())
                    .fileSize(f == null || f.getFileSize() == null ? null : String.valueOf(f.getFileSize()))
                    .fileCategory(e.getFileCategory())
                    .description(e.getDescription())
                    .uploadUserId(e.getUploadUserId())
                    .approvalStatus(e.getApprovalStatus())
                    .currentRecordId(e.getCurrentRecordId())
                    .createdTime(e.getCreatedTime())
                    .updatedTime(e.getUpdatedTime());

            if (e.getUploadUserId() != null) {
                SysUser u = sysUserMapper.selectById(e.getUploadUserId());
                if (u != null) b.uploadUserName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }

            // 审批链 + 当前审批人
            List<ApprovalRecordVo> chain = approvalFlowService.queryChain(ApprovalBizType.FILE, e.getProjectFileId());
            if (e.getCurrentRecordId() != null) {
                chain.stream()
                        .filter(v -> e.getCurrentRecordId().equals(v.getApprovalRecordId()))
                        .findFirst()
                        .ifPresent(v -> {
                            b.currentApproverId(v.getApprover());
                            b.currentApproverName(v.getApproverName());
                        });
            }
            if (withChain) b.approvalChain(chain);

            return b.build();
        }).collect(Collectors.toList());
    }

    private Long parseProjectIdSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 展示名优先级：用户填写的 project_files.file_name →
     * 物理文件 files.display_name → 物理文件 files.file_name。
     */
    private String resolveDisplayName(ProjectFiles pf, Files f) {
        if (pf != null && pf.getFileName() != null && !pf.getFileName().isBlank()) {
            return pf.getFileName();
        }
        if (f == null) return null;
        if (f.getDisplayName() != null) return f.getDisplayName();
        return f.getFileName();
    }
}
