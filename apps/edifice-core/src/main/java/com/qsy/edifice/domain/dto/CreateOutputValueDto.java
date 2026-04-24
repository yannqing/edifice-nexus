package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建产值分配单请求参数（v0.2 新版）
 *
 * 计算口径：
 * - 公司留存 = totalAmount × 40%（固定）
 * - 个人池   = totalAmount × 60%
 * - 每人应得 = 个人池 × allocRatio%
 * - 每人实得 = 每人应得 × completionRatio%
 * - 降档差额 = 每人应得 - 每人实得，归入领导兜底（leaderExtra）
 * - 离职成员：每人应得 全部归入 其他金额（otherAmount），本人不得
 * - 公司补贴（subsidyAmount）只记录，不计入总额
 * - 守恒：公司留存 + Σ每人实得 + 领导兜底 + 其他金额 = totalAmount
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建产值分配单请求参数")
public class CreateOutputValueDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectStageId;

    @Schema(description = "所属季度，格式 YYYY-Qn")
    private String quarter;

    @Schema(description = "阶段产值总额（元）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;

    @Schema(description = "公司补贴（元，只记录不计入产值）")
    private BigDecimal subsidyAmount;

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

        /** 在个人池（60%）内的分配比例，0-100，合计应为 100 */
        @Schema(description = "分配比例（%，在 60% 个人池内）")
        private BigDecimal allocRatio;

        /** 完成比例，0-100；100 为全完成；低于 100 则差额归领导兜底 */
        @Schema(description = "完成比例（%）")
        private BigDecimal completionRatio;

        /** 在职状态快照：0-离职/1-在职；为空时后端按当前用户状态推断 */
        @Schema(description = "下单时在职状态：0-离职/1-在职（可空，后端推断）")
        private Integer isActive;
    }
}
