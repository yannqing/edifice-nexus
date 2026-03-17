package com.qsy.edifice.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContractVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 合同id
     */
    private Long contractId;

    /**
     * 合同名称
     */
    private String contractName;

    /**
     * 合同唯一编码（项目编号）
     */
    private String contractCode;

    /**
     * 合同类型：0-基本收费/1-基本+效益
     */
    private Integer contractType;

    /**
     * 合同金额
     */
    private Integer contractAmount;

    /**
     * 合同主文件id
     */
    private Long contractFile;

    /**
     * 合同其他附件(id json 数组)
     */
    private String contractOtherFiles;

    /**
     * 基本收益金额
     */
    private Integer baseAmount;

    /**
     * 效益收益规则
     */
    private String benefitRules;

    /**
     * 项目签订日期
     */
    private LocalDateTime signingDate;

    /**
     * 项目预计开始日期
     */
    private LocalDateTime preStartDate;

    /**
     * 项目预计结束日期
     */
    private LocalDateTime preEndDate;
}
