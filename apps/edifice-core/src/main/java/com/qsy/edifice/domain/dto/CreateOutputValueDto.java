package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建产值分配单请求参数")
public class CreateOutputValueDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectStageId;

    @Schema(description = "阶段产值总额（元）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;

    @Schema(description = "分配明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DistributionItem> distributions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DistributionItem {
        @Schema(description = "分配对象用户id")
        private Long userId;

        /** 0-管理工作/1-基础工作/2-智励工作 */
        @Schema(description = "工作类型")
        private Integer workType;

        @Schema(description = "分配比例（%）")
        private BigDecimal ratio;

        @Schema(description = "分配金额（元）")
        private BigDecimal amount;
    }
}
