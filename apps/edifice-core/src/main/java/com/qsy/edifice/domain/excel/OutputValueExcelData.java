package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 产值分配 Excel 导出数据模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputValueExcelData {

    @ExcelProperty("项目名称")
    @ColumnWidth(30)
    private String projectName;

    @ExcelProperty("项目编码")
    @ColumnWidth(18)
    private String projectCode;

    @ExcelProperty("项目类型")
    @ColumnWidth(16)
    private String projectTypeName;

    @ExcelProperty("阶段")
    @ColumnWidth(18)
    private String stageName;

    @ExcelProperty("季度")
    @ColumnWidth(12)
    private String quarter;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String status;

    @ExcelProperty("本期产值(元)")
    @ColumnWidth(16)
    private BigDecimal totalAmount;

    @ExcelProperty("阶段累计应得(元)")
    @ColumnWidth(18)
    private BigDecimal stageCumulativeAmount;

    @ExcelProperty("上次累计(元)")
    @ColumnWidth(16)
    private BigDecimal previousCumulativeAmount;

    @ExcelProperty("基本部分(元)")
    @ColumnWidth(16)
    private BigDecimal baseAmountPart;

    @ExcelProperty("效益部分(元)")
    @ColumnWidth(16)
    private BigDecimal benefitAmountPart;

    @ExcelProperty("公司账(元)")
    @ColumnWidth(16)
    private BigDecimal companyReserve;

    @ExcelProperty("离职兜底(元)")
    @ColumnWidth(16)
    private BigDecimal otherAmount;

    @ExcelProperty("公司补贴(元)")
    @ColumnWidth(16)
    private BigDecimal subsidyAmount;

    @ExcelProperty("提交人")
    @ColumnWidth(14)
    private String submitUserName;

    @ExcelProperty("提交时间")
    @ColumnWidth(20)
    private String submitTime;

    @ExcelProperty("审批时间")
    @ColumnWidth(20)
    private String approvedTime;

    @ExcelProperty("发放时间")
    @ColumnWidth(20)
    private String paidTime;

    @ExcelProperty("分配人员")
    @ColumnWidth(14)
    private String userName;

    @ExcelProperty("项目角色")
    @ColumnWidth(14)
    private String userRole;

    @ExcelProperty("工作类型")
    @ColumnWidth(14)
    private String workType;

    @ExcelProperty("分配比例(%)")
    @ColumnWidth(14)
    private BigDecimal allocRatio;

    @ExcelProperty("完成比例(%)")
    @ColumnWidth(14)
    private BigDecimal completionRatio;

    @ExcelProperty("分配类型")
    @ColumnWidth(14)
    private String distType;

    @ExcelProperty("人员状态")
    @ColumnWidth(12)
    private String activeStatus;

    @ExcelProperty("实得金额(元)")
    @ColumnWidth(16)
    private BigDecimal actualAmount;
}
