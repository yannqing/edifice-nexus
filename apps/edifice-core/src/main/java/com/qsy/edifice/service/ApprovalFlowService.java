package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.enums.ApprovalBizType;

import java.util.List;
import java.util.Map;

/**
 * 通用审批流服务（Phase 3 #1）
 *
 * 设计要点：
 * - 复用 {@code approval_records} 单表，通过 {@code biz_type_ext} + {@code inspection_form_id}（作为 biz_id）
 *   区分业务。老字段 {@code approval_record_type} 同步写入以保证向后兼容。
 * - 每条业务同一时刻至多只有 **一条** 待审核节点；上游业务可通过
 *   {@link #getCurrentPending(ApprovalBizType, Long)} 判断当前审批人。
 * - {@code approve} 通过且 {@code nextApproverId} 非空时，级联创建下一级节点；
 *   {@code nextApproverId} 为空视为终审，终审通过 / 驳回都返回 {@link ApprovalResult}，
 *   由业务侧监听并更新主表状态。
 */
public interface ApprovalFlowService {

    /**
     * 提交审批：创建第一条待审核节点。
     *
     * @param dto          {@link SubmitApprovalDto}
     * @param applyUserId  提交人（写入整条审批链，用于结果通知和追踪）
     * @return 新建的审批记录
     */
    ApprovalRecords submit(SubmitApprovalDto dto, Long applyUserId);

    /**
     * 审批操作：通过 / 驳回。
     *
     * @param dto         {@link ApproveDto}
     * @param operatorId  操作人（必须等于当前待审核节点的 approver）
     * @return 审批结果（含 isFinal / rejected / bizType / bizId，便于业务侧决策）
     */
    ApprovalResult approve(ApproveDto dto, Long operatorId);

    /**
     * 查询某条业务的审批链（按时间升序）。
     */
    List<ApprovalRecordVo> queryChain(ApprovalBizType bizType, Long bizId);

    /**
     * 查询当前"待审核"节点（同一业务最多一条），不存在则返回 null。
     */
    ApprovalRecords getCurrentPending(ApprovalBizType bizType, Long bizId);

    /**
     * 查询某审批人待办，可按业务类型过滤。
     */
    List<ApprovalRecordVo> listPendingByApprover(Long approverId, ApprovalBizType bizType);

    /**
     * 查询某审批人在各业务类型下的待办数量。
     * 返回以 {@link ApprovalBizType#getExt()} 为 key 的计数表，未出现的类型视为 0。
     */
    Map<String, Long> countPendingByApprover(Long approverId);

    /**
     * 审批操作返回结果（业务侧据此判断是否需要更新主表状态）。
     */
    class ApprovalResult {
        /** 当前被操作的记录id */
        public final Long recordId;
        /** 业务类型 */
        public final ApprovalBizType bizType;
        /** 业务id */
        public final Long bizId;
        /** 本次是否驳回 */
        public final boolean rejected;
        /** 是否终审（本次通过或驳回后，流程结束） */
        public final boolean isFinal;
        /** 若创建了下一级节点，其 id */
        public final Long nextRecordId;

        public ApprovalResult(Long recordId, ApprovalBizType bizType, Long bizId,
                              boolean rejected, boolean isFinal, Long nextRecordId) {
            this.recordId = recordId;
            this.bizType = bizType;
            this.bizId = bizId;
            this.rejected = rejected;
            this.isFinal = isFinal;
            this.nextRecordId = nextRecordId;
        }
    }
}
