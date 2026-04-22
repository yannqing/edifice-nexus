package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 验工单 Excel 导出数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionFormExcelData {

    @ExcelProperty("验工单编号")
    @ColumnWidth(25)
    private String inspectionFormCode;

    @ExcelProperty("项目名称")
    @ColumnWidth(30)
    private String projectName;

    @ExcelProperty("项目编码")
    @ColumnWidth(18)
    private String projectCode;

    @ExcelProperty("项目类型")
    @ColumnWidth(14)
    private String projectTypeName;

    @ExcelProperty("验工阶段")
    @ColumnWidth(18)
    private String stageName;

    @ExcelProperty("阶段产值比例(%)")
    @ColumnWidth(16)
    private BigDecimal stageOutput;

    @ExcelProperty("合同金额(元)")
    @ColumnWidth(15)
    private BigDecimal contractAmount;

    @ExcelProperty("阶段金额(元)")
    @ColumnWidth(15)
    private BigDecimal stageAmount;

    @ExcelProperty("申请人")
    @ColumnWidth(12)
    private String applyUserName;

    @ExcelProperty("申请时间")
    @ColumnWidth(20)
    private String applyTime;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;

    @ExcelProperty("验工说明")
    @ColumnWidth(40)
    private String description;
}
