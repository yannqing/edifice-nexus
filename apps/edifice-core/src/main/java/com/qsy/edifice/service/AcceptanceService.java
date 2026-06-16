package com.qsy.edifice.service;

import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateAcceptanceDto;
import com.qsy.edifice.domain.vo.AcceptanceVo;

import java.util.List;

/**
 * 成果 / 过程 / 阶段性验收服务（Phase 3 #4）
 *
 * 与项目文件审批共用 {@code ApprovalFlowService}，区别仅在业务主表不同。
 */
public interface AcceptanceService {

    /** 创建验收单并提交到审批链（一级：项目负责人） */
    Long createAndSubmit(CreateAcceptanceDto dto, Long applyUserId);

    /** 审批（通过 / 驳回），委托 ApprovalFlowService 并维护主表 status */
    void approve(ApproveDto dto, Long operatorId);

    /**
     * 列表查询
     * @param projectId       可空
     * @param acceptanceType  可空（0/1/2）
     * @param status          可空（0/1/2/3）
     * @param keyword         可空，模糊 title / content
     */
    List<AcceptanceVo> list(Long projectId, Integer acceptanceType, Integer status, String keyword);

    /** 详情 + 审批链 */
    AcceptanceVo getDetail(Long acceptanceId, Long userId, boolean canViewAll);

    /** 我的待审 */
    List<AcceptanceVo> listMyPending(Long userId);
}
