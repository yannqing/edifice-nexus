package com.qsy.edifice.domain.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户参考表 Excel 数据模型（模板辅助 Sheet）
 * 列出系统中所有用户，供导入时查找手机号/邮箱填写
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReferenceData {

    @ExcelProperty("姓名")
    @ColumnWidth(15)
    private String realName;

    @ExcelProperty("手机号")
    @ColumnWidth(18)
    private String phone;

    @ExcelProperty("邮箱")
    @ColumnWidth(25)
    private String email;
}
