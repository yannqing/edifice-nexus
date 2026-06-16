package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 合同管理 Excel 导出数据模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractExcelData {

    @ExcelProperty("合同名称")
    @ColumnWidth(30)
    private String contractName;

    @ExcelProperty("合同编号")
    @ColumnWidth(24)
    private String contractCode;

    @ExcelProperty("合同类型")
    @ColumnWidth(14)
    private String contractType;

    @ExcelProperty("合同金额(元)")
    @ColumnWidth(16)
    private BigDecimal contractAmount;

    @ExcelProperty("基本收费(元)")
    @ColumnWidth(16)
    private BigDecimal baseAmount;

    @ExcelProperty("预计效益(元)")
    @ColumnWidth(16)
    private BigDecimal benefitAmount;

    @ExcelProperty("效益状态")
    @ColumnWidth(14)
    private String benefitStatus;

    @ExcelProperty("关联项目")
    @ColumnWidth(30)
    private String projectName;

    @ExcelProperty("项目编号")
    @ColumnWidth(20)
    private String projectCode;

    @ExcelProperty("项目状态")
    @ColumnWidth(14)
    private String projectStatus;

    @ExcelProperty("签订日期")
    @ColumnWidth(20)
    private String signingDate;

    @ExcelProperty("预计开始")
    @ColumnWidth(20)
    private String preStartDate;

    @ExcelProperty("预计结束")
    @ColumnWidth(20)
    private String preEndDate;

    @ExcelProperty("效益规则")
    @ColumnWidth(40)
    private String benefitRules;
}
