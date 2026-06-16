package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.GetTodoCenterListDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.TodoCenterDetailVo;
import com.qsy.edifice.domain.vo.TodoCenterItemVo;
import com.qsy.edifice.domain.vo.TodoCenterStatsVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.TodoCenterService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TodoCenterServiceImpl implements TodoCenterService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Resource
    private SysUserMapper sysUserMapper;

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
    private ApprovalFlowService approvalFlowService;

    @Override
    public Page<TodoCenterItemVo> pending(Long userId, GetTodoCenterListDto dto) {
        if (userId == null) return emptyPage(dto);
        List<ApprovalRecords> records = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .eq(ApprovalRecords::getInspectionFormStatus, STATUS_PENDING)
                .orderByDesc(ApprovalRecords::getCreatedTime));
        return page(buildRecordItems(records, true), dto);
    }

    @Override
    public Page<TodoCenterItemVo> initiated(Long userId, GetTodoCenterListDto dto) {
        if (userId == null) return emptyPage(dto);
        List<ApprovalRecords> records = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApplyUserId, userId)
                .orderByDesc(ApprovalRecords::getCreatedTime));
        return page(buildInitiatedItems(records), dto);
    }

    @Override
    public Page<TodoCenterItemVo> processed(Long userId, GetTodoCenterListDto dto) {
        if (userId == null) return emptyPage(dto);
        List<ApprovalRecords> records = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .in(ApprovalRecords::getInspectionFormStatus, STATUS_APPROVED, STATUS_REJECTED)
                .orderByDesc(ApprovalRecords::getUpdatedTime));
        return page(buildRecordItems(records, false), dto);
    }

    @Override
    public TodoCenterDetailVo detail(Long userId, Long recordId) {
        if (userId == null || recordId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批记录id不能为空");
        }
        ApprovalRecords record = approvalRecordsMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "审批记录不存在");
        }
        ApprovalBizType type = resolveBizType(record);
        if (type == null || record.getInspectionFormId() == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "审批业务信息不完整");
        }
        List<ApprovalRecords> chain = queryChainRecords(type, record.getInspectionFormId());
        boolean visible = chain.stream().anyMatch(item -> userId.equals(item.getApprover())
                || userId.equals(item.getApplyUserId()));
        if (!visible) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权查看该待办详情");
        }
        Map<String, String> businessNames = resolveBusinessNames(chain);
        Map<Long, String> userNames = resolveUserNames(userIds(chain));
        TodoCenterItemVo item = buildItem(record, record, businessNames, userNames,
                Integer.valueOf(STATUS_PENDING).equals(record.getInspectionFormStatus()),
                statusOf(record), statusLabel(statusOf(record)));
        return TodoCenterDetailVo.builder()
                .item(item)
                .approvalRecords(approvalFlowService.queryChain(type, record.getInspectionFormId()))
                .build();
    }

    @Override
    public TodoCenterStatsVo statistics(Long userId) {
        if (userId == null) {
            return TodoCenterStatsVo.builder()
                    .pendingCount(0L)
                    .initiatedCount(0L)
                    .processedCount(0L)
                    .ccCount(0L)
                    .todayPendingCount(0L)
                    .build();
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long pendingCount = approvalRecordsMapper.selectCount(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .eq(ApprovalRecords::getInspectionFormStatus, STATUS_PENDING));
        Long processedCount = approvalRecordsMapper.selectCount(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .in(ApprovalRecords::getInspectionFormStatus, STATUS_APPROVED, STATUS_REJECTED));
        Long todayPendingCount = approvalRecordsMapper.selectCount(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprover, userId)
                .eq(ApprovalRecords::getInspectionFormStatus, STATUS_PENDING)
                .ge(ApprovalRecords::getCreatedTime, todayStart));
        Long initiatedCount = (long) initiatedBusinessKeys(userId).size();
        return TodoCenterStatsVo.builder()
                .pendingCount(pendingCount)
                .initiatedCount(initiatedCount)
                .processedCount(processedCount)
                .ccCount(0L)
                .todayPendingCount(todayPendingCount)
                .build();
    }

    private Set<String> initiatedBusinessKeys(Long userId) {
        List<ApprovalRecords> records = approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .select(ApprovalRecords::getApprovalRecordType,
                        ApprovalRecords::getInspectionFormId,
                        ApprovalRecords::getBizTypeExt)
                .eq(ApprovalRecords::getApplyUserId, userId));
        return records.stream()
                .map(this::businessKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<TodoCenterItemVo> buildRecordItems(List<ApprovalRecords> records, boolean pendingAction) {
        if (records.isEmpty()) return List.of();
        Map<String, String> businessNames = resolveBusinessNames(records);
        Map<Long, String> userNames = resolveUserNames(userIds(records));
        return records.stream()
                .map(record -> buildItem(record, record, businessNames, userNames, pendingAction,
                        statusOf(record), statusLabel(statusOf(record))))
                .toList();
    }

    private List<TodoCenterItemVo> buildInitiatedItems(List<ApprovalRecords> records) {
        if (records.isEmpty()) return List.of();
        Map<String, List<ApprovalRecords>> groups = records.stream()
                .filter(record -> businessKey(record) != null)
                .collect(Collectors.groupingBy(this::businessKey, LinkedHashMap::new, Collectors.toList()));
        Map<String, String> businessNames = resolveBusinessNames(records);
        Map<Long, String> userNames = resolveUserNames(userIds(records));
        List<TodoCenterItemVo> items = new ArrayList<>();
        for (List<ApprovalRecords> chain : groups.values()) {
            ApprovalRecords latest = latest(chain);
            ApprovalRecords pending = latest(chain.stream()
                    .filter(record -> Integer.valueOf(STATUS_PENDING).equals(record.getInspectionFormStatus()))
                    .toList());
            boolean rejected = chain.stream()
                    .anyMatch(record -> Integer.valueOf(STATUS_REJECTED).equals(record.getInspectionFormStatus()));
            int status = rejected ? STATUS_REJECTED : pending != null ? STATUS_PENDING : STATUS_APPROVED;
            ApprovalRecords displayRecord = pending != null ? pending : latest;
            items.add(buildItem(latest, displayRecord, businessNames, userNames, false,
                    status, initiatedStatusLabel(status)));
        }
        return items;
    }

    private TodoCenterItemVo buildItem(ApprovalRecords sourceRecord,
                                       ApprovalRecords displayRecord,
                                       Map<String, String> businessNames,
                                       Map<Long, String> userNames,
                                       boolean pendingAction,
                                       Integer status,
                                       String statusLabel) {
        ApprovalBizType bizType = resolveBizType(sourceRecord);
        String bizTypeExt = bizType == null ? sourceRecord.getBizTypeExt() : bizType.getExt();
        String bizTypeLabel = bizType == null ? "业务" : bizType.getLabel();
        String bizName = businessName(sourceRecord, businessNames);
        Long currentApproverId = Integer.valueOf(STATUS_PENDING).equals(displayRecord.getInspectionFormStatus())
                ? displayRecord.getApprover()
                : displayRecord.getNextApproverId();
        return TodoCenterItemVo.builder()
                .todoId(displayRecord.getApprovalRecordId())
                .bizType(bizTypeExt)
                .bizTypeLabel(bizTypeLabel)
                .bizId(sourceRecord.getInspectionFormId())
                .bizName(bizName)
                .title(bizName + " · " + statusLabel)
                .status(status)
                .statusLabel(statusLabel)
                .applyUserId(sourceRecord.getApplyUserId())
                .applyUserName(userNames.getOrDefault(sourceRecord.getApplyUserId(), "-"))
                .currentApproverId(currentApproverId)
                .currentApproverName(userNames.getOrDefault(currentApproverId, "-"))
                .approvalLevel(displayRecord.getApprovalLevel() == null ? 1 : displayRecord.getApprovalLevel())
                .createdTime(sourceRecord.getCreatedTime())
                .updatedTime(displayRecord.getUpdatedTime() != null ? displayRecord.getUpdatedTime() : displayRecord.getCreatedTime())
                .link(linkFor(sourceRecord, pendingAction))
                .build();
    }

    private Page<TodoCenterItemVo> page(List<TodoCenterItemVo> all, GetTodoCenterListDto dto) {
        List<TodoCenterItemVo> filtered = applyFilters(all, dto);
        filtered.sort(Comparator.comparing(this::sortTime, Comparator.nullsLast(Comparator.reverseOrder())));
        int current = dto != null && dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 100)
                : 10;
        int from = Math.min((current - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        Page<TodoCenterItemVo> page = new Page<>(current, pageSize, filtered.size());
        page.setRecords(filtered.subList(from, to));
        return page;
    }

    private List<TodoCenterItemVo> applyFilters(List<TodoCenterItemVo> items, GetTodoCenterListDto dto) {
        if (dto == null) return new ArrayList<>(items);
        return items.stream()
                .filter(item -> !StringUtils.hasText(dto.getBizType()) || dto.getBizType().equals(item.getBizType()))
                .filter(item -> dto.getStatus() == null || dto.getStatus().equals(item.getStatus()))
                .filter(item -> matchesKeyword(item, dto.getKeyword()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean matchesKeyword(TodoCenterItemVo item, String keyword) {
        if (!StringUtils.hasText(keyword)) return true;
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.getTitle(), normalized)
                || contains(item.getBizName(), normalized)
                || contains(item.getApplyUserName(), normalized)
                || contains(item.getCurrentApproverName(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Page<TodoCenterItemVo> emptyPage(GetTodoCenterListDto dto) {
        int current = dto != null && dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;
        return new Page<>(current, pageSize, 0);
    }

    private LocalDateTime sortTime(TodoCenterItemVo item) {
        return item.getUpdatedTime() != null ? item.getUpdatedTime() : item.getCreatedTime();
    }

    private ApprovalRecords latest(List<ApprovalRecords> records) {
        return records.stream()
                .max(Comparator.comparing(this::recordTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private LocalDateTime recordTime(ApprovalRecords record) {
        return record.getUpdatedTime() != null ? record.getUpdatedTime() : record.getCreatedTime();
    }

    private int statusOf(ApprovalRecords record) {
        return record.getInspectionFormStatus() == null ? STATUS_PENDING : record.getInspectionFormStatus();
    }

    private String statusLabel(Integer status) {
        if (Integer.valueOf(STATUS_APPROVED).equals(status)) return "已通过";
        if (Integer.valueOf(STATUS_REJECTED).equals(status)) return "已驳回";
        return "待处理";
    }

    private String initiatedStatusLabel(Integer status) {
        if (Integer.valueOf(STATUS_APPROVED).equals(status)) return "已通过";
        if (Integer.valueOf(STATUS_REJECTED).equals(status)) return "已驳回";
        return "审批中";
    }

    private Set<Long> userIds(List<ApprovalRecords> records) {
        Set<Long> userIds = new HashSet<>();
        for (ApprovalRecords record : records) {
            if (record.getApplyUserId() != null) userIds.add(record.getApplyUserId());
            if (record.getApprover() != null) userIds.add(record.getApprover());
            if (record.getNextApproverId() != null) userIds.add(record.getNextApproverId());
        }
        return userIds;
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

    private ApprovalBizType resolveBizType(ApprovalRecords record) {
        ApprovalBizType bizType = ApprovalBizType.fromExt(record.getBizTypeExt());
        return bizType != null ? bizType : ApprovalBizType.fromCode(record.getApprovalRecordType());
    }

    private List<ApprovalRecords> queryChainRecords(ApprovalBizType type, Long bizId) {
        if (type == null || bizId == null) return List.of();
        List<ApprovalRecords> chain = approvalRecordsMapper.selectByBizTypeExtAndBizId(type.getExt(), bizId);
        if (!chain.isEmpty()) return chain;
        return approvalRecordsMapper.selectList(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getApprovalRecordType, type.getCode())
                .eq(ApprovalRecords::getInspectionFormId, bizId)
                .orderByAsc(ApprovalRecords::getCreatedTime)
                .orderByAsc(ApprovalRecords::getApprovalRecordId));
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

    private String businessKey(ApprovalRecords record) {
        ApprovalBizType type = resolveBizType(record);
        if (type == null || record.getInspectionFormId() == null) return null;
        return businessKey(type, record.getInspectionFormId());
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

    private <T> List<T> selectBatch(BaseMapper<T> mapper, Set<Long> ids) {
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
}
