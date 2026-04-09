package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目文件实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_files")
public class ProjectFiles implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目文件id
     */
    @TableId(value = "project_file_id", type = IdType.ASSIGN_ID)
    private Long projectFileId;

    /**
     * 项目id
     */
    @TableField("project_id")
    private String projectId;

    /**
     * 项目阶段id
     */
    @TableField("project_stage_id")
    private Long projectStageId;

    /**
     * 文件id
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除
     */
    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
