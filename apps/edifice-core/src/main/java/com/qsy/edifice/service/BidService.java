package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateBidDto;
import com.qsy.edifice.domain.dto.UpdateBidDto;
import com.qsy.edifice.domain.dto.UpdateBidStatusDto;
import com.qsy.edifice.domain.vo.BidVo;

import java.util.List;

/**
 * 投标服务接口（Phase 3 #5）
 *
 * 两条独立状态线：
 * - {@code bidStatus}（业务生命周期）：筹备 → 已投递 → 中标/未中标/终止
 * - {@code approvalStatus}（内部审批）：草稿 → 审核中 → 通过/驳回
 * 审批链只针对 {@code approvalStatus}，业务状态由负责人直接切换。
 */
public interface BidService {

    Long create(CreateBidDto dto, Long operatorId);

    void update(UpdateBidDto dto, Long operatorId);

    void delete(Long bidId);

    /** 切换业务状态（看板拖拽 / 按钮） */
    void updateBidStatus(UpdateBidStatusDto dto);

    /** 提交审批（草稿 → 审核中） */
    void submitApproval(Long bidId, Long firstApproverId, Long operatorId);

    /** 审批（委托 ApprovalFlowService） */
    void approve(ApproveDto dto, Long operatorId);

    /**
     * 列表
     * @param bidStatus       可空（0-4）
     * @param approvalStatus  可空（0-3）
     * @param keyword         模糊匹配 bid_name / bid_code / client_name
     */
    List<BidVo> list(Integer bidStatus, Integer approvalStatus, String keyword);

    /** 详情（含附件 + 审批链） */
    BidVo getDetail(Long bidId);

    /** 我的待审投标 */
    List<BidVo> listMyPending(Long userId);
}
