package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "已上传的项目 Excel 及合同主文件")
public class ImportProjectDto {
    private Long fileId;
    private List<Long> contractFileIds;
}
