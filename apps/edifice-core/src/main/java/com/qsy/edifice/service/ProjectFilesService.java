package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectFiles;

import java.util.List;

/**
 * 项目文件服务接口
 */
public interface ProjectFilesService {

    /**
     * 根据项目文件id查询项目文件
     * @param projectFileId 项目文件id
     * @return 项目文件信息
     */
    ProjectFiles getProjectFilesById(Long projectFileId);

    /**
     * 根据项目id查询项目文件列表
     * @param projectId 项目id
     * @return 项目文件列表
     */
    List<ProjectFiles> getProjectFilesByProjectId(String projectId);

    /**
     * 根据项目阶段id查询项目文件列表
     * @param projectStageId 项目阶段id
     * @return 项目文件列表
     */
    List<ProjectFiles> getProjectFilesByProjectStageId(Long projectStageId);

    /**
     * 分页查询项目文件列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<ProjectFiles> getProjectFilesPage(Integer current, Integer pageSize);

    /**
     * 保存项目文件
     * @param projectFiles 项目文件信息
     * @return 是否成功
     */
    boolean saveProjectFiles(ProjectFiles projectFiles);

    /**
     * 更新项目文件
     * @param projectFiles 项目文件信息
     * @return 是否成功
     */
    boolean updateProjectFiles(ProjectFiles projectFiles);

    /**
     * 删除项目文件
     * @param projectFileId 项目文件id
     * @return 是否成功
     */
    boolean deleteProjectFiles(Long projectFileId);
}
