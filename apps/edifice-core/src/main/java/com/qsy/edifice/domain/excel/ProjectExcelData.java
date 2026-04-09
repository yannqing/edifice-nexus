package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目信息 Excel 数据模型（Sheet1）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectExcelData {

    @ExcelProperty("项目名称")
    @ColumnWidth(30)
    private String projectName;

    @ExcelProperty("项目编码")
    @ColumnWidth(20)
    private String projectCode;

    @ExcelProperty("项目类型编码")
    @ColumnWidth(15)
    private String projectTypeCode;

    @ExcelProperty("项目状态")
    @ColumnWidth(12)
    private String projectStatus;

    @ExcelProperty("合同类型")
    @ColumnWidth(12)
    private String contractType;

    @ExcelProperty("合同金额（元）")
    @ColumnWidth(15)
    private Integer contractAmount;

    @ExcelProperty("基础金额（元）")
    @ColumnWidth(15)
    private Integer baseAmount;

    @ExcelProperty("效益规则")
    @ColumnWidth(20)
    private String benefitRules;

    @ExcelProperty("预计开始日期")
    @ColumnWidth(15)
    private String preStartDate;

    @ExcelProperty("预计结束日期")
    @ColumnWidth(15)
    private String preEndDate;
}
