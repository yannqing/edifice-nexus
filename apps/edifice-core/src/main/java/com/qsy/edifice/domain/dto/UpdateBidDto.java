package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 更新投标（基础信息 + 附件全量替换）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "更新投标请求参数")
public class UpdateBidDto {

    @Schema(description = "投标id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long bidId;

    @Schema(description = "投标项目名称")
    private String bidName;

    @Schema(description = "投标编号")
    private String bidCode;

    @Schema(description = "负责人id")
    private Long ownerUserId;

    @Schema(description = "标的金额（元）")
    private BigDecimal tenderAmount;

    @Schema(description = "业主 / 甲方")
    private String clientName;

    @Schema(description = "投标日期")
    private LocalDate bidDate;

    @Schema(description = "结果日期")
    private LocalDate resultDate;

    @Schema(description = "说明")
    private String description;

    /** 非空时全量替换附件 */
    @Schema(description = "附件清单（非空时全量替换）")
    private List<CreateBidDto.BidFileItem> files;
}
