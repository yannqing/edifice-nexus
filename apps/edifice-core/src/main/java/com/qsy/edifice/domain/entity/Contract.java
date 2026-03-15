package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("contract")
public class Contract implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 合同id
     */
    @TableId(value = "contract_id", type = IdType.ASSIGN_ID)
    private Long contractId;
    /**
     * 合同名称
     */
    @TableField("project_d")
    private Long projectId;

    /**
     * 合同名称
     */
    @TableField("contract_name")
    private String contractName;

    /**
     * 合同唯一编码（项目编号）
     */
    @TableField("contract_code")
    private String contractCode;

    /**
     * 合同类型：0-基本收费/1-基本+效益
     */
    @TableField("contract_type")
    private Integer contractType;

    /**
     * 合同金额
     */
    @TableField("contract_amount")
    private Integer contractAmount;

    /**
     * 合同主文件id
     */
    @TableField("contract_file")
    private Long contractFile;

    /**
     * 合同其他附件(id json 数组)
     */
    @TableField("contract_other_files")
    private String contractOtherFiles;

    /**
     * 基本收益金额
     */
    @TableField("base_amount")
    private Integer baseAmount;

    /**
     * 效益收益规则
     */
    @TableField("benefit_rules")
    private String benefitRules;

    /**
     * 项目签订日期
     */
    @TableField("signing_date")
    private LocalDateTime signingDate;

    /**
     * 项目预计开始日期
     */
    @TableField("pre_start_date")
    private LocalDateTime preStartDate;

    /**
     * 项目预计结束日期
     */
    @TableField("pre_end_date")
    private LocalDateTime preEndDate;

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
