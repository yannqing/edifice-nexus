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
 * 项目文件实体类（Phase 3 扩展：三级审批）
 *
 * 注意 {@code project_id} 历史遗留为 {@code varchar(64)}，与其它表的 {@code bigint}
 * 口径不一致。为避免数据迁移风险，保留字段类型 {@code String}，在业务层与
 * {@code Long} 口径互转（见 Service）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_files")
public class ProjectFiles implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "project_file_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectFileId;

    /** 项目id（历史遗留 varchar 列，业务层按 Long 口径传入） */
    @TableField("project_id")
    private String projectId;

    @TableField("project_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    @TableField("file_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    /** 上传人id */
    @TableField("upload_user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploadUserId;

    /** 文件分类：图纸 / 合同 / 报告 / 其他 */
    @TableField("file_category")
    private String fileCategory;

    /** 文件说明 */
    @TableField("description")
    private String description;

    /** 审批状态：0-待提交/1-审批中/2-通过/3-驳回 */
    @TableField("approval_status")
    private Integer approvalStatus;

    /** 当前待审记录id（快照，方便快速拿到当前审批人） */
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
