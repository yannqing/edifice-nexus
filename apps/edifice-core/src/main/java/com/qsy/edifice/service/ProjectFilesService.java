package com.qsy.edifice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateProjectFileDto;
import com.qsy.edifice.domain.entity.ProjectFiles;
import com.qsy.edifice.domain.vo.ProjectFileVo;

import java.util.List;

/**
 * 项目文件服务接口（Phase 3 扩展三级审批）
 */
public interface ProjectFilesService {

    // ==================== 旧接口（保留） ====================

    ProjectFiles getProjectFilesById(Long projectFileId);

    List<ProjectFiles> getProjectFilesByProjectId(String projectId);

    List<ProjectFiles> getProjectFilesByProjectStageId(Long projectStageId);

    Page<ProjectFiles> getProjectFilesPage(Integer current, Integer pageSize);

    boolean saveProjectFiles(ProjectFiles projectFiles);

    boolean updateProjectFiles(ProjectFiles projectFiles);

    boolean deleteProjectFiles(Long projectFileId);

    // ==================== 三级审批新接口 ====================

    /**
     * 创建项目文件记录并提交审批（一级：项目负责人）。
     *
     * @return 新创建的 project_file_id
     */
    Long createAndSubmit(CreateProjectFileDto dto, Long uploadUserId);

    /**
     * 当前审批人对某条项目文件执行审批（通过 / 驳回）。
     *
     * 业务规则：
     * - {@code dto.recordId} 必须是该项目文件当前待审核节点；
     * - 通过 + nextApproverId 非空：流转下一级；
     * - 通过 + nextApproverId 为空：终审通过，approval_status=2；
     * - 驳回：approval_status=3，链终止。
     */
    void approve(ApproveDto dto, Long operatorId);

    /**
     * 上传人撤销审批中的项目文件。
     */
    void cancel(Long projectFileId, Long operatorId);

    /**
     * 按条件查询项目文件列表。
     *
     * @param projectId      可空
     * @param approvalStatus 可空（0/1/2/3）
     * @param keyword        模糊匹配 description / 分类
     */
    List<ProjectFileVo> listProjectFiles(Long projectId, Integer approvalStatus, String keyword);

    /**
     * 详情 + 审批链
     */
    ProjectFileVo getDetail(Long projectFileId);

    /**
     * 我的待审：当前登录用户的所有"审批中且当前节点 approver = 我"的项目文件
     */
    List<ProjectFileVo> listMyPending(Long userId);
}
