package com.qsy.edifice.domain.dto;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ProjectMemberDto {



    /**
     * 用户ID（必须）
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 项目内角色ID（如：1-项目经理, 2-开发, 3-测试等）
     */
    @NotNull(message = "项目角色ID不能为空")
    private Long projectRole;



}
