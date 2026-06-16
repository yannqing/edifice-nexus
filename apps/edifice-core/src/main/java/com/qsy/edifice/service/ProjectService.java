package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.GetAllProjectListDto;
import com.qsy.edifice.domain.dto.GetMyProjectListDto;
import com.qsy.edifice.domain.dto.GetProjectArchiveListDto;
import com.qsy.edifice.domain.dto.UpdateProjectDto;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.vo.ProjectDetailVo;
import com.qsy.edifice.domain.vo.ProjectArchiveVo;
import com.qsy.edifice.domain.vo.ProjectArchiveDetailVo;
import com.qsy.edifice.domain.vo.ProjectListVo;
import com.qsy.edifice.domain.vo.ProjectLifecycleVo;
import com.qsy.edifice.domain.vo.ProjectStatisticsVo;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
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
     * 创建项目（含合同、阶段、成员）
     * @param dto 创建项目请求参数
     * @param userId 当前登录用户id
     * @return 项目id
     */
    Long createProject(CreateProjectDto dto, Long userId);

    /**
     * 更新项目
     * @param project 项目信息
     * @return 是否成功
     */
    boolean updateProject(Project project);

    /**
     * 更新项目（含合同、成员）
     * @param dto 更新参数
     */
    void updateProjectFull(UpdateProjectDto dto);

    /**
     * 删除项目
     * @param projectId 项目id
     * @return 是否成功
     */
    boolean deleteProject(Long projectId);

    /**
     * 分页查询全部项目列表
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<ProjectListVo> getAllProjectPage(GetAllProjectListDto dto);

    /**
     * 分页查询项目生命周期看板可选项目
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<ProjectListVo> getLifecycleProjectPage(GetAllProjectListDto dto, Long userId, boolean canViewAll);

    /**
     * 分页查询本人参与的项目列表
     * @param userId 用户id
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<ProjectListVo> getMyProjectPage(Long userId, GetMyProjectListDto dto);

    /**
     * 根据项目id查询项目详情
     * @param projectId 项目id
     * @return 项目详情
     */
    ProjectDetailVo getProjectDetailById(Long projectId);

    /**
     * 查询项目生命周期看板详情
     * @param projectId 项目id
     * @return 生命周期聚合详情
     */
    ProjectLifecycleVo getProjectLifecycleDetail(Long projectId, Long userId, boolean canViewAll);

    /**
     * 检查项目是否存在
     * @param projectId 项目id
     * @return 项目是否存在
     */
    boolean checkProjectExists(Long projectId);

    /**
     * 获取项目统计信息（全部项目）
     * @return 统计信息
     */
    ProjectStatisticsVo getProjectStatistics();

    /**
     * 获取我参与的项目统计信息
     * @param userId 当前用户id
     * @return 统计信息
     */
    ProjectStatisticsVo getMyProjectStatistics(Long userId);

    /**
     * 查询可归档项目
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<ProjectArchiveVo> getArchivableProjectPage(GetProjectArchiveListDto dto);

    /**
     * 查询已归档项目
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<ProjectArchiveVo> getArchivedProjectPage(GetProjectArchiveListDto dto);

    /**
     * 查询项目归档详情
     * @param projectId 项目id
     * @return 归档详情
     */
    ProjectArchiveDetailVo getProjectArchiveDetail(Long projectId);

    /**
     * 下载项目归档资料包
     * @param projectId 项目id
     * @param response HTTP 响应
     */
    void exportProjectArchivePackage(Long projectId, HttpServletResponse response) throws IOException;

    /**
     * 归档项目
     * @param projectId 项目id
     */
    void archiveProject(Long projectId, Long operatorId, String archiveRemark);

    /**
     * 取消归档
     * @param projectId 项目id
     */
    void unarchiveProject(Long projectId);

    /**
     * 检查项目是否已归档，归档项目禁止新增业务操作
     * @param projectId 项目id
     */
    void ensureProjectNotArchived(Long projectId);
}
