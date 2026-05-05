package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建项目文件并发起审批（Phase 3 #2）
 *
 * 上传人先通过 /file/upload/* 上传得到 fileId，再调用 /project-files/create 把
 * 文件归档到项目 + 提交到审批链。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建项目文件并发起审批")
public class CreateProjectFileDto {

    @Schema(description = "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Schema(description = "项目阶段id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectStageId;

    @Schema(description = "文件id（来自 /file/upload）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fileId;

    @Schema(description = "用户填写的文件名称（展示名）")
    private String fileName;

    @Schema(description = "文件分类：图纸/合同/报告/其他")
    private String fileCategory;

    @Schema(description = "文件说明")
    private String description;

    /** 项目负责人作为一级审批人；如果前端不传，后端会尝试从项目成员里查 ROLE_MANAGER */
    @Schema(description = "一级审批人（项目负责人）id")
    private Long firstApproverId;
}
