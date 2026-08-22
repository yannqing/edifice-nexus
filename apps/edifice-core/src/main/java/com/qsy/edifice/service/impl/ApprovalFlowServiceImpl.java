package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.SysUserRole;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ApprovalFlowConfigVo;
import com.qsy.edifice.domain.vo.ApprovalFlowNodeVo;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.mapper.SysUserRoleMapper;
import com.qsy.edifice.service.ApprovalFlowConfigService;
import com.qsy.edifice.service.ApprovalFlowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通用审批流服务实现
 */
@Slf4j
@Service
public class ApprovalFlowServiceImpl implements ApprovalFlowService {

    /** 0-待审核 */
    private static final int STATUS_PENDING = 0;
    /** 1-已通过 */
    private static final int STATUS_APPROVED = 1;
    /** 2-已拒绝 */
    private static final int STATUS_REJECTED = 2;

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private ApprovalFlowConfigService approvalFlowConfigService;

    // ==================== 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecords submit(SubmitApprovalDto dto, Long applyUserId) {
        if (dto == null || !StringUtils.hasText(dto.getBizType())
                || dto.getBizId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "业务类型和业务id不能为空");
        }
        ApprovalBizType bt = ApprovalBizType.fromExt(dto.getBizType());
        if (bt == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知业务类型：" + dto.getBizType());
        }
        ApprovalFlowConfigVo config = approvalFlowConfigService.getEnabledByBizType(bt.getExt());
        ApprovalFlowNodeVo firstNode = nodeAt(config, 1);
        Long firstApproverId = resolveApproverForNode(config, firstNode, dto.getFirstApproverId());
        validateApprover(firstApproverId, applyUserId, Collections.emptySet(), allowsSelfApproval(config));
        // 同一业务已有待审核节点，禁止重复提交
        ApprovalRecords current = getCurrentPending(bt, dto.getBizId());
        if (current != null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "该业务已在审批中（当前审批人 id=" + current.getApprover() + "），请勿重复提交");
        }

        ApprovalRecords record = ApprovalRecords.builder()
                .approvalRecordType(bt.getCode())
                .bizTypeExt(bt.getExt())
                .inspectionFormId(dto.getBizId())
                .approver(firstApproverId)
                .applyUserId(applyUserId)
                .approvalDescription(dto.getDescription())
                .inspectionFormStatus(STATUS_PENDING)
                .approvalLevel(1)
                .parentRecordId(null)
                .nextApproverId(null)
                .build();
        try {
            approvalRecordsMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "该业务已在审批中，请勿重复提交");
        }
        log.info("审批提交 bizType={} bizId={} 申请人={} 首审={} recordId={}",
                bt.getExt(), dto.getBizId(), applyUserId, firstApproverId,
                record.getApprovalRecordId());
        return record;
    }

    // ==================== 审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalResult approve(ApproveDto dto, Long operatorId) {
        if (dto == null || dto.getRecordId() == null || dto.getPass() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批记录id / 是否通过不能为空");
        }
        ApprovalRecords record = approvalRecordsMapper.selectById(dto.getRecordId());
        if (record == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "审批记录不存在");
        }
        if (record.getInspectionFormStatus() == null
                || record.getInspectionFormStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "当前节点不是待审核状态");
        }
        if (operatorId == null || !operatorId.equals(record.getApprover())) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "您不是当前审批人");
        }

        ApprovalBizType bt = ApprovalBizType.fromExt(record.getBizTypeExt());
        if (bt == null) bt = ApprovalBizType.fromCode(record.getApprovalRecordType());

        boolean rejected = !Boolean.TRUE.equals(dto.getPass());
        ApprovalFlowConfigVo config = approvalFlowConfigService.getEnabledByBizType(bt == null ? null : bt.getExt());
        if (Objects.equals(operatorId, record.getApplyUserId()) && !allowsSelfApproval(config)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "当前流程不允许申请人审批自己的流程");
        }
        Long effectiveNextApproverId = rejected ? null : resolveNextApprover(
                config, record, dto.getNextApproverId(), Boolean.TRUE.equals(dto.getTerminate()));
        if (!rejected && effectiveNextApproverId != null) {
            Set<Long> previousApprovers = queryChain(bt, record.getInspectionFormId()).stream()
                    .map(ApprovalRecordVo::getApprover)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            validateApprover(effectiveNextApproverId, record.getApplyUserId(), previousApprovers,
                    allowsSelfApproval(config));
        }
        // 更新当前节点
        record.setInspectionFormStatus(rejected ? STATUS_REJECTED : STATUS_APPROVED);
        if (StringUtils.hasText(dto.getComment())) {
            record.setApprovalDescription(dto.getComment());
        }
        if (!rejected && effectiveNextApproverId != null) {
            record.setNextApproverId(effectiveNextApproverId);
        }
        record.setUpdatedTime(LocalDateTime.now());
        if (approvalRecordsMapper.updatePendingResult(record) != 1) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "该审批已被处理，请刷新后重试");
        }

        // 驳回 → 终审，链终止
        if (rejected) {
            log.info("审批驳回 bizType={} bizId={} recordId={} 操作人={}",
                    record.getBizTypeExt(), record.getInspectionFormId(), record.getApprovalRecordId(), operatorId);
            return new ApprovalResult(record.getApprovalRecordId(), bt,
                    record.getInspectionFormId(), true, true, null);
        }

        // 通过 + 还有下级 → 创建下一级节点
        if (effectiveNextApproverId != null) {
            ApprovalRecords next = ApprovalRecords.builder()
                    .approvalRecordType(record.getApprovalRecordType())
                    .bizTypeExt(record.getBizTypeExt())
                    .inspectionFormId(record.getInspectionFormId())
                    .approver(effectiveNextApproverId)
                    .applyUserId(record.getApplyUserId())
                    .approvalDescription(null)
                    .inspectionFormStatus(STATUS_PENDING)
                    .approvalLevel((record.getApprovalLevel() == null ? 1 : record.getApprovalLevel()) + 1)
                    .parentRecordId(record.getApprovalRecordId())
                    .nextApproverId(null)
                    .build();
            approvalRecordsMapper.insert(next);
            log.info("审批通过并转下一级 bizType={} bizId={} 当前={} 下一级审批={} nextRecordId={}",
                    record.getBizTypeExt(), record.getInspectionFormId(),
                    record.getApprovalRecordId(), effectiveNextApproverId, next.getApprovalRecordId());
            return new ApprovalResult(record.getApprovalRecordId(), bt,
                    record.getInspectionFormId(), false, false, next.getApprovalRecordId());
        }

        // 通过 + 终审
        log.info("审批终审通过 bizType={} bizId={} recordId={} 操作人={}",
                record.getBizTypeExt(), record.getInspectionFormId(), record.getApprovalRecordId(), operatorId);
        return new ApprovalResult(record.getApprovalRecordId(), bt,
                record.getInspectionFormId(), false, true, null);
    }

    // ==================== 查询 ====================

    @Override
    public List<ApprovalRecordVo> queryChain(ApprovalBizType bizType, Long bizId) {
        if (bizType == null || bizId == null) return Collections.emptyList();
        List<ApprovalRecords> list = approvalRecordsMapper
                .selectByBizTypeExtAndBizId(bizType.getExt(), bizId);
        if (list.isEmpty()) {
            list = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                    .eq(ApprovalRecords::getApprovalRecordType, bizType.getCode())
                    .eq(ApprovalRecords::getInspectionFormId, bizId)
                    .orderByAsc(ApprovalRecords::getCreatedTime)
                    .orderByAsc(ApprovalRecords::getApprovalRecordId));
        }
        return toVos(list);
    }

    @Override
    public ApprovalRecords getCurrentPending(ApprovalBizType bizType, Long bizId) {
        if (bizType == null || bizId == null) return null;
        LambdaQueryWrapper<ApprovalRecords> w = new LambdaQueryWrapper<>();
        w.eq(ApprovalRecords::getBizTypeExt, bizType.getExt())
                .eq(ApprovalRecords::getInspectionFormId, bizId)
                .eq(ApprovalRecords::getInspectionFormStatus, STATUS_PENDING)
                .orderByDesc(ApprovalRecords::getCreatedTime)
                .last("LIMIT 1");
        return approvalRecordsMapper.selectOne(w);
    }

    @Override
    public List<ApprovalRecordVo> listPendingByApprover(Long approverId, ApprovalBizType bizType) {
        if (approverId == null) return Collections.emptyList();
        LambdaQueryWrapper<ApprovalRecords> w = new LambdaQueryWrapper<>();
        w.eq(ApprovalRecords::getApprover, approverId)
                .eq(ApprovalRecords::getInspectionFormStatus, STATUS_PENDING);
        if (bizType != null) {
            w.eq(ApprovalRecords::getBizTypeExt, bizType.getExt());
        }
        w.orderByDesc(ApprovalRecords::getCreatedTime);
        return toVos(approvalRecordsMapper.selectList(w));
    }

    @Override
    public Map<String, Long> countPendingByApprover(Long approverId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ApprovalBizType bt : ApprovalBizType.values()) {
            result.put(bt.getExt(), 0L);
        }
        if (approverId == null) return result;

        LambdaQueryWrapper<ApprovalRecords> w = new LambdaQueryWrapper<>();
        w.eq(ApprovalRecords::getApprover, approverId)
                .eq(ApprovalRecords::getInspectionFormStatus, STATUS_PENDING);
        List<ApprovalRecords> rows = approvalRecordsMapper.selectList(w);
        for (ApprovalRecords r : rows) {
            String key = r.getBizTypeExt();
            if (key == null) {
                ApprovalBizType bt = ApprovalBizType.fromCode(r.getApprovalRecordType());
                if (bt != null) key = bt.getExt();
            }
            if (key != null) result.merge(key, 1L, Long::sum);
        }
        return result;
    }

    /**
     * 供方法权限表达式使用：申请人或审批链中的审批人可以查看对应业务详情。
     */
    public boolean isParticipant(String bizType, Long bizId, Long userId) {
        ApprovalBizType type = ApprovalBizType.fromExt(bizType);
        if (type == null || bizId == null || userId == null) return false;
        Long count = approvalRecordsMapper.selectCount(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getBizTypeExt, type.getExt())
                .eq(ApprovalRecords::getInspectionFormId, bizId)
                .and(w -> w.eq(ApprovalRecords::getApplyUserId, userId)
                        .or()
                        .eq(ApprovalRecords::getApprover, userId)));
        return count != null && count > 0;
    }

    // ==================== VO 转换 ====================

    private List<ApprovalRecordVo> toVos(List<ApprovalRecords> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Set<Long> userIds = new HashSet<>();
        for (ApprovalRecords r : list) {
            if (r.getApprover() != null) userIds.add(r.getApprover());
            if (r.getNextApproverId() != null) userIds.add(r.getNextApproverId());
        }
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        return list.stream().map(r -> {
            ApprovalRecordVo vo = ApprovalRecordVo.builder()
                    .approvalRecordId(r.getApprovalRecordId())
                    .approver(r.getApprover())
                    .applyUserId(r.getApplyUserId())
                    .approvalDescription(r.getApprovalDescription())
                    .inspectionFormStatus(r.getInspectionFormStatus())
                    .createdTime(r.getCreatedTime())
                    .bizType(r.getBizTypeExt())
                    .bizTypeCode(r.getApprovalRecordType())
                    .bizId(r.getInspectionFormId())
                    .approvalLevel(r.getApprovalLevel())
                    .nextApproverId(r.getNextApproverId())
                    .parentRecordId(r.getParentRecordId())
                    .updatedTime(r.getUpdatedTime())
                    .build();
            SysUser u = r.getApprover() == null ? null : userMap.get(r.getApprover());
            if (u != null) vo.setApproverName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            SysUser nu = r.getNextApproverId() == null ? null : userMap.get(r.getNextApproverId());
            if (nu != null) vo.setNextApproverName(nu.getRealName() != null ? nu.getRealName() : nu.getUsername());
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateApprover(Long userId,
                                  Long applyUserId,
                                  Set<Long> previousApprovers,
                                  boolean allowSelfApproval) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(0).equals(user.getEmploymentStatus())) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "所选审批人不存在、已停用或已离职");
        }
        if (!allowSelfApproval && Objects.equals(userId, applyUserId)) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "当前流程不允许申请人审批自己的流程");
        }
        if (previousApprovers.contains(userId)) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "审批人不能在审批链中重复出现");
        }
    }

    private boolean allowsSelfApproval(ApprovalFlowConfigVo config) {
        return config != null && Integer.valueOf(1).equals(config.getAllowSelfApproval());
    }

    private ApprovalFlowNodeVo nodeAt(ApprovalFlowConfigVo config, int level) {
        if (config == null || config.getNodes() == null) return null;
        return config.getNodes().stream()
                .filter(node -> Integer.valueOf(level).equals(node.getNodeOrder()))
                .findFirst()
                .orElse(null);
    }

    private Long resolveNextApprover(ApprovalFlowConfigVo config,
                                     ApprovalRecords record,
                                     Long requestedNextApproverId,
                                     boolean terminateRequested) {
        if (config == null) return terminateRequested ? null : requestedNextApproverId;
        int currentLevel = record.getApprovalLevel() == null ? 1 : record.getApprovalLevel();
        ApprovalFlowNodeVo currentNode = nodeAt(config, currentLevel);
        ApprovalFlowNodeVo nextNode = nodeAt(config, currentLevel + 1);
        if (nextNode == null) {
            if (requestedNextApproverId != null) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "流程配置中没有下一级审批节点");
            }
            return null;
        }
        boolean canFinishHere = currentNode != null && Integer.valueOf(1).equals(currentNode.getAllowTerminate());
        if (terminateRequested) {
            if (!canFinishHere) {
                throw new BusinessException(ErrorType.OPERATION_FAILED, "当前节点不允许终审通过");
            }
            return null;
        }
        return resolveApproverForNode(config, nextNode, requestedNextApproverId);
    }

    private Long resolveApproverForNode(ApprovalFlowConfigVo config, ApprovalFlowNodeVo node, Long requestedApproverId) {
        if (node == null) {
            if (requestedApproverId == null) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批人不能为空");
            }
            return requestedApproverId;
        }
        String sourceType = node.getApproverSourceType();
        if ("user".equals(sourceType)) {
            return parseSourceId(node.getApproverSourceId(), node.getNodeName());
        }
        if ("starter_select".equals(sourceType)) {
            if (config != null && !Integer.valueOf(1).equals(config.getAllowStarterSelectNext())) {
                throw new BusinessException(ErrorType.OPERATION_FAILED,
                        "流程配置不允许自选审批人，请为节点[" + node.getNodeName() + "]配置固定审批人");
            }
            if (requestedApproverId == null) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择节点[" + node.getNodeName() + "]的审批人");
            }
            return requestedApproverId;
        }
        if ("role".equals(sourceType)) {
            return resolveRoleApprover(node);
        }
        if ("position".equals(sourceType)) {
            return resolvePositionApprover(node);
        }
        if (requestedApproverId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择节点[" + node.getNodeName() + "]的审批人");
        }
        return requestedApproverId;
    }

    private Long resolveRoleApprover(ApprovalFlowNodeVo node) {
        Long roleId = parseSourceId(node.getApproverSourceId(), node.getNodeName());
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId)
                        .eq(SysUserRole::getProjectId, 0L))
                .stream()
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .filter(this::isActiveUser)
                .toList();
        return uniqueApprover(userIds, "角色", node.getNodeName());
    }

    private Long resolvePositionApprover(ApprovalFlowNodeVo node) {
        Long positionId = parseSourceId(node.getApproverSourceId(), node.getNodeName());
        List<Long> userIds = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPositionId, positionId)
                        .eq(SysUser::getStatus, 1)
                        .eq(SysUser::getEmploymentStatus, 1))
                .stream()
                .map(SysUser::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return uniqueApprover(userIds, "岗位", node.getNodeName());
    }

    private Long uniqueApprover(List<Long> userIds, String sourceLabel, String nodeName) {
        if (userIds.isEmpty()) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "节点[" + nodeName + "]配置的" + sourceLabel + "下没有可用审批人");
        }
        if (userIds.size() > 1) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "节点[" + nodeName + "]配置的" + sourceLabel + "匹配到多人，请改为指定用户或自选审批人");
        }
        return userIds.get(0);
    }

    private boolean isActiveUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && Integer.valueOf(1).equals(user.getStatus())
                && !Integer.valueOf(0).equals(user.getEmploymentStatus());
    }

    private Long parseSourceId(String value, String nodeName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "节点[" + nodeName + "]未配置审批来源");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "节点[" + nodeName + "]审批来源ID不合法");
        }
    }
}
