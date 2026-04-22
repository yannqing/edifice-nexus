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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "回款记录VO")
public class CollectionRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long collectionRecordId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;
    private String projectCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    private String stageName;

    private Integer amount;
    private LocalDate collectDate;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long voucherFileId;

    private String remark;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordUserId;

    private String recordUserName;

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
