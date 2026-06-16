package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批记录 VO（通用审批链）
 *
 * 旧字段（{@code approvalRecordId}/{@code approver}/{@code approvalDescription}/
 * {@code inspectionFormStatus}）保留，用于验工单等老模块；新增字段为 Phase 3
 * ApprovalFlowService 的通用输出。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "审批记录VO")
public class ApprovalRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "审批记录id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalRecordId;

    @Schema(description = "审批人id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approver;

    @Schema(description = "审批流程发起人id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyUserId;

    @Schema(description = "审批人姓名")
    private String approverName;

    @Schema(description = "审批说明 / 意见")
    private String approvalDescription;

    @Schema(description = "审批状态：0-待审核/1-已通过/2-已拒绝")
    private Integer inspectionFormStatus;

    @Schema(description = "审批时间")
    private LocalDateTime createdTime;

    // ==================== 通用审批链扩展字段（Phase 3） ====================

    @Schema(description = "业务类型 ext：file/inspection/bid/acceptance/output/timesheet")
    private String bizType;

    @Schema(description = "旧 approval_record_type 数值")
    private Integer bizTypeCode;

    @Schema(description = "业务id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    @Schema(description = "层级（1 起）")
    private Integer approvalLevel;

    @Schema(description = "下一级审批人id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextApproverId;

    @Schema(description = "下一级审批人姓名")
    private String nextApproverName;

    @Schema(description = "上一步审批记录id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentRecordId;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
