package com.qsy.edifice.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
@Data
public class ProjectCreateDto {



        @NotBlank(message = "项目名称不能为空")
        private String projectName;

        @NotBlank(message = "项目编号不能为空")
        private String projectCode;

        @NotNull(message = "项目类型不能为空")
        private Long projectType;

        private Integer projectStatus = 0; // 默认：未开始

        private Integer isShow = 1; // 默认：公开

        private LocalDateTime projectStartTime;

        private LocalDateTime projectEndTime;

        // 可选：合同列表
        private List<ContractDTO> contracts;

        // 可选：阶段列表（若不传，使用默认模板）
        @Valid
        private List<StageDto> stages;

        // 其他成员用户ID（不包括创建者，创建者由后端自动加入）
        @NotEmpty(message = "至少需要一名项目成员")
        @Valid
        private List<ProjectMemberDto> members;

        // 合同可为空（但通常建议至少一个）

        @NotEmpty(message = "阶段")
        @Valid
        private List<ProjectStageDto> projectStageDtos;
    }


