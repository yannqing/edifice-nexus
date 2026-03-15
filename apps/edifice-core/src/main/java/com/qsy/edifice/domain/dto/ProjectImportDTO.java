package com.qsy.edifice.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ProjectImportDTO {
    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("项目编号")
    private String projectCode;

    @ExcelProperty("项目类型ID")
    private Long projectType;

    @ExcelProperty("项目状态")
    private Integer projectStatus; // 0-4

    @ExcelProperty("是否公开")
    private Integer isShow; // 0 or 1

    @ExcelProperty("开始日期")
    private LocalDateTime projectStartTime;

    @ExcelProperty("结束日期")
    private LocalDateTime projectEndTime;
}
