package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.qsy.edifice.domain.dto.GetMessageCenterListDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.MessageCenterItemVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.MessageCenterService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MessageCenterServiceImpl implements MessageCenterService {

    private static final String SOURCE_ANNOUNCEMENT = "announcement";
    private static final String SOURCE_APPROVAL = "approval";
    private static final String SOURCE_APPROVAL_RESULT = "approval_result";
    private static final String SOURCE_APPROVAL_DONE = "approval_done";
    private static final String SOURCE_APPROVAL_URGE = "approval_urge";
    private static final String SOURCE_PROJECT_MEMBER = "project_member";
    private static final String SOURCE_PROJECT_ARCHIVE = "project_archive";

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Resource
    private ApprovalUrgeMapper approvalUrgeMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private AnnouncementMapper announcementMapper;

    @Resource
    private UserMessageReadMapper userMessageReadMapper;

    @Resource
    private InspectionFormMapper inspectionFormMapper;

    @Resource
    private ProjectFilesMapper projectFilesMapper;

    @Resource
    private BidMapper bidMapper;

    @Resource
    private ProjectAcceptanceMapper projectAcceptanceMapper;

    @Resource
    private OaApplicationMapper oaApplicationMapper;

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private TimesheetMapper timesheetMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectMemberMapper projectMemberMapper;

    @Override
    public Page<MessageCenterItemVo> list(Long userId, GetMessageCenterListDto dto) {
        List<MessageCenterItemVo> all = buildMessages(userId);
        if (StringUtils.hasText(dto.getCategory())) {
            all = all.stream().filter(item -> dto.getCategory().equals(item.getCategory())).toList();
        }
        if (Boolean.TRUE.equals(dto.getUnreadOnly())) {
            all = all.stream().filter(item -> !Boolean.TRUE.equals(item.getRead())).toList();
        }

        int current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? Math.min(dto.getPageSize(), 100) : 10;
        int from = Math.min((current - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        Page<MessageCenterItemVo> page = new Page<>(current, pageSize, all.size());
        page.setRecords(all.subList(from, to));
        return page;
    }

    @Override
    public long unreadCount(Long userId) {
        Set<String> readKeys = readKeys(userId);
        return visibleSources(userId).stream().filter(source -> !readKeys.contains(source.key())).count();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, String sourceType, Long sourceId) {
        if (userId == null || !StringUtils.hasText(sourceType) || sourceId == null) return;
        boolean visible = visibleSources(userId).stream()
                .anyMatch(source -> sourceType.equals(source.sourceType()) && sourceId.equals(source.sourceId()));
        if (!visible) return;
        insertRead(userId, sourceType, sourceId);
    }

    private void insertRead(Long userId, String sourceType, Long sourceId) {
        userMessageReadMapper.insertIgnore(UserMessageRead.builder()
                .id(IdWorker.getId())
                .userId(userId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .readTime(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        Set<String> readKeys = readKeys(userId);
        List<UserMessageRead> rows = visibleSources(userId).stream()
                .filter(source -> !readKeys.contains(source.key()))
                .map(source -> UserMessageRead.builder()
                        .id(IdWorker.getId())
                        .userId(userId)
                        .sourceType(source.sourceType())
                        .sourceId(source.sourceId())
                        .readTime(LocalDateTime.now())
                        .build())
                .toList();
        for (int from = 0; from < rows.size(); from += 500) {
            userMessageReadMapper.insertIgnoreBatch(rows.subList(from, Math.min(from + 500, rows.size())));
        }
    }

    private List<MessageCenterItemVo> buildMessages(Long userId) {
        if (userId == null) return List.of();
        Set<String> readKeys = readKeys(userId);
        List<MessageCenterItemVo> messages = new ArrayList<>();

        List<ApprovalRecords> approvals = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .eq(ApprovalRecords::getInspectionFormStatus, 0)
                .orderByDesc(ApprovalRecords::getCreatedTime));
        List<ApprovalRecords> approvalResults = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApplyUserId, userId)
                .in(ApprovalRecords::getInspectionFormStatus, 1, 2)
                .and(w -> w.isNull(ApprovalRecords::getApprovalDescription)
                        .or()
                        .notIn(ApprovalRecords::getApprovalDescription, "申请人撤回", "上传人撤销"))
                .orderByDesc(ApprovalRecords::getUpdatedTime));
        // 我已审批：我是审批人且已处理的记录
        List<ApprovalRecords> approvalDone = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .in(ApprovalRecords::getInspectionFormStatus, 1, 2)
                .and(w -> w.isNull(ApprovalRecords::getApprovalDescription)
                        .or()
                        .notIn(ApprovalRecords::getApprovalDescription, "申请人撤回", "上传人撤销"))
                .orderByDesc(ApprovalRecords::getUpdatedTime));
        List<ApprovalUrge> urges = approvalUrgeMapper.selectList(new LambdaQueryWrapper<ApprovalUrge>()
                .eq(ApprovalUrge::getToUserId, userId)
                .orderByDesc(ApprovalUrge::getCreatedTime));
        Set<Long> urgedRecordIds = urges.stream()
                .map(ApprovalUrge::getRecordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ApprovalRecords> urgedRecords = urgedRecordIds.isEmpty()
                ? Map.of()
                : approvalRecordsMapper.selectBatchIds(urgedRecordIds).stream()
                .collect(Collectors.toMap(ApprovalRecords::getApprovalRecordId, Function.identity(), (left, right) -> left));
        List<ApprovalRecords> allApprovalRecords = new ArrayList<>(approvals);
        allApprovalRecords.addAll(approvalResults);
        allApprovalRecords.addAll(approvalDone);
        allApprovalRecords.addAll(urgedRecords.values());
        Map<String, String> businessNames = resolveBusinessNames(allApprovalRecords);
        Map<Long, String> urgeFromUserNames = resolveUserNames(urges.stream()
                .map(ApprovalUrge::getFromUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        for (ApprovalRecords record : approvals) {
            String businessName = businessName(record, businessNames);
            String sourceKey = key(SOURCE_APPROVAL, record.getApprovalRecordId());
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("approval")
                    .categoryLabel("待我审批")
                    .title(businessName + "待您审批")
                    .content("第 " + (record.getApprovalLevel() == null ? 1 : record.getApprovalLevel()) + " 级审批等待处理")
                    .link(linkFor(record, true))
                    .priority(1)
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_APPROVAL)
                    .sourceId(record.getApprovalRecordId())
                    .createdTime(record.getCreatedTime())
                    .build());
        }

        for (ApprovalRecords record : approvalResults) {
            boolean approved = Integer.valueOf(1).equals(record.getInspectionFormStatus());
            boolean finalApproved = approved && record.getNextApproverId() == null;
            String businessName = businessName(record, businessNames);
            String sourceKey = key(SOURCE_APPROVAL_RESULT, record.getApprovalRecordId());
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("result")
                    .categoryLabel("审批结果")
                    .title(resultTitle(businessName, approved, finalApproved))
                    .content(resultContent(record, approved, finalApproved))
                    .link(linkFor(record, false))
                    .priority(approved ? 0 : 2)
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_APPROVAL_RESULT)
                    .sourceId(record.getApprovalRecordId())
                    .createdTime(record.getUpdatedTime() != null ? record.getUpdatedTime() : record.getCreatedTime())
                    .build());
        }

        for (ApprovalRecords record : approvalDone) {
            boolean approved = Integer.valueOf(1).equals(record.getInspectionFormStatus());
            boolean finalApproved = approved && record.getNextApproverId() == null;
            String businessName = businessName(record, businessNames);
            String sourceKey = key(SOURCE_APPROVAL_DONE, record.getApprovalRecordId());
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("processed")
                    .categoryLabel("我已审批")
                    .title(doneTitle(businessName, approved, finalApproved))
                    .content(doneContent(record, approved, finalApproved))
                    .link(linkFor(record, false))
                    .priority(approved ? 0 : 2)
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_APPROVAL_DONE)
                    .sourceId(record.getApprovalRecordId())
                    .createdTime(record.getUpdatedTime() != null ? record.getUpdatedTime() : record.getCreatedTime())
                    .build());
        }

        for (ApprovalUrge urge : urges) {
            ApprovalRecords record = urgedRecords.get(urge.getRecordId());
            String businessName = record == null ? "审批事项" : businessName(record, businessNames);
            String fromUserName = urgeFromUserNames.getOrDefault(urge.getFromUserId(), "申请人");
            String sourceKey = key(SOURCE_APPROVAL_URGE, urge.getUrgeId());
            String comment = StringUtils.hasText(urge.getComment()) ? "：" + urge.getComment().trim() : "";
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("approval")
                    .categoryLabel("催办提醒")
                    .title(businessName + "催办提醒")
                    .content(fromUserName + "催您尽快处理审批" + comment)
                    .link(record == null ? "/todo-center" : linkFor(record, true))
                    .priority(2)
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_APPROVAL_URGE)
                    .sourceId(urge.getUrgeId())
                    .createdTime(urge.getCreatedTime())
                    .build());
        }

        List<ProjectMember> projectMembers = projectMemberMapper.selectByUserId(userId);
        Set<Long> memberProjectIds = (projectMembers == null ? Collections.<ProjectMember>emptyList() : projectMembers).stream()
                .map(ProjectMember::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Project> memberProjects = memberProjectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectBatchIds(memberProjectIds).stream()
                .filter(project -> project.getProjectId() != null)
                .collect(Collectors.toMap(Project::getProjectId, Function.identity(), (left, right) -> left));
        for (ProjectMember member : projectMembers == null ? Collections.<ProjectMember>emptyList() : projectMembers) {
            Project project = memberProjects.get(member.getProjectId());
            if (project == null) continue;
            String sourceKey = key(SOURCE_PROJECT_MEMBER, member.getProjectMemberId());
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("project")
                    .categoryLabel("项目动态")
                    .title("您已加入项目「" + project.getProjectName() + "」")
                    .content("项目编号：" + nullableText(project.getProjectCode(), "-") + "，角色：" + projectRoleLabel(member.getProjectRole()))
                    .link("/project-lifecycle?projectId=" + project.getProjectId())
                    .priority(0)
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_PROJECT_MEMBER)
                    .sourceId(member.getProjectMemberId())
                    .createdTime(member.getCreatedTime())
                    .build());
        }
        for (Project project : memberProjects.values()) {
            if (!Objects.equals(project.getArchiveStatus(), 1)) continue;
            String sourceKey = key(SOURCE_PROJECT_ARCHIVE, project.getProjectId());
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("project")
                    .categoryLabel("项目归档")
                    .title("项目「" + project.getProjectName() + "」已归档")
                    .content(StringUtils.hasText(project.getArchiveRemark()) ? project.getArchiveRemark() : "项目生命周期已完成")
                    .link("/project-lifecycle?projectId=" + project.getProjectId())
                    .priority(1)
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_PROJECT_ARCHIVE)
                    .sourceId(project.getProjectId())
                    .createdTime(project.getArchiveTime() == null ? project.getUpdatedTime() : project.getArchiveTime())
                    .build());
        }

        List<Announcement> announcements = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .and(w -> w.isNull(Announcement::getExpireTime).or().ge(Announcement::getExpireTime, LocalDateTime.now()))
                .orderByDesc(Announcement::getPublishTime));
        for (Announcement announcement : announcements) {
            String sourceKey = key(SOURCE_ANNOUNCEMENT, announcement.getAnnouncementId());
            messages.add(MessageCenterItemVo.builder()
                    .messageKey(sourceKey)
                    .category("announcement")
                    .categoryLabel("系统公告")
                    .title(announcement.getTitle())
                    .content(announcement.getContent())
                    .link("/")
                    .priority(announcement.getPriority() == null ? 0 : announcement.getPriority())
                    .read(readKeys.contains(sourceKey))
                    .sourceType(SOURCE_ANNOUNCEMENT)
                    .sourceId(announcement.getAnnouncementId())
                    .createdTime(announcement.getPublishTime() != null ? announcement.getPublishTime() : announcement.getCreatedTime())
                    .build());
        }

        messages.sort(Comparator.comparing(MessageCenterItemVo::getCreatedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return messages;
    }

    private Set<String> readKeys(Long userId) {
        if (userId == null) return Set.of();
        List<UserMessageRead> rows = userMessageReadMapper.selectList(new LambdaQueryWrapper<UserMessageRead>()
                .eq(UserMessageRead::getUserId, userId));
        Set<String> result = new HashSet<>();
        for (UserMessageRead row : rows) {
            result.add(key(row.getSourceType(), row.getSourceId()));
        }
        return result;
    }

    private List<MessageSource> visibleSources(Long userId) {
        if (userId == null) return List.of();
        List<MessageSource> sources = new ArrayList<>();
        approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                        .select(ApprovalRecords::getApprovalRecordId)
                        .eq(ApprovalRecords::getApprover, userId)
                        .eq(ApprovalRecords::getInspectionFormStatus, 0))
                .forEach(record -> sources.add(new MessageSource(SOURCE_APPROVAL, record.getApprovalRecordId())));
        approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                        .select(ApprovalRecords::getApprovalRecordId)
                        .eq(ApprovalRecords::getApplyUserId, userId)
                        .in(ApprovalRecords::getInspectionFormStatus, 1, 2)
                        .and(w -> w.isNull(ApprovalRecords::getApprovalDescription)
                                .or()
                                .notIn(ApprovalRecords::getApprovalDescription, "申请人撤回", "上传人撤销")))
                .forEach(record -> sources.add(new MessageSource(SOURCE_APPROVAL_RESULT, record.getApprovalRecordId())));
        // 我已审批
        approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                        .select(ApprovalRecords::getApprovalRecordId)
                        .eq(ApprovalRecords::getApprover, userId)
                        .in(ApprovalRecords::getInspectionFormStatus, 1, 2)
                        .and(w -> w.isNull(ApprovalRecords::getApprovalDescription)
                                .or()
                                .notIn(ApprovalRecords::getApprovalDescription, "申请人撤回", "上传人撤销")))
                .forEach(record -> sources.add(new MessageSource(SOURCE_APPROVAL_DONE, record.getApprovalRecordId())));
        approvalUrgeMapper.selectList(new LambdaQueryWrapper<ApprovalUrge>()
                        .select(ApprovalUrge::getUrgeId)
                        .eq(ApprovalUrge::getToUserId, userId))
                .forEach(urge -> sources.add(new MessageSource(SOURCE_APPROVAL_URGE, urge.getUrgeId())));
        List<ProjectMember> projectMembers = projectMemberMapper.selectByUserId(userId);
        Set<Long> projectIds = (projectMembers == null ? Collections.<ProjectMember>emptyList() : projectMembers).stream()
                .peek(member -> sources.add(new MessageSource(SOURCE_PROJECT_MEMBER, member.getProjectMemberId())))
                .map(ProjectMember::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!projectIds.isEmpty()) {
            projectMapper.selectBatchIds(projectIds).stream()
                    .filter(project -> Objects.equals(project.getArchiveStatus(), 1))
                    .forEach(project -> sources.add(new MessageSource(SOURCE_PROJECT_ARCHIVE, project.getProjectId())));
        }
        announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .select(Announcement::getAnnouncementId)
                        .eq(Announcement::getStatus, 1)
                        .and(w -> w.isNull(Announcement::getExpireTime)
                                .or()
                                .ge(Announcement::getExpireTime, LocalDateTime.now())))
                .forEach(announcement -> sources.add(new MessageSource(
                        SOURCE_ANNOUNCEMENT, announcement.getAnnouncementId())));
        return sources;
    }

    private String key(String sourceType, Long sourceId) {
        return sourceType + ":" + sourceId;
    }

    private ApprovalBizType resolveBizType(ApprovalRecords record) {
        ApprovalBizType bizType = ApprovalBizType.fromExt(record.getBizTypeExt());
        return bizType != null ? bizType : ApprovalBizType.fromCode(record.getApprovalRecordType());
    }

    private String resultTitle(String businessName, boolean approved, boolean finalApproved) {
        if (!approved) return businessName + "审批已驳回";
        return businessName + (finalApproved ? "最终审批已通过" : "审批节点已通过");
    }

    private String resultContent(ApprovalRecords record, boolean approved, boolean finalApproved) {
        int level = record.getApprovalLevel() == null ? 1 : record.getApprovalLevel();
        if (!approved) return "第 " + level + " 级审批已驳回，流程已结束";
        if (finalApproved) return "第 " + level + " 级审批已通过，流程审批完成";
        return "第 " + level + " 级审批已通过，流程已转交下一审批人";
    }

    private String doneTitle(String businessName, boolean approved, boolean finalApproved) {
        if (!approved) return "您已驳回" + businessName;
        return "您已通过" + businessName + (finalApproved ? "（最终审批）" : "");
    }

    private String doneContent(ApprovalRecords record, boolean approved, boolean finalApproved) {
        int level = record.getApprovalLevel() == null ? 1 : record.getApprovalLevel();
        if (!approved) return "第 " + level + " 级审批已驳回";
        if (finalApproved) return "第 " + level + " 级审批已通过，流程审批完成";
        return "第 " + level + " 级审批已通过，流程已转交下一审批人";
    }

    private String linkFor(ApprovalRecords record, boolean pendingAction) {
        ApprovalBizType bizType = resolveBizType(record);
        if (bizType == null || record.getInspectionFormId() == null) return "/";
        String base = switch (bizType.getExt()) {
            case "file" -> "/project-files/approval";
            case "inspection" -> "/inspection-approval";
            case "bid" -> "/bids";
            case "acceptance" -> "/acceptance";
            case "output" -> "/output-value";
            case "timesheet" -> "/timesheet";
            case "oa_application" -> "/oa/applications";
            default -> "/";
        };
        if ("/".equals(base)) return base;
        return base + "?detailId=" + record.getInspectionFormId() + (pendingAction ? "&action=approve" : "");
    }

    private String businessName(ApprovalRecords record, Map<String, String> businessNames) {
        ApprovalBizType bizType = resolveBizType(record);
        String label = bizType == null ? "业务" : bizType.getLabel();
        if (bizType == null || record.getInspectionFormId() == null) return label;
        return businessNames.getOrDefault(businessKey(bizType, record.getInspectionFormId()), label);
    }

    private Map<String, String> resolveBusinessNames(List<ApprovalRecords> records) {
        Map<ApprovalBizType, Set<Long>> idsByType = new EnumMap<>(ApprovalBizType.class);
        for (ApprovalRecords record : records) {
            ApprovalBizType type = resolveBizType(record);
            if (type != null && record.getInspectionFormId() != null) {
                idsByType.computeIfAbsent(type, ignored -> new HashSet<>()).add(record.getInspectionFormId());
            }
        }

        Map<String, String> names = new HashMap<>();
        putNames(names, ApprovalBizType.INSPECTION, selectBatch(inspectionFormMapper, idsByType.get(ApprovalBizType.INSPECTION)),
                InspectionForm::getInspectionFormId, form -> displayName("验工单", form.getInspectionFormCode()));
        putNames(names, ApprovalBizType.FILE, selectBatch(projectFilesMapper, idsByType.get(ApprovalBizType.FILE)),
                ProjectFiles::getProjectFileId, file -> displayName("项目文件", file.getFileName()));
        putNames(names, ApprovalBizType.BID, selectBatch(bidMapper, idsByType.get(ApprovalBizType.BID)),
                Bid::getBidId, bid -> displayName("投标", bid.getBidName()));
        putNames(names, ApprovalBizType.ACCEPTANCE, selectBatch(projectAcceptanceMapper, idsByType.get(ApprovalBizType.ACCEPTANCE)),
                ProjectAcceptance::getAcceptanceId, acceptance -> displayName("验收", acceptance.getTitle()));
        putNames(names, ApprovalBizType.OA_APPLICATION, selectBatch(oaApplicationMapper, idsByType.get(ApprovalBizType.OA_APPLICATION)),
                OaApplication::getApplicationId, application -> displayName("OA申请", application.getTitle()));

        List<OutputValue> outputValues = selectBatch(outputValueMapper, idsByType.get(ApprovalBizType.OUTPUT));
        List<Timesheet> timesheets = selectBatch(timesheetMapper, idsByType.get(ApprovalBizType.TIMESHEET));
        Set<Long> projectIds = new HashSet<>();
        outputValues.stream().map(OutputValue::getProjectId).filter(Objects::nonNull).forEach(projectIds::add);
        timesheets.stream().map(Timesheet::getProjectId).filter(Objects::nonNull).forEach(projectIds::add);
        Map<Long, String> projectNames = selectBatch(projectMapper, projectIds).stream()
                .filter(project -> project.getProjectId() != null && StringUtils.hasText(project.getProjectName()))
                .collect(Collectors.toMap(Project::getProjectId, Project::getProjectName, (left, right) -> left));
        putNames(names, ApprovalBizType.OUTPUT, outputValues, OutputValue::getOutputValueId,
                item -> displayName("产值分配", projectNames.get(item.getProjectId())));
        putNames(names, ApprovalBizType.TIMESHEET, timesheets, Timesheet::getTimesheetId,
                item -> displayName("工时", projectNames.get(item.getProjectId())));
        return names;
    }

    private <T> List<T> selectBatch(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mapper.selectBatchIds(ids);
    }

    private <T> void putNames(Map<String, String> target, ApprovalBizType type, List<T> rows,
                              Function<T, Long> idGetter, Function<T, String> nameGetter) {
        for (T row : rows) {
            Long id = idGetter.apply(row);
            if (id != null) target.put(businessKey(type, id), nameGetter.apply(row));
        }
    }

    private String businessKey(ApprovalBizType type, Long id) {
        return type.getExt() + ":" + id;
    }

    private String displayName(String label, String name) {
        return StringUtils.hasText(name) ? label + "「" + name + "」" : label;
    }

    private String nullableText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String projectRoleLabel(Long roleId) {
        if (Objects.equals(roleId, 101L)) return "项目经理";
        if (Objects.equals(roleId, 102L)) return "项目成员";
        return "项目成员";
    }

    private Map<Long, String> resolveUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return sysUserMapper.selectBatchIds(userIds).stream()
                .filter(user -> user.getUserId() != null)
                .collect(Collectors.toMap(SysUser::getUserId, this::displayUserName, (left, right) -> left));
    }

    private String displayUserName(SysUser user) {
        if (StringUtils.hasText(user.getRealName())) return user.getRealName();
        if (StringUtils.hasText(user.getUsername())) return user.getUsername();
        return String.valueOf(user.getUserId());
    }

    private record MessageSource(String sourceType, Long sourceId) {
        private String key() {
            return sourceType + ":" + sourceId;
        }
    }
}
