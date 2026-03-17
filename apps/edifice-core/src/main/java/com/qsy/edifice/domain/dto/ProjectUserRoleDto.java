package com.qsy.edifice.domain.dto;

import lombok.Data;

import java.util.List;
@Data
public class ProjectUserRoleDto {
    private Long projectId;      // 项目ID
    private Long userId;         // 用户ID
    private List<Long> roleIds;  // 角色ID列表 ← 关键！
}
