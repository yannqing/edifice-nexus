package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工时记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("timesheet")
public class Timesheet implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "timesheet_id", type = IdType.ASSIGN_ID)
    private Long timesheetId;

    @TableField("user_id")
    private Long userId;

    @TableField("project_id")
    private Long projectId;

    @TableField("project_stage_id")
    private Long projectStageId;

    /** 工作类型：0-管理工作/1-基础工作/2-智励工作 */
    @TableField("work_type")
    private Integer workType;

    @TableField("work_date")
    private LocalDate workDate;

    @TableField("hours")
    private BigDecimal hours;

    @TableField("description")
    private String description;

    /** 状态：0-草稿/1-已提交 */
    @TableField("status")
    private Integer status;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
