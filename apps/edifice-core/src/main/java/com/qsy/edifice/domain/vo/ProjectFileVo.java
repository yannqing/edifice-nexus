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
 * 项目文件 VO（含审批链）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "项目文件VO")
public class ProjectFileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectFileId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;
    private String projectCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    private String stageName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    /** 源文件元数据（从 files 表补齐） */
    private String fileName;
    private String fileUrl;
    private String fileExtension;
    private String fileSize;
    private String thumbnailUrl;

    private String fileCategory;
    private String description;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploadUserId;

    private String uploadUserName;

    /** 0-待提交/1-审批中/2-通过/3-驳回 */
    private Integer approvalStatus;

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
