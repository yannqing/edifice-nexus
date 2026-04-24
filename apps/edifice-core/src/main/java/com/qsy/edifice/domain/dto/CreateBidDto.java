package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建投标
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建投标请求参数")
public class CreateBidDto {

    @Schema(description = "投标项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bidName;

    @Schema(description = "投标编号")
    private String bidCode;

    @Schema(description = "负责人id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ownerUserId;

    @Schema(description = "标的金额（元）")
    private BigDecimal tenderAmount;

    @Schema(description = "业主 / 甲方")
    private String clientName;

    @Schema(description = "投标日期（YYYY-MM-DD）")
    private LocalDate bidDate;

    @Schema(description = "结果日期（YYYY-MM-DD）")
    private LocalDate resultDate;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "附件清单")
    private List<BidFileItem> files;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "投标附件项")
    public static class BidFileItem {
        @Schema(description = "文件id（来自 /file/upload）")
        private Long fileId;
        @Schema(description = "分类：招标文件/投标文件/中标通知/其他")
        private String fileCategory;
    }
}
