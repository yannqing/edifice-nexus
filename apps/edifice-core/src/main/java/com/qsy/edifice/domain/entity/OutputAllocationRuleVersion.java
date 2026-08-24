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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("output_allocation_rule_version")
public class OutputAllocationRuleVersion {

    @TableId(value = "rule_version_id", type = IdType.AUTO)
    private Long ruleVersionId;

    @TableField("project_type_id")
    private Long projectTypeId;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("employee_pool_rate")
    private BigDecimal employeePoolRate;

    @TableField("company_base_rate")
    private BigDecimal companyBaseRate;

    @TableField("status")
    private Integer status;

    @TableField("created_by")
    private Long createdBy;

    @TableField("effective_time")
    private LocalDateTime effectiveTime;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
