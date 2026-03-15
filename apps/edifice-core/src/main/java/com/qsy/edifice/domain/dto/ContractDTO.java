package com.qsy.edifice.domain.dto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ContractDTO {

    /**
     * 合同名称（如：XX系统开发合同）
     */
    @NotBlank(message = "合同名称不能为空")
    private String contractName;

    /**
     * 合同唯一编码（如：CT-2026-001），不是项目ID！
     */
    @NotBlank(message = "合同编号不能为空")
    @Pattern(regexp = "^[A-Z0-9\\-_]{3,50}$", message = "合同编号格式不合法")
    private String contractCode;  // ← 改名为 contractCode

    /**
     * 合同类型：0-基本收费 / 1-基本+效益分成
     */
    @NotNull(message = "合同类型不能为空")
    @Min(value = 0, message = "合同类型无效")
    @Max(value = 1, message = "合同类型无效")
    private Integer contractType;

    /**
     * 合同总金额（单位：元，整数）
     */
    @NotNull(message = "合同金额不能为空")
    @Min(value = 1, message = "合同金额必须大于0")
    private Integer contractAmount;

    /**
     * 合同主文件ID（文件服务返回的ID）
     */
    private Long contractFile; // 可为空（创建时可能未上传）

    /**
     * 其他附件ID列表（JSON数组字符串，如 "[101,102]"）
     */
    private String contractOtherFiles; // 可选

    /**
     * 基本收益金额（仅当 contractType=1 时必填）
     */
    private Integer baseAmount;

    /**
     * 效益收益规则（JSON 或 自由文本，如：按回款5%提成）
     */
    private String benefitRules;

    /**
     * 合同签订日期
     */
    @NotNull(message = "签订日期不能为空")
    @FutureOrPresent(message = "签订日期不能晚于今天")
    private LocalDateTime signingDate;

    /**
     * 项目预计开始日期
     */
    @NotNull(message = "预计开始日期不能为空")
    private LocalDateTime preStartDate;

    /**
     * 项目预计结束日期
     */
    @NotNull(message = "预计结束日期不能为空")
    private LocalDateTime preEndDate;
}
