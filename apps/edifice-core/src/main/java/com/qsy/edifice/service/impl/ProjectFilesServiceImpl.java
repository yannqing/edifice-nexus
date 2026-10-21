package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateProjectFileDto;
import com.qsy.edifice.domain.dto.GetProjectFileListDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.dto.SubmitProjectFileBatchDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.domain.vo.ProjectFileVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.ProjectFilesService;
import com.qsy.edifice.service.ProjectMemberService;
import com.qsy.edifice.service.ProjectFileFolderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目文件服务实现类（Phase 3 扩展三级审批）
 */
@Slf4j
@Service
public class ProjectFilesServiceImpl implements ProjectFilesService {

    /** 项目经理角色 id（同 OutputValueServiceImpl 约定） */
    private static final Long ROLE_MANAGER_ID = 101L;

    private static final int APPROVAL_IN_PROGRESS = 1;
    private static final int APPROVAL_APPROVED = 2;
    private static final int APPROVAL_REJECTED = 3;
    private static final int RECORD_REJECTED = 2;

    @Resource
    private ProjectFilesMapper projectFilesMapper;

    @org.springframework.beans.factory.annotation.Value("${project-files.draft-retention-days:30}")
    private int draftRetentionDays = 30;

    @Resource
    private FilesMapper filesMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ApprovalFlowService approvalFlowService;

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private ProjectFileFolderService folderService;

    // ==================== 旧接口 ====================

    @Override
    public ProjectFiles getProjectFilesById(Long projectFileId) {
        return projectFilesMapper.selectById(projectFileId);
    }

    @Override
    public List<ProjectFiles> getProjectFilesByProjectId(String projectId) {
        return projectFilesMapper.selectByProjectId(projectId);
    }

    @Override
    public List<ProjectFiles> getProjectFilesByProjectStageId(Long projectStageId) {
        return projectFilesMapper.selectByProjectStageId(projectStageId);
    }

    @Override
    public Page<ProjectFiles> getProjectFilesPage(Integer current, Integer pageSize) {
        return projectFilesMapper.selectPage(new Page<>(current, pageSize), null);
    }

    @Override
    public boolean saveProjectFiles(ProjectFiles projectFiles) {
        return projectFilesMapper.insert(projectFiles) > 0;
    }

    @Override
    public boolean updateProjectFiles(ProjectFiles projectFiles) {
        return projectFilesMapper.updateById(projectFiles) > 0;
    }

    @Override
    public boolean deleteProjectFiles(Long projectFileId) {
        return projectFilesMapper.deleteById(projectFileId) > 0;
    }

    // ==================== 三级审批 ====================

    @Override
    public int getDraftRetentionDays() {
        return draftRetentionDays == 7 ? 7 : 30;
    }

    private void validateProject(Long projectId, Long userId) {
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException("项目不存在");
        if (Objects.equals(project.getArchiveStatus(), 1)) throw new BusinessException("项目已归档，不能新增文件申请");
        if (!hasProjectFileManageAuthority()
                && projectMemberService.getProjectMemberByProjectIdAndUserId(projectId, userId) == null)
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只有项目成员可以管理文件草稿");
    }

    private void validateStage(Long projectId, Long stageId) {
        if (stageId == null) return;
        ProjectStage stage = projectStageMapper.selectById(stageId);
        if (stage == null || !Objects.equals(stage.getProjectId(), projectId))
            throw new BusinessException("所选阶段不属于当前项目");
    }

    private String bounded(String value, int length, String label) {
        if (value != null && value.length() > length) throw new BusinessException(label + "过长");
        return value == null ? null : value.trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> saveDrafts(List<CreateProjectFileDto> items, Long userId) {
        if (userId == null || items == null || items.isEmpty() || items.size() > 20
                || items.stream().anyMatch(i -> i == null || i.getFileId() == null || i.getProjectId() == null))
            throw new BusinessException("每次保存1到20个文件，项目和文件不能为空");
        if (items.stream().map(i -> i.getFileId() + ":" + (i.getFolderId() == null ? 0 : i.getFolderId())).distinct().count() != items.size())
            throw new BusinessException("文件不能重复");
        Long projectId = items.get(0).getProjectId();
        if (items.stream().anyMatch(i -> !projectId.equals(i.getProjectId()))) throw new BusinessException("只能保存同一项目的文件");
        validateProject(projectId, userId);
        List<String> ids = new ArrayList<>();
        // Consistent lock order also makes a retried save idempotent for the same physical files.
        for (CreateProjectFileDto item : items.stream().sorted(Comparator.comparing(CreateProjectFileDto::getFileId)).toList()) {
            Long folderId = item.getFolderId() == null ? null : folderService.validateFolder(projectId, item.getFolderId());
            Files file = filesMapper.selectOne(new LambdaQueryWrapper<Files>().eq(Files::getFileId, item.getFileId()).last("FOR UPDATE"));
            if (file == null || !Objects.equals(file.getUploadUserId(), userId))
                throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只能保存本人上传的文件");
            validateStage(projectId, item.getProjectStageId());
            String category = bounded(item.getFileCategory(), 64, "文件分类");
            if (StringUtils.hasText(category) && !Set.of("图纸", "合同", "成果文件", "计算底稿", "其他").contains(category))
                throw new BusinessException("不支持的文件分类");
            var existingQuery = new LambdaQueryWrapper<ProjectFiles>()
                    .eq(ProjectFiles::getProjectId, String.valueOf(projectId)).eq(ProjectFiles::getFileId, item.getFileId())
                    .eq(ProjectFiles::getUploadUserId, userId).eq(ProjectFiles::getApprovalStatus, 0)
                    .gt(ProjectFiles::getDraftExpiresAt, LocalDateTime.now()).last("LIMIT 1 FOR UPDATE");
            if (folderId == null) existingQuery.isNull(ProjectFiles::getFolderId);
            else existingQuery.eq(ProjectFiles::getFolderId, folderId);
            ProjectFiles existing = projectFilesMapper.selectOne(existingQuery);
            if (existing != null) { ids.add(String.valueOf(existing.getProjectFileId())); continue; }
            ProjectFiles draft = ProjectFiles.builder().projectId(String.valueOf(projectId))
                    .projectStageId(item.getProjectStageId()).folderId(folderId).fileId(item.getFileId()).uploadUserId(userId)
                    .fileName(bounded(item.getFileName(), 255, "文件名称")).fileCategory(category)
                    .description(bounded(item.getDescription(), 512, "文件说明"))
                    .approvalStatus(0).batchHeader(0).draftExpiresAt(LocalDateTime.now().plusDays(getDraftRetentionDays())).build();
            projectFilesMapper.insert(draft);
            ids.add(String.valueOf(draft.getProjectFileId()));
        }
        return ids;
    }

    @Override
    public Page<ProjectFileVo> listDrafts(Long projectId, String keyword, int current, int pageSize, Long userId) {
        if (userId == null || projectId == null) throw new BusinessException("项目不能为空");
        validateProject(projectId, userId);
        LambdaQueryWrapper<ProjectFiles> query = new LambdaQueryWrapper<ProjectFiles>()
                .eq(ProjectFiles::getProjectId, String.valueOf(projectId)).eq(ProjectFiles::getUploadUserId, userId)
                .eq(ProjectFiles::getApprovalStatus, 0).eq(ProjectFiles::getBatchHeader, 0)
                .gt(ProjectFiles::getDraftExpiresAt, LocalDateTime.now());
        if (StringUtils.hasText(keyword)) query.and(q -> q.like(ProjectFiles::getFileName, keyword.trim())
                .or().like(ProjectFiles::getDescription, keyword.trim()).or().like(ProjectFiles::getFileCategory, keyword.trim()));
        query.orderByAsc(ProjectFiles::getDraftExpiresAt).orderByAsc(ProjectFiles::getProjectFileId);
        int page = Math.max(1, current), size = Math.max(1, Math.min(100, pageSize));
        Page<ProjectFiles> result = projectFilesMapper.selectPage(new Page<>(page, size), query);
        return new Page<ProjectFileVo>(page, size, result.getTotal()).setRecords(toVos(result.getRecords(), false));
    }

    private ProjectFiles ownedDraft(Long id, Long userId) {
        ProjectFiles draft = projectFilesMapper.lock(id);
        if (draft == null || !Objects.equals(draft.getUploadUserId(), userId))
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权操作该草稿");
        if (!Integer.valueOf(0).equals(draft.getApprovalStatus()) || draft.getBatchId() != null
                || draft.getDraftExpiresAt() == null || !draft.getDraftExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessException("草稿已提交、已删除或已过期，请刷新后重试");
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDraft(Long id, Long userId) {
        ownedDraft(id, userId);
        projectFilesMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, CreateProjectFileDto dto, Long userId) {
        ProjectFiles draft = ownedDraft(id, userId);
        if (dto == null || !Objects.equals(dto.getFileId(), draft.getFileId())
                || !Objects.equals(String.valueOf(dto.getProjectId()), draft.getProjectId()))
            throw new BusinessException("不能替换草稿的源文件或所属项目");
        validateProject(dto.getProjectId(), userId);
        validateStage(dto.getProjectId(), dto.getProjectStageId());
        Long folderId = dto.getFolderId() == null ? draft.getFolderId() : folderService.validateFolder(dto.getProjectId(), dto.getFolderId());
        String category = bounded(dto.getFileCategory(), 64, "分类");
        if (StringUtils.hasText(category) && !Set.of("图纸", "合同", "成果文件", "计算底稿", "其他").contains(category))
            throw new BusinessException("不支持的文件分类");
        projectFilesMapper.update(null, new LambdaUpdateWrapper<ProjectFiles>()
                .eq(ProjectFiles::getProjectFileId, id).set(ProjectFiles::getProjectStageId, dto.getProjectStageId())
                .set(ProjectFiles::getFolderId, folderId)
                .set(ProjectFiles::getFileName, bounded(dto.getFileName(), 255, "文件名"))
                .set(ProjectFiles::getFileCategory, category).set(ProjectFiles::getDescription, bounded(dto.getDescription(), 512, "说明"))
                .set(ProjectFiles::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitDrafts(SubmitProjectFileBatchDto dto, Long userId) {
        if (dto == null || dto.getProjectId() == null || dto.getDraftIds() == null || dto.getDraftIds().isEmpty()
                || dto.getDraftIds().size() > 200 || dto.getDraftIds().contains(null)
                || new HashSet<>(dto.getDraftIds()).size() != dto.getDraftIds().size())
            throw new BusinessException("请选择1到200个不重复的草稿");
        validateProject(dto.getProjectId(), userId);
        List<ProjectFiles> drafts = dto.getDraftIds().stream().sorted().map(id -> ownedDraft(id, userId)).toList();
        for (ProjectFiles draft : drafts) {
            if (!String.valueOf(dto.getProjectId()).equals(draft.getProjectId())) throw new BusinessException("不能合并不同项目的文件");
            validateStage(dto.getProjectId(), draft.getProjectStageId());
            if (draft.getFolderId() != null) folderService.validateFolder(dto.getProjectId(), draft.getFolderId());
            if (filesMapper.selectById(draft.getFileId()) == null) throw new BusinessException("草稿的源文件不存在，请重新上传");
        }
        Long firstApprover = dto.getFirstApproverId() == null ? resolveProjectManagerId(dto.getProjectId()) : dto.getFirstApproverId();
        String title = bounded(dto.getTitle(), 255, "申请标题");
        ProjectFiles header = ProjectFiles.builder().projectId(String.valueOf(dto.getProjectId())).uploadUserId(userId)
                .fileName(StringUtils.hasText(title) ? title : "项目文件申请（" + drafts.size() + "个文件）")
                .description(bounded(dto.getDescription(), 512, "申请说明")).approvalStatus(1).batchHeader(1)
                .submittedTime(LocalDateTime.now()).build();
        projectFilesMapper.insert(header);
        ApprovalRecords record = approvalFlowService.submit(new SubmitApprovalDto(ApprovalBizType.FILE.getExt(),
                header.getProjectFileId(), firstApprover, header.getDescription()), userId);
        header.setCurrentRecordId(record.getApprovalRecordId());
        projectFilesMapper.updateById(header);
        for (ProjectFiles draft : drafts) {
            draft.setBatchId(header.getProjectFileId());
            draft.setApprovalStatus(1);
            draft.setCurrentRecordId(record.getApprovalRecordId());
            draft.setSubmittedTime(header.getSubmittedTime());
            projectFilesMapper.updateById(draft);
        }
        log.info("项目文件整批提交 batchId={} count={} userId={}", header.getProjectFileId(), drafts.size(), userId);
        return header.getProjectFileId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> copyToDrafts(Long id, Long userId) {
        ProjectFiles original = projectFilesMapper.lock(id);
        if (original == null || !Objects.equals(original.getUploadUserId(), userId))
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只能复制本人的申请");
        if (!Set.of(3, 4).contains(original.getApprovalStatus())) throw new BusinessException("只有驳回或撤回的申请可以重新保存为草稿");
        List<ProjectFiles> entries = Integer.valueOf(1).equals(original.getBatchHeader())
                ? projectFilesMapper.selectList(new LambdaQueryWrapper<ProjectFiles>().eq(ProjectFiles::getBatchId, id)
                        .orderByAsc(ProjectFiles::getFileId))
                : List.of(original);
        List<String> ids = new ArrayList<>();
        // Keep the rejected application's immutable attachment list and chain intact.
        for (int start = 0; start < entries.size(); start += 20) {
            ids.addAll(saveDrafts(entries.subList(start, Math.min(start + 20, entries.size())).stream()
                    .map(f -> new CreateProjectFileDto(parseProjectIdSafe(f.getProjectId()), f.getProjectStageId(), f.getFileId(),
                            f.getFileName(), f.getFileCategory(), f.getDescription(), null, f.getFolderId())).toList(), userId));
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireDrafts() {
        return projectFilesMapper.update(null, new LambdaUpdateWrapper<ProjectFiles>()
                .eq(ProjectFiles::getApprovalStatus, 0).eq(ProjectFiles::getBatchHeader, 0).isNull(ProjectFiles::getBatchId)
                .le(ProjectFiles::getDraftExpiresAt, LocalDateTime.now())
                .set(ProjectFiles::getApprovalStatus, 5).set(ProjectFiles::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndSubmit(CreateProjectFileDto dto, Long uploadUserId) {
        List<String> ids = saveDrafts(Collections.singletonList(dto), uploadUserId);
        SubmitProjectFileBatchDto batch = new SubmitProjectFileBatchDto();
        batch.setProjectId(dto.getProjectId());
        batch.setDraftIds(ids.stream().map(Long::valueOf).toList());
        batch.setFirstApproverId(dto.getFirstApproverId());
        batch.setDescription(dto.getDescription());
        return submitDrafts(batch, uploadUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(ApproveDto dto, Long operatorId) {
        if (dto.getRecordId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批记录id不能为空");
        }

        ApprovalRecords pending = approvalRecordsMapper.selectById(dto.getRecordId());
        if (pending == null || !(ApprovalBizType.FILE.getExt().equals(pending.getBizTypeExt())
                || (pending.getBizTypeExt() == null && Objects.equals(pending.getApprovalRecordType(), ApprovalBizType.FILE.getCode()))))
            throw new BusinessException("此审批记录不属于项目文件");
        ProjectFiles locked = projectFilesMapper.lock(pending.getInspectionFormId());
        if (locked == null || !Integer.valueOf(1).equals(locked.getApprovalStatus())
                || !Objects.equals(locked.getCurrentRecordId(), dto.getRecordId()))
            throw new BusinessException("该文件申请已被处理，请刷新后重试");
        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(dto, operatorId);
        if (result.bizType != ApprovalBizType.FILE) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "此审批记录不属于项目文件");
        }

        ProjectFiles entity = locked;
        if (entity == null) {
            throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        }

        if (result.rejected) {
            entity.setApprovalStatus(APPROVAL_REJECTED);
            entity.setCurrentRecordId(null);
        } else if (result.isFinal) {
            entity.setApprovalStatus(APPROVAL_APPROVED);
            entity.setCurrentRecordId(null);
        } else {
            entity.setApprovalStatus(APPROVAL_IN_PROGRESS);
            entity.setCurrentRecordId(result.nextRecordId);
        }
        updateApplication(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long projectFileId, Long operatorId) {
        if (projectFileId == null || operatorId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目文件 / 操作人不能为空");
        }

        ProjectFiles entity = projectFilesMapper.selectById(projectFileId);
        if (entity != null) entity = projectFilesMapper.lock(entity.getBatchId() == null ? projectFileId : entity.getBatchId());
        if (entity == null) {
            throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        }
        if (!operatorId.equals(entity.getUploadUserId())) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只有上传人可以撤销该文件");
        }
        if (!Objects.equals(entity.getApprovalStatus(), APPROVAL_IN_PROGRESS)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "只有审批中的项目文件可以撤销");
        }

        ApprovalRecords pending = approvalFlowService.getCurrentPending(ApprovalBizType.FILE, entity.getProjectFileId());
        if (pending == null && Integer.valueOf(1).equals(entity.getBatchHeader()))
            throw new BusinessException("当前审批节点不存在，请刷新后重试");
        if (pending != null) {
            pending.setInspectionFormStatus(RECORD_REJECTED);
            pending.setApprovalDescription("上传人撤销");
            pending.setNextApproverId(null);
            pending.setUpdatedTime(LocalDateTime.now());
            if (approvalRecordsMapper.updatePendingResult(pending) != 1) {
                throw new BusinessException(ErrorType.OPERATION_FAILED, "该文件审批已被处理，无法撤销");
            }
        }

        entity.setApprovalStatus(Integer.valueOf(1).equals(entity.getBatchHeader()) ? 4 : APPROVAL_REJECTED);
        entity.setCurrentRecordId(null);
        updateApplication(entity);
        if (!Integer.valueOf(1).equals(entity.getBatchHeader())) projectFilesMapper.deleteById(entity.getProjectFileId());
    }

    private void updateApplication(ProjectFiles entity) {
        projectFilesMapper.update(null, new LambdaUpdateWrapper<ProjectFiles>()
                .and(w -> w.eq(ProjectFiles::getProjectFileId, entity.getProjectFileId())
                        .or().eq(ProjectFiles::getBatchId, entity.getProjectFileId()))
                .set(ProjectFiles::getApprovalStatus, entity.getApprovalStatus())
                .set(ProjectFiles::getCurrentRecordId, entity.getCurrentRecordId())
                .set(ProjectFiles::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public List<ProjectFileVo> listProjectFiles(Long projectId, Integer approvalStatus, String keyword) {
        LambdaQueryWrapper<ProjectFiles> w = new LambdaQueryWrapper<ProjectFiles>()
                .eq(ProjectFiles::getBatchHeader, 0)
                .and(q -> q.in(ProjectFiles::getApprovalStatus, 1, 2, 3).or()
                        .eq(ProjectFiles::getApprovalStatus, 0).isNull(ProjectFiles::getDraftExpiresAt));
        if (projectId != null) {
            w.eq(ProjectFiles::getProjectId, String.valueOf(projectId));
        }
        if (approvalStatus != null) {
            w.eq(ProjectFiles::getApprovalStatus, approvalStatus);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(ww -> ww.like(ProjectFiles::getDescription, keyword)
                    .or().like(ProjectFiles::getFileCategory, keyword));
        }
        w.orderByDesc(ProjectFiles::getCreatedTime);
        List<ProjectFiles> list = projectFilesMapper.selectList(w);
        return toVos(list, false);
    }

    @Override
    public Page<ProjectFileVo> listProjectFilesPage(GetProjectFileListDto dto, Long userId, boolean canViewAll) {
        int current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? Math.min(100, dto.getPageSize()) : 10;

        LambdaQueryWrapper<ProjectFiles> w = new LambdaQueryWrapper<>();
        w.and(q -> q.in(ProjectFiles::getApprovalStatus, 1, 2, 3, 4).or()
                .eq(ProjectFiles::getApprovalStatus, 0).isNull(ProjectFiles::getDraftExpiresAt));
        if (Boolean.TRUE.equals(dto.getApplicationsOnly())) w.isNull(ProjectFiles::getBatchId);
        else w.eq(ProjectFiles::getBatchHeader, 0).ne(ProjectFiles::getApprovalStatus, 4);
        if (dto.getProjectId() != null) {
            w.eq(ProjectFiles::getProjectId, String.valueOf(dto.getProjectId()));
        }
        if (dto.getFolderId() != null) {
            if (dto.getProjectId() == null) throw new BusinessException("按文件夹查询时必须指定项目");
            Long folderId = folderService.validateFolder(dto.getProjectId(), dto.getFolderId());
            if (folderId == null) w.isNull(ProjectFiles::getFolderId);
            else w.eq(ProjectFiles::getFolderId, folderId);
        }
        if (dto.getApprovalStatus() != null) {
            w.eq(ProjectFiles::getApprovalStatus, dto.getApprovalStatus());
        }
        if (StringUtils.hasText(dto.getFileCategory())) {
            w.and(q -> q.eq(ProjectFiles::getFileCategory, dto.getFileCategory().trim()).or().apply(
                    "EXISTS (SELECT 1 FROM project_files child WHERE child.batch_id = project_files.project_file_id "
                            + "AND child.is_delete = 0 AND child.file_category = {0})", dto.getFileCategory().trim()));
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String kw = dto.getKeyword().trim();
            w.and(ww -> ww.like(ProjectFiles::getDescription, kw)
                    .or().like(ProjectFiles::getFileCategory, kw).or().like(ProjectFiles::getFileName, kw)
                    .or().apply("EXISTS (SELECT 1 FROM project_files child WHERE child.batch_id = project_files.project_file_id "
                            + "AND child.is_delete = 0 AND (child.file_name LIKE {0} OR child.description LIKE {0}))", "%" + kw + "%"));
        }

        // 权限下推到 SQL：非 canViewAll 的用户只能看到「本人上传」或「本人所在项目」的文件
        // 注意 ProjectFiles.project_id 是 varchar，ProjectMember.project_id 是 bigint，需要 CAST
        if (!canViewAll && userId != null) {
            String uid = String.valueOf(userId);
            w.and(ww -> ww.eq(ProjectFiles::getUploadUserId, userId)
                    .or().apply(
                            "project_id IN (SELECT CAST(project_id AS CHAR) FROM project_member "
                                    + "WHERE user_id = {0} AND is_delete = 0)",
                            uid).or().apply("EXISTS (SELECT 1 FROM approval_records ar WHERE ar.inspection_form_id = "
                                    + "COALESCE(project_files.batch_id, project_files.project_file_id) AND ar.biz_type_ext = 'file' "
                                    + "AND ar.is_delete = 0 AND ar.approver = {0})", userId));
        }

        // 「我上传的」tab：只看 upload_user_id = 当前用户，覆盖权限过滤
        if (Boolean.TRUE.equals(dto.getMineOnly()) && userId != null) {
            w.eq(ProjectFiles::getUploadUserId, userId);
        }

        w.orderByDesc(ProjectFiles::getCreatedTime);

        Page<ProjectFiles> entityPage = projectFilesMapper.selectPage(new Page<>(current, pageSize), w);
        List<ProjectFileVo> voRecords = toVos(entityPage.getRecords(), false);
        return new Page<ProjectFileVo>(current, pageSize, entityPage.getTotal()).setRecords(voRecords);
    }

    @Override
    public Map<String, Long> getStatistics(Long userId, boolean canViewAll) {
        // 与 listProjectFilesPage 相同的权限过滤，但不分页、不按状态过滤
        LambdaQueryWrapper<ProjectFiles> w = new LambdaQueryWrapper<ProjectFiles>()
                .isNull(ProjectFiles::getBatchId)
                .and(q -> q.in(ProjectFiles::getApprovalStatus, 1, 2, 3, 4).or()
                        .eq(ProjectFiles::getApprovalStatus, 0).isNull(ProjectFiles::getDraftExpiresAt));
        if (!canViewAll && userId != null) {
            String uid = String.valueOf(userId);
            w.and(ww -> ww.eq(ProjectFiles::getUploadUserId, userId)
                    .or().apply(
                            "project_id IN (SELECT CAST(project_id AS CHAR) FROM project_member "
                                    + "WHERE user_id = {0} AND is_delete = 0)",
                            uid).or().apply("EXISTS (SELECT 1 FROM approval_records ar WHERE ar.inspection_form_id = "
                                    + "project_files.project_file_id AND ar.biz_type_ext = 'file' "
                                    + "AND ar.is_delete = 0 AND ar.approver = {0})", userId));
        }
        List<ProjectFiles> all = projectFilesMapper.selectList(w);
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", (long) all.size());
        stats.put("pending", all.stream().filter(f -> Integer.valueOf(1).equals(f.getApprovalStatus())).count());
        stats.put("approved", all.stream().filter(f -> Integer.valueOf(2).equals(f.getApprovalStatus())).count());
        stats.put("rejected", all.stream().filter(f -> Integer.valueOf(3).equals(f.getApprovalStatus())).count());
        return stats;
    }

    @Override
    public List<ProjectFileVo> listProjectFiles(Long projectId, Integer approvalStatus, String keyword, Long userId, boolean canViewAll) {
        List<ProjectFileVo> list = listProjectFiles(projectId, approvalStatus, keyword);
        if (canViewAll) {
            return list;
        }
        if (userId == null) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(item -> canViewProjectFile(item, userId))
                .collect(Collectors.toList());
    }

    @Override
    public ProjectFileVo getDetail(Long projectFileId) {
        ProjectFiles entity = projectFilesMapper.selectById(projectFileId);
        if (entity == null) throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        if (entity.getBatchId() != null) return getDetail(entity.getBatchId());
        return toVos(Collections.singletonList(entity), true).get(0);
    }

    @Override
    public ProjectFileVo getDetail(Long projectFileId, Long userId, boolean canViewAll) {
        ProjectFiles entity = projectFilesMapper.selectById(projectFileId);
        if (entity == null) throw new BusinessException(ErrorType.FILE_NOT_FOUND, "项目文件不存在");
        if ((Integer.valueOf(0).equals(entity.getApprovalStatus()) && entity.getDraftExpiresAt() != null)
                || Integer.valueOf(5).equals(entity.getApprovalStatus())) {
            if (!Objects.equals(entity.getUploadUserId(), userId) || entity.getDraftExpiresAt() == null
                    || !entity.getDraftExpiresAt().isAfter(LocalDateTime.now()) || !Integer.valueOf(0).equals(entity.getApprovalStatus()))
                throw new BusinessException(ErrorType.NO_AUTH_ERROR, "草稿不可见或已过期");
        }
        if (entity.getBatchId() != null) return getDetail(entity.getBatchId(), userId, canViewAll);
        ProjectFileVo vo = toVos(Collections.singletonList(entity), true).get(0);
        if (!canViewAll && !canViewProjectFile(vo, userId)) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权查看该项目文件");
        }
        return vo;
    }

    @Override
    public List<ProjectFileVo> listMyPending(Long userId) {
        if (userId == null) return Collections.emptyList();
        // 拿到我的所有 pending 审批记录（biz_type=file）
        List<ApprovalRecordVo> myPending = approvalFlowService.listPendingByApprover(userId, ApprovalBizType.FILE);
        if (myPending.isEmpty()) return Collections.emptyList();
        Set<Long> fileIds = myPending.stream()
                .map(ApprovalRecordVo::getBizId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (fileIds.isEmpty()) return Collections.emptyList();
        List<ProjectFiles> list = projectFilesMapper.selectBatchIds(fileIds);
        return toVos(list, false);
    }

    // ==================== helpers ====================

    /** 从项目成员里查 ROLE_MANAGER_ID 的用户（没有则 null） */
    private Long resolveProjectManagerId(Long projectId) {
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(projectId);
        if (members == null || members.isEmpty()) return null;
        return members.stream()
                .filter(m -> ROLE_MANAGER_ID.equals(m.getProjectRole()))
                .map(ProjectMember::getUserId)
                .findFirst()
                .orElse(null);
    }

    private boolean canViewProjectFile(ProjectFileVo file, Long userId) {
        if (file == null || userId == null) {
            return false;
        }
        if (Objects.equals(file.getUploadUserId(), userId)) {
            return true;
        }
        if (file.getProjectId() != null
                && projectMemberService.getProjectMemberByProjectIdAndUserId(file.getProjectId(), userId) != null) {
            return true;
        }
        return (file.getApprovalChain() == null ? Collections.<ApprovalRecordVo>emptyList() : file.getApprovalChain())
                .stream()
                .anyMatch(record -> Objects.equals(record.getApprover(), userId));
    }

    private boolean hasProjectFileManageAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "menu:project-files-approval".equals(authority.getAuthority())
                        || "menu:all-projects".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    private List<ProjectFileVo> toVos(List<ProjectFiles> list, boolean withChain) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Set<Long> folderIds = list.stream().map(ProjectFiles::getFolderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> folderPaths = folderIds.isEmpty() ? Map.of() : folderService.pathsFor(folderIds);

        Set<Long> headerIds = list.stream().filter(e -> Integer.valueOf(1).equals(e.getBatchHeader()))
                .map(ProjectFiles::getProjectFileId).collect(Collectors.toSet());
        Map<Long, List<ProjectFiles>> children = headerIds.isEmpty() ? Collections.emptyMap()
                : projectFilesMapper.selectList(new LambdaQueryWrapper<ProjectFiles>().in(ProjectFiles::getBatchId, headerIds)
                        .orderByAsc(ProjectFiles::getCreatedTime).orderByAsc(ProjectFiles::getProjectFileId))
                    .stream().collect(Collectors.groupingBy(ProjectFiles::getBatchId));

        Set<Long> fileIds = list.stream().map(ProjectFiles::getFileId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> stageIds = list.stream().map(ProjectFiles::getProjectStageId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> projectIds = list.stream().map(ProjectFiles::getProjectId)
                .filter(Objects::nonNull)
                .map(this::parseProjectIdSafe)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Files> fileMap = fileIds.isEmpty() ? Collections.emptyMap()
                : filesMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(Files::getFileId, f -> f, (a, b) -> a));
        Map<Long, ProjectStage> stageMap = stageIds.isEmpty() ? Collections.emptyMap()
                : projectStageMapper.selectBatchIds(stageIds).stream()
                .collect(Collectors.toMap(ProjectStage::getProjectStageId, s -> s, (a, b) -> a));
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));
        Set<Long> uploaderIds = list.stream().map(ProjectFiles::getUploadUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> uploaders = uploaderIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(uploaderIds).stream().collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));
        Map<Long, List<ApprovalRecordVo>> chains = new HashMap<>();

        return list.stream().map(e -> {
            Files f = e.getFileId() == null ? null : fileMap.get(e.getFileId());
            ProjectStage s = e.getProjectStageId() == null ? null : stageMap.get(e.getProjectStageId());
            Long projectIdLong = parseProjectIdSafe(e.getProjectId());
            Project p = projectIdLong == null ? null : projectMap.get(projectIdLong);

            ProjectFileVo.ProjectFileVoBuilder b = ProjectFileVo.builder()
                    .projectFileId(e.getProjectFileId())
                    .projectId(projectIdLong)
                    .projectName(p == null ? null : p.getProjectName())
                    .projectCode(p == null ? null : p.getProjectCode())
                    .projectStageId(e.getProjectStageId())
                    .stageName(s == null ? null : s.getStageName())
                    .folderId(e.getFolderId()).folderPath(e.getFolderId() == null ? null : folderPaths.get(e.getFolderId()))
                    .fileId(e.getFileId())
                    .fileName(resolveDisplayName(e, f))
                    .fileUrl(f == null ? null : f.getFileUrl())
                    .fileExtension(f == null ? null : f.getFileExtension())
                    .fileSize(f == null || f.getFileSize() == null ? null : String.valueOf(f.getFileSize()))
                    .thumbnailUrl(f == null ? null : f.getThumbnailUrl())
                    .fileCategory(e.getFileCategory())
                    .description(e.getDescription())
                    .uploadUserId(e.getUploadUserId())
                    .approvalStatus(e.getApprovalStatus())
                    .currentRecordId(e.getCurrentRecordId())
                    .batchHeader(Integer.valueOf(1).equals(e.getBatchHeader()))
                    .batchId(e.getBatchId()).draftExpiresAt(e.getDraftExpiresAt()).submittedTime(e.getSubmittedTime())
                    .batchFileCount(Integer.valueOf(1).equals(e.getBatchHeader())
                            ? children.getOrDefault(e.getProjectFileId(), List.of()).size() : 1)
                    .createdTime(e.getCreatedTime())
                    .updatedTime(e.getUpdatedTime());

            if (e.getUploadUserId() != null) {
                SysUser u = uploaders.get(e.getUploadUserId());
                if (u != null) b.uploadUserName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }

            // 审批链 + 当前审批人
            List<ApprovalRecordVo> chain = Integer.valueOf(0).equals(e.getApprovalStatus()) || Integer.valueOf(5).equals(e.getApprovalStatus())
                    ? List.of() : chains.computeIfAbsent(e.getBatchId() == null ? e.getProjectFileId() : e.getBatchId(),
                            id -> approvalFlowService.queryChain(ApprovalBizType.FILE, id));
            if (e.getCurrentRecordId() != null) {
                chain.stream()
                        .filter(v -> e.getCurrentRecordId().equals(v.getApprovalRecordId()))
                        .findFirst()
                        .ifPresent(v -> {
                            b.currentApproverId(v.getApprover());
                            b.currentApproverName(v.getApproverName());
                        });
            }
            if (withChain) b.approvalChain(chain);
            if (withChain && Integer.valueOf(1).equals(e.getBatchHeader()))
                b.batchFiles(toVos(children.getOrDefault(e.getProjectFileId(), List.of()), false));

            return b.build();
        }).collect(Collectors.toList());
    }

    private Long parseProjectIdSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 展示名优先级：用户填写的 project_files.file_name →
     * 物理文件 files.display_name → 物理文件 files.file_name。
     */
    private String resolveDisplayName(ProjectFiles pf, Files f) {
        if (pf != null && pf.getFileName() != null && !pf.getFileName().isBlank()) {
            return pf.getFileName();
        }
        if (f == null) return null;
        if (f.getDisplayName() != null) return f.getDisplayName();
        return f.getFileName();
    }
}
