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
import java.util.List;

/**
 * 验收单 VO（含审批链）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "验收单VO")
public class AcceptanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long acceptanceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;
    private String projectCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    private String stageName;

    /** 0-过程 / 1-成果 / 2-阶段性 */
    private Integer acceptanceType;
    private String acceptanceTypeLabel;

    private String title;
    private String content;

    /** json 数组字符串，前端自行解析 */
    private String fileIds;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyUserId;
    private String applyUserName;

    /** 0-待审批 / 1-审批中 / 2-通过 / 3-驳回 */
    private Integer status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentRecordId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentApproverId;
    private String currentApproverName;

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /** 审批链（按时间升序） */
    private List<ApprovalRecordVo> approvalChain;
}
