package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同字段变更日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("contract_change_log")
public class ContractChangeLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "change_log_id", type = IdType.ASSIGN_ID)
    private Long changeLogId;

    @TableField("contract_id")
    private Long contractId;

    @TableField("project_id")
    private Long projectId;

    @TableField("field_name")
    private String fieldName;

    @TableField("field_label")
    private String fieldLabel;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    @TableField("operator_id")
    private Long operatorId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
