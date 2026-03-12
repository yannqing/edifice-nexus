package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.Project;

import java.util.List;

/**
 * 项目服务接口
 */
public interface ProjectService {

    /**
     * 根据项目id查询项目
     * @param projectId 项目id
     * @return 项目信息
     */
    Project getProjectById(Long projectId);

    /**
     * 根据项目编码查询项目
     * @param projectCode 项目编码
     * @return 项目信息
     */
    Project getProjectByCode(String projectCode);

    /**
     * 根据项目状态查询项目列表
     * @param projectStatus 项目状态
     * @return 项目列表
     */
    List<Project> getProjectsByStatus(Integer projectStatus);

    /**
     * 根据项目类型查询项目列表
     * @param projectType 项目类型
     * @return 项目列表
     */
    List<Project> getProjectsByType(Long projectType);

    /**
     * 分页查询项目列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<Project> getProjectPage(Integer current, Integer pageSize);

    /**
     * 保存项目
     * @param project 项目信息
     * @return 是否成功
     */
    boolean saveProject(Project project);

    /**
     * 更新项目
     * @param project 项目信息
     * @return 是否成功
     */
    boolean updateProject(Project project);

    /**
     * 删除项目
     * @param projectId 项目id
     * @return 是否成功
     */
    boolean deleteProject(Long projectId);
}
