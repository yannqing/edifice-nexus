package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateAcceptanceDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.AcceptanceVo;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ProjectAcceptanceMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.AcceptanceService;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.ProjectMemberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 验收服务实现（Phase 3 #4）
 */
@Slf4j
@Service
public class AcceptanceServiceImpl implements AcceptanceService {

    private static final Long ROLE_MANAGER_ID = 101L;

    private static final int STATUS_IN_PROGRESS = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;

    private static final int TYPE_PROCESS = 0;
    private static final int TYPE_DELIVERABLE = 1;
    private static final int TYPE_STAGE = 2;

    private static final Map<Integer, String> TYPE_LABELS = Map.of(
            TYPE_PROCESS, "过程验收",
            TYPE_DELIVERABLE, "成果验收",
            TYPE_STAGE, "阶段性验收"
    );

    @Resource
    private ProjectAcceptanceMapper acceptanceMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ApprovalFlowService approvalFlowService;

    @Resource
    private ProjectMemberService projectMemberService;

    // ==================== 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndSubmit(CreateAcceptanceDto dto, Long applyUserId) {
        if (dto.getProjectId() == null || dto.getAcceptanceType() == null
                || !StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目 / 类型 / 标题不能为空");
        }
        if (!TYPE_LABELS.containsKey(dto.getAcceptanceType())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知的验收类型");
        }
        if (dto.getAcceptanceType() == TYPE_STAGE && dto.getProjectStageId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "阶段性验收必须选择项目阶段");
        }

        Long firstApproverId = dto.getFirstApproverId();
        if (firstApproverId == null) {
            firstApproverId = resolveProjectManagerId(dto.getProjectId());
        }
        if (firstApproverId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL,
                    "未找到项目负责人，请显式指定一级审批人");
        }

        ProjectAcceptance entity = ProjectAcceptance.builder()
                .projectId(dto.getProjectId())
                .projectStageId(dto.getProjectStageId())
                .acceptanceType(dto.getAcceptanceType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .fileIds(dto.getFileIds())
                .applyUserId(applyUserId)
                .status(STATUS_IN_PROGRESS)
                .build();
        acceptanceMapper.insert(entity);

        SubmitApprovalDto submit = new SubmitApprovalDto(
                ApprovalBizType.ACCEPTANCE.getExt(),
                entity.getAcceptanceId(),
                firstApproverId,
                dto.getTitle()
        );
        ApprovalRecords record = approvalFlowService.submit(submit, applyUserId);
        entity.setCurrentRecordId(record.getApprovalRecordId());
        acceptanceMapper.updateById(entity);

        log.info("验收单提交 type={} acceptanceId={} firstApprover={} recordId={}",
                TYPE_LABELS.get(dto.getAcceptanceType()),
                entity.getAcceptanceId(), firstApproverId, record.getApprovalRecordId());
        return entity.getAcceptanceId();
    }

    // ==================== 审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(ApproveDto dto, Long operatorId) {
        if (dto.getRecordId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批记录id不能为空");
        }
        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(dto, operatorId);
        if (result.bizType != ApprovalBizType.ACCEPTANCE) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "此审批记录不属于验收单");
        }

        ProjectAcceptance entity = acceptanceMapper.selectById(result.bizId);
        if (entity == null) {
            throw new BusinessException(ErrorType.ACCEPTANCE_NOT_FOUND);
        }

        if (result.rejected) {
            entity.setStatus(STATUS_REJECTED);
            entity.setCurrentRecordId(null);
        } else if (result.isFinal) {
            entity.setStatus(STATUS_APPROVED);
            entity.setCurrentRecordId(null);
        } else {
            entity.setStatus(STATUS_IN_PROGRESS);
            entity.setCurrentRecordId(result.nextRecordId);
        }
        acceptanceMapper.updateById(entity);
    }

    // ==================== 查询 ====================

    @Override
    public List<AcceptanceVo> list(Long projectId, Integer acceptanceType, Integer status, String keyword) {
        LambdaQueryWrapper<ProjectAcceptance> w = new LambdaQueryWrapper<>();
        if (projectId != null) w.eq(ProjectAcceptance::getProjectId, projectId);
        if (acceptanceType != null) w.eq(ProjectAcceptance::getAcceptanceType, acceptanceType);
        if (status != null) w.eq(ProjectAcceptance::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            w.and(ww -> ww.like(ProjectAcceptance::getTitle, keyword)
                    .or().like(ProjectAcceptance::getContent, keyword));
        }
        w.orderByDesc(ProjectAcceptance::getCreatedTime);
        return toVos(acceptanceMapper.selectList(w), false);
    }

    @Override
    public AcceptanceVo getDetail(Long acceptanceId, Long userId, boolean canViewAll) {
        ProjectAcceptance entity = acceptanceMapper.selectById(acceptanceId);
        if (entity == null) throw new BusinessException(ErrorType.ACCEPTANCE_NOT_FOUND);
        if (!canViewAll && !canViewAcceptance(entity, userId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权查看该验收单");
        }
        return toVos(Collections.singletonList(entity), true).get(0);
    }

    @Override
    public List<AcceptanceVo> listMyPending(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<ApprovalRecordVo> myPending = approvalFlowService.listPendingByApprover(userId, ApprovalBizType.ACCEPTANCE);
        if (myPending.isEmpty()) return Collections.emptyList();
        Set<Long> ids = myPending.stream()
                .map(ApprovalRecordVo::getBizId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyList();
        return toVos(acceptanceMapper.selectBatchIds(ids), false);
    }

    // ==================== helpers ====================

    private Long resolveProjectManagerId(Long projectId) {
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(projectId);
        if (members == null || members.isEmpty()) return null;
        return members.stream()
                .filter(m -> ROLE_MANAGER_ID.equals(m.getProjectRole()))
                .map(ProjectMember::getUserId)
                .findFirst().orElse(null);
    }

    private boolean canViewAcceptance(ProjectAcceptance entity, Long userId) {
        if (entity == null || userId == null) {
            return false;
        }
        if (Objects.equals(entity.getApplyUserId(), userId)) {
            return true;
        }
        if (entity.getProjectId() != null
                && projectMemberService.getProjectMemberByProjectIdAndUserId(entity.getProjectId(), userId) != null) {
            return true;
        }
        return approvalFlowService.queryChain(ApprovalBizType.ACCEPTANCE, entity.getAcceptanceId()).stream()
                .anyMatch(record -> Objects.equals(record.getApprover(), userId));
    }

    private List<AcceptanceVo> toVos(List<ProjectAcceptance> list, boolean withChain) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> projectIds = list.stream().map(ProjectAcceptance::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> stageIds = list.stream().map(ProjectAcceptance::getProjectStageId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = list.stream().map(ProjectAcceptance::getApplyUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));
        Map<Long, ProjectStage> stageMap = stageIds.isEmpty() ? Collections.emptyMap()
                : projectStageMapper.selectBatchIds(stageIds).stream()
                .collect(Collectors.toMap(ProjectStage::getProjectStageId, s -> s, (a, b) -> a));
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        return list.stream().map(e -> {
            Project p = e.getProjectId() == null ? null : projectMap.get(e.getProjectId());
            ProjectStage s = e.getProjectStageId() == null ? null : stageMap.get(e.getProjectStageId());
            SysUser u = e.getApplyUserId() == null ? null : userMap.get(e.getApplyUserId());

            AcceptanceVo.AcceptanceVoBuilder b = AcceptanceVo.builder()
                    .acceptanceId(e.getAcceptanceId())
                    .projectId(e.getProjectId())
                    .projectName(p == null ? null : p.getProjectName())
                    .projectCode(p == null ? null : p.getProjectCode())
                    .projectStageId(e.getProjectStageId())
                    .stageName(s == null ? null : s.getStageName())
                    .acceptanceType(e.getAcceptanceType())
                    .acceptanceTypeLabel(TYPE_LABELS.getOrDefault(e.getAcceptanceType(), "未知"))
                    .title(e.getTitle())
                    .content(e.getContent())
                    .fileIds(e.getFileIds())
                    .applyUserId(e.getApplyUserId())
                    .applyUserName(u == null ? null : (u.getRealName() != null ? u.getRealName() : u.getUsername()))
                    .status(e.getStatus())
                    .currentRecordId(e.getCurrentRecordId())
                    .createdTime(e.getCreatedTime())
                    .updatedTime(e.getUpdatedTime());

            List<ApprovalRecordVo> chain = approvalFlowService.queryChain(
                    ApprovalBizType.ACCEPTANCE, e.getAcceptanceId());
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
}
