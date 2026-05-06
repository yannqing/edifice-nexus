package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_department")
public class SysDepartment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "department_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    @TableField("oa_department_id")
    private Integer oaDepartmentId;

    @TableField("parent_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @TableField("oa_parent_id")
    private Integer oaParentId;

    @TableField("name")
    private String name;

    @TableField("leader_user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaderUserId;

    @TableField("sort")
    private Integer sort;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    @TableField("synced_at")
    private LocalDateTime syncedAt;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
