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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "投标 VO")
public class BidVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bidId;

    private String bidName;
    private String bidCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;
    private String ownerUserName;

    private BigDecimal tenderAmount;

    /** 业务状态：0-筹备/1-已投递/2-中标/3-未中标/4-终止 */
    private Integer bidStatus;
    private String bidStatusLabel;

    private LocalDate bidDate;
    private LocalDate resultDate;

    private String clientName;
    private String description;

    /** 审批状态：0-草稿/1-审核中/2-通过/3-驳回 */
    private Integer approvalStatus;
    private String approvalStatusLabel;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentRecordId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentApproverId;
    private String currentApproverName;

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private List<BidFileVo> files;
    private List<ApprovalRecordVo> approvalChain;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BidFileVo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long bidFileId;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long fileId;

        private String fileName;
        private String fileUrl;
        private String fileExtension;
        private String fileSize;
        private String fileCategory;
        private LocalDateTime createdTime;
    }
}
