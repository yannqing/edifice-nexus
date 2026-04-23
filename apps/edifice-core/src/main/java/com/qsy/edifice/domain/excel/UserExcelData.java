package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户花名册 Excel 数据模型
 *
 * 表头和列顺序对齐 docs/assets/users.xls（"全部（含离职）"Sheet）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExcelData {

    @ExcelProperty("编号")
    @ColumnWidth(10)
    private String employeeNo;

    @ExcelProperty("姓名")
    @ColumnWidth(12)
    private String realName;

    /** "男" / "女" / "其他" */
    @ExcelProperty("性别")
    @ColumnWidth(8)
    private String gender;

    @ExcelProperty("民族")
    @ColumnWidth(8)
    private String ethnicity;

    /** 支持 "1990.05.01" / "1990-05-01" / "1990/05/01" */
    @ExcelProperty("出生日期")
    @ColumnWidth(14)
    private String birthDate;

    @ExcelProperty("学历")
    @ColumnWidth(12)
    private String education;

    @ExcelProperty("毕业院校")
    @ColumnWidth(24)
    private String school;

    @ExcelProperty("专业")
    @ColumnWidth(18)
    private String major;

    @ExcelProperty("职务")
    @ColumnWidth(20)
    private String position;

    @ExcelProperty("职称")
    @ColumnWidth(16)
    private String professionalTitle;

    @ExcelProperty("证书")
    @ColumnWidth(28)
    private String certificates;

    @ExcelProperty("入职时间")
    @ColumnWidth(14)
    private String entryDate;

    @ExcelProperty("身份证号")
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty("电话")
    @ColumnWidth(16)
    private String phone;

    @ExcelProperty("户籍所在地")
    @ColumnWidth(28)
    private String domicile;

    @ExcelProperty("居住地")
    @ColumnWidth(28)
    private String address;

    @ExcelProperty("邮箱")
    @ColumnWidth(22)
    private String email;

    @ExcelProperty("合同期限")
    @ColumnWidth(14)
    private String contractEndDate;

    @ExcelProperty("入社保时间")
    @ColumnWidth(14)
    private String socialInsuranceDate;

    /** "在职" / "离职" */
    @ExcelProperty("在职/离职")
    @ColumnWidth(10)
    private String employmentStatus;

    @ExcelProperty("离职时间")
    @ColumnWidth(14)
    private String resignDate;

    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}
