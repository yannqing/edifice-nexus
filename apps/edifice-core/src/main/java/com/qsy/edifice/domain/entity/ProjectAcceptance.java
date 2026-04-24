package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 验收单（成果 / 过程 / 阶段性，三合一用 {@code acceptance_type} 区分）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_acceptance")
public class ProjectAcceptance implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "acceptance_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long acceptanceId;

    @TableField("project_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /** 成果 / 过程验收可空 */
    @TableField("project_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    /** 0-过程 / 1-成果 / 2-阶段性验收 */
    @TableField("acceptance_type")
    private Integer acceptanceType;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    /** 附件 id 列表（json 数组字符串） */
    @TableField("file_ids")
    private String fileIds;

    @TableField("apply_user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyUserId;

    /** 0-待审批 / 1-审批中 / 2-通过 / 3-驳回 */
    @TableField("status")
    private Integer status;

    @TableField("current_record_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentRecordId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
