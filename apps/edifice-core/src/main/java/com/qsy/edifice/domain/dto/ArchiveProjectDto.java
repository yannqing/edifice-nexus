package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目归档操作 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArchiveProjectDto {

    @Schema(description = "归档备注")
    private String archiveRemark;
}
