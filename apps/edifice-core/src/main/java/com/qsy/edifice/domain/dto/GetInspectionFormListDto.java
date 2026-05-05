package com.qsy.edifice.domain.dto;


import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "验工单列表查询条件")
public class GetInspectionFormListDto {

    /**
     * 验工单id
     */
    @Schema(description = "验工单id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long inspectionFormId;

    /**
     * 验工单编号
     */
    @Schema(description = "验工单编号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String inspectionFormCode;

    /**
     * 项目id
     */
    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String projectId;

    /**
     * 项目阶段id
     */
    @Schema(description = "项目阶段id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long projectStageId;

    /**
     * 申请人id
     */
    @Schema(description = "申请人id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long applyUserId;

    /**
     * 验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿
     */
    @Schema(description = "验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer inspectionFormStatus;

    /**
     * 状态白名单（用于"未处理" = 0 + 1 这种组合筛选）；与 inspectionFormStatus 同时传时优先生效
     */
    @Schema(description = "状态白名单，如 [0,1]")
    private List<Integer> inspectionFormStatuses;

    /**
     * 当前页
     */
    @Schema(description = "当前页", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer current = 1;

    /**
     * 一页大小
     */
    @Schema(description = "一页大小", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer pageSize = 5;

}

