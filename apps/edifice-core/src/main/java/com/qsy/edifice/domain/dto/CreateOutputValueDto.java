package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建产值分配单请求参数（v0.4 修订）
 *
 * 计算口径（v0.4 与 v0.2 的关键差异）：
 * - 阶段产值（totalAmount）由系统自动计算：基于合同 base_amount + benefit_amount 与
 *   阶段的累计计入比例（stage_output / benefit_inclusion_ratio）；
 *   本期产值 = 当前累计 - 上一次累计
 * - 比例对调：员工池 40%、公司账 60%（v0.2 写反了）
 * - 每人应得 = 员工池 × allocRatio%
 * - 每人实得 = 每人应得 × completionRatio%
 * - 降档差额（应得-实得）→ 公司账
 * - 离职成员应得 → 公司账（独立记账 other_amount，但实际归公司）
 * - 不再使用 leader_extra（始终 0）
 * - 守恒：Σ员工实得 + company_reserve = totalAmount
 *   其中 company_reserve = 60% 主体 + 降档差额 + 离职兜底
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建产值分配单请求参数（v0.4）")
public class CreateOutputValueDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectStageId;

    @Schema(description = "所属季度，格式 YYYY-Qn", requiredMode = Schema.RequiredMode.REQUIRED)
    private String quarter;

    /**
     * v0.4 起 totalAmount 由系统从合同 + 阶段比例自动算出，前端不再传。
     * 保留字段仅作向后兼容（若传入会被忽略）。
     */
    @Deprecated
    @Schema(description = "[v0.4 已废弃] 阶段产值总额，前端无需再传，由系统自动计算")
    private BigDecimal totalAmount;

    @Schema(description = "公司补贴（元，只记录不计入产值）")
    private BigDecimal subsidyAmount;

    /**
     * 当效益修正导致本期产值为负（罕见）时，财务需显式确认才能落库。
     */
    @Schema(description = "允许本期产值为负（效益下调时使用，需财务显式确认）")
    private Boolean allowNegative;

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

        /** 在员工池（40%）内的分配比例，0-100，合计应为 100 */
        @Schema(description = "分配比例（%，在 40% 员工池内；正常 ~20.15% 即对应 8.06% of 总产值）")
        private BigDecimal allocRatio;

        /** 完成比例，0-100；100 为全完成；低于 100 则差额归公司账 */
        @Schema(description = "完成比例（%）")
        private BigDecimal completionRatio;

        /** 在职状态快照：0-离职/1-在职；为空时后端按当前用户状态推断 */
        @Schema(description = "下单时在职状态：0-离职/1-在职（可空，后端推断）")
        private Integer isActive;
    }
}
