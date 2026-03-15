package com.qsy.edifice.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
@Data
public class ProjectStageDto {
    /**
     * 阶段名称（如：需求分析、开发、测试）
     */
    @NotBlank(message = "阶段名称不能为空")
    private String stageName;

    /**
     * 阶段产值比例（0.00 ~ 100.00，总和应为 100）
     */
    @NotNull(message = "阶段产值比例不能为空")
    private BigDecimal stageOutput;

}
