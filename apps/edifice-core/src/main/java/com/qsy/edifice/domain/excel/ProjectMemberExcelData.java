package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目成员 Excel 数据模型（Sheet2）
 * 一个成员一行，通过项目编码关联项目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberExcelData {

    @ExcelProperty("项目编码")
    @ColumnWidth(20)
    private String projectCode;

    @ExcelProperty("姓名")
    @ColumnWidth(15)
    private String realName;

    @ExcelProperty("手机号")
    @ColumnWidth(18)
    private String phone;

    @ExcelProperty("邮箱")
    @ColumnWidth(25)
    private String email;

    @ExcelProperty("角色")
    @ColumnWidth(12)
    private String role;
}
