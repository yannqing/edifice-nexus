package com.qsy.edifice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "项目 Excel 导入结果")
public class ProjectImportResultVo {
    private int successCount;
    private int failCount;
    private List<String> errors;
    private String message;
}
