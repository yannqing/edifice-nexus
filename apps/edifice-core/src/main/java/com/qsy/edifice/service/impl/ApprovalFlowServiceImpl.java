package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.ApprovalFlowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // ==================== 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecords submit(SubmitApprovalDto dto, Long applyUserId) {
        if (dto == null || !StringUtils.hasText(dto.getBizType())
                || dto.getBizId() == null || dto.getFirstApproverId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "业务类型 / 业务id / 第一审批人不能为空");
        }
        ApprovalBizType bt = ApprovalBizType.fromExt(dto.getBizType());
        if (bt == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知业务类型：" + dto.getBizType());
        }
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
                .approver(dto.getFirstApproverId())
                .approvalDescription(dto.getDescription())
                .inspectionFormStatus(STATUS_PENDING)
                .approvalLevel(1)
                .parentRecordId(null)
                .nextApproverId(null)
                .build();
        approvalRecordsMapper.insert(record);
        log.info("审批提交 bizType={} bizId={} 申请人={} 首审={} recordId={}",
                bt.getExt(), dto.getBizId(), applyUserId, dto.getFirstApproverId(),
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
        // 更新当前节点
        record.setInspectionFormStatus(rejected ? STATUS_REJECTED : STATUS_APPROVED);
        if (StringUtils.hasText(dto.getComment())) {
            record.setApprovalDescription(dto.getComment());
        }
        if (!rejected && dto.getNextApproverId() != null) {
            record.setNextApproverId(dto.getNextApproverId());
        }
        record.setUpdatedTime(LocalDateTime.now());
        approvalRecordsMapper.updateById(record);

        // 驳回 → 终审，链终止
        if (rejected) {
            log.info("审批驳回 bizType={} bizId={} recordId={} 操作人={}",
                    record.getBizTypeExt(), record.getInspectionFormId(), record.getApprovalRecordId(), operatorId);
            return new ApprovalResult(record.getApprovalRecordId(), bt,
                    record.getInspectionFormId(), true, true, null);
        }

        // 通过 + 还有下级 → 创建下一级节点
        if (dto.getNextApproverId() != null) {
            ApprovalRecords next = ApprovalRecords.builder()
                    .approvalRecordType(record.getApprovalRecordType())
                    .bizTypeExt(record.getBizTypeExt())
                    .inspectionFormId(record.getInspectionFormId())
                    .approver(dto.getNextApproverId())
                    .approvalDescription(null)
                    .inspectionFormStatus(STATUS_PENDING)
                    .approvalLevel((record.getApprovalLevel() == null ? 1 : record.getApprovalLevel()) + 1)
                    .parentRecordId(record.getApprovalRecordId())
                    .nextApproverId(null)
                    .build();
            approvalRecordsMapper.insert(next);
            log.info("审批通过并转下一级 bizType={} bizId={} 当前={} 下一级审批={} nextRecordId={}",
                    record.getBizTypeExt(), record.getInspectionFormId(),
                    record.getApprovalRecordId(), dto.getNextApproverId(), next.getApprovalRecordId());
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
}
