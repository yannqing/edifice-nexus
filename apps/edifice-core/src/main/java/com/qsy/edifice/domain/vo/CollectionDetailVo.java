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
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目回款详情 VO（弹窗用）：按阶段分组 + 原始记录
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "项目回款详情VO")
public class CollectionDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目级汇总 */
    private CollectionSummaryVo summary;

    /** 按阶段聚合的回款情况 */
    private List<StageCollectionVo> stageCollections;

    /** 原始记录（按时间倒序） */
    private List<CollectionRecordVo> records;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StageCollectionVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long projectStageId;

        private String stageName;

        /** 阶段产值比例 (%) */
        private BigDecimal stageOutput;

        /** 阶段计划回款金额 = 合同金额 × stageOutput% */
        private Integer planAmount;

        /** 该阶段已回款金额（元） */
        private Integer actualAmount;

        /** 0-未回款/1-部分回款/2-已回款 */
        private Integer status;
    }
}
