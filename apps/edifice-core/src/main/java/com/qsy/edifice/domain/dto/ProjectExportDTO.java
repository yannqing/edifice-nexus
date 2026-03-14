package com.qsy.edifice.domain.dto;

// ProjectExportDTO.java
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectExportDTO {

    @ExcelProperty("项目ID")
    private Long projectId;

    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("项目编号")
    private String projectCode;

    @ExcelProperty("项目类型ID")
    private Long projectType;

    @ExcelProperty("项目状态")
    private String projectStatusText; // 转为中文

    @ExcelProperty("是否公开")
    private String isShowText; // 转为中文

    @ExcelProperty("开始日期")
    private LocalDateTime projectStartTime;

    @ExcelProperty("结束日期")
    private LocalDateTime projectEndTime;

    @ExcelProperty("创建时间")
    private LocalDateTime createdTime;

    // 不导出 updatedTime / isDelete（通常不需要）
}

