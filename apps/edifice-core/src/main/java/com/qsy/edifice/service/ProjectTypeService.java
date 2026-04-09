package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.entity.ProjectType;

import java.util.List;

/**
 * 项目类型服务接口
 */
public interface ProjectTypeService {

    /**
     * 根据项目类型id查询项目类型
     * @param projectTypeId 项目类型id
     * @return 项目类型信息
     */
    ProjectType getProjectTypeById(Long projectTypeId);

    /**
     * 根据项目类型编码查询项目类型
     * @param projectTypeCode 项目类型编码
     * @return 项目类型信息
     */
    ProjectType getProjectTypeByCode(String projectTypeCode);

    /**
     * 查询所有启用的项目类型
     * @return 项目类型列表
     */
    List<ProjectType> getAllEnabledProjectTypes();

    /**
     * 分页查询项目类型列表
     * @param current 当前页
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<ProjectType> getProjectTypePage(Integer current, Integer pageSize);

    /**
     * 保存项目类型
     * @param projectType 项目类型信息
     * @return 是否成功
     */
    boolean saveProjectType(ProjectType projectType);

    /**
     * 更新项目类型
     * @param projectType 项目类型信息
     * @return 是否成功
     */
    boolean updateProjectType(ProjectType projectType);

    /**
     * 删除项目类型
     * @param projectTypeId 项目类型id
     * @return 是否成功
     */
    boolean deleteProjectType(Long projectTypeId);
}
