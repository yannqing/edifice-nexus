package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.domain.dto.ApproveDto;
import com.qsy.edifice.domain.dto.CreateOaApplicationDto;
import com.qsy.edifice.domain.dto.GetOaApplicationListDto;
import com.qsy.edifice.domain.dto.SubmitApprovalDto;
import com.qsy.edifice.domain.dto.SubmitOaApplicationDto;
import com.qsy.edifice.domain.dto.UpdateOaApplicationDto;
import com.qsy.edifice.domain.entity.ApprovalRecords;
import com.qsy.edifice.domain.entity.OaApplication;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.ApprovalRecordVo;
import com.qsy.edifice.domain.vo.OaApplicationTypeVo;
import com.qsy.edifice.domain.vo.OaApplicationVo;
import com.qsy.edifice.enums.ApprovalBizType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.ApprovalRecordsMapper;
import com.qsy.edifice.mapper.OaApplicationMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.ApprovalFlowService;
import com.qsy.edifice.service.OaApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OaApplicationServiceImpl implements OaApplicationService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_APPROVING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_WITHDRAWN = 4;

    private static final int APPROVAL_STATUS_REJECTED = 2;

    private static final List<OaApplicationTypeVo> TYPES = List.of(
            new OaApplicationTypeVo("general", "通用审批", "通用", true),
            new OaApplicationTypeVo("leave", "请假", "人事", true),
            new OaApplicationTypeVo("business_trip", "出差", "人事", true),
            new OaApplicationTypeVo("makeup_card", "补卡", "考勤", true),
            new OaApplicationTypeVo("outgoing", "外出申请", "考勤", true),
            new OaApplicationTypeVo("probation", "转正申请", "人事", true),
            new OaApplicationTypeVo("resignation", "离职", "人事", true),
            new OaApplicationTypeVo("seal", "用章申请", "行政", true),
            new OaApplicationTypeVo("vehicle", "用车申请", "行政", true),
            new OaApplicationTypeVo("document", "公文审批", "行政", true),
            new OaApplicationTypeVo("purchase", "采购申请", "财务", true),
            new OaApplicationTypeVo("expense", "费用报销", "财务", true),
            new OaApplicationTypeVo("payment", "对外付款", "财务", true),
            new OaApplicationTypeVo("invoice", "开票申请", "财务", true),
            new OaApplicationTypeVo("acceptance", "阶段性验收", "项目", true),
            new OaApplicationTypeVo("deliverable", "成果", "项目", true)
    );

    private static final Map<String, OaApplicationTypeVo> TYPE_MAP = TYPES.stream()
            .collect(Collectors.toMap(OaApplicationTypeVo::getType, t -> t, (a, b) -> a, LinkedHashMap::new));

    @Resource
    private OaApplicationMapper oaApplicationMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ApprovalFlowService approvalFlowService;

    @Resource
    private ApprovalRecordsMapper approvalRecordsMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public List<OaApplicationTypeVo> listTypes() {
        return TYPES;
    }

    @Override
    public Page<OaApplicationVo> list(GetOaApplicationListDto dto, Long currentUserId) {
        if (dto == null) {
            dto = new GetOaApplicationListDto();
        }
        int current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;

        LambdaQueryWrapper<OaApplication> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(dto.getKeywords())) {
            String kw = dto.getKeywords().trim();
            wrapper.and(w -> w.like(OaApplication::getTitle, kw)
                    .or().like(OaApplication::getApplicationNo, kw));
        }
        if (StringUtils.isNotBlank(dto.getApplicationType())) {
            wrapper.eq(OaApplication::getApplicationType, dto.getApplicationType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(OaApplication::getStatus, dto.getStatus());
        }
        if (!Boolean.FALSE.equals(dto.getMine())) {
            wrapper.eq(OaApplication::getApplicantId, currentUserId);
        }
        wrapper.orderByDesc(OaApplication::getCreatedTime);

        Page<OaApplication> page = oaApplicationMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<OaApplicationVo> result = new Page<>(current, pageSize, page.getTotal());
        result.setRecords(toVos(page.getRecords()));
        return result;
    }

    @Override
    public Page<OaApplicationVo> listMyPending(GetOaApplicationListDto dto, Long currentUserId) {
        if (dto == null) {
            dto = new GetOaApplicationListDto();
        }
        int current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;

        List<ApprovalRecordVo> pendingRecords = approvalFlowService
                .listPendingByApprover(currentUserId, ApprovalBizType.OA_APPLICATION);
        List<Long> recordIds = pendingRecords.stream()
                .map(ApprovalRecordVo::getApprovalRecordId)
                .filter(Objects::nonNull)
                .toList();
        if (recordIds.isEmpty()) {
            return new Page<OaApplicationVo>(current, pageSize, 0).setRecords(Collections.emptyList());
        }

        LambdaQueryWrapper<OaApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OaApplication::getCurrentRecordId, recordIds);
        if (StringUtils.isNotBlank(dto.getKeywords())) {
            String kw = dto.getKeywords().trim();
            wrapper.and(w -> w.like(OaApplication::getTitle, kw)
                    .or().like(OaApplication::getApplicationNo, kw));
        }
        if (StringUtils.isNotBlank(dto.getApplicationType())) {
            wrapper.eq(OaApplication::getApplicationType, dto.getApplicationType());
        }
        wrapper.orderByDesc(OaApplication::getSubmittedTime)
                .orderByDesc(OaApplication::getCreatedTime);

        Page<OaApplication> page = oaApplicationMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Page<OaApplicationVo> result = new Page<>(current, pageSize, page.getTotal());
        result.setRecords(toVos(page.getRecords()));
        return result;
    }

    @Override
    public OaApplicationVo getById(Long applicationId, Long currentUserId) {
        OaApplication application = findVisible(applicationId, currentUserId);
        return toVos(List.of(application)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CreateOaApplicationDto dto, Long currentUserId) {
        validateCreate(dto);
        boolean submitNow = dto.getStatus() != null && dto.getStatus() == STATUS_APPROVING;
        OaApplication application = OaApplication.builder()
                .applicationNo(nextApplicationNo())
                .applicationType(dto.getApplicationType())
                .title(dto.getTitle().trim())
                .applicantId(currentUserId)
                .status(STATUS_DRAFT)
                .priority(dto.getPriority() == null ? 0 : dto.getPriority())
                .formData(writeJson(dto.getFormData() == null ? Collections.emptyMap() : dto.getFormData()))
                .attachmentIds(writeJson(dto.getAttachmentIds() == null ? Collections.emptyList() : dto.getAttachmentIds()))
                .build();
        oaApplicationMapper.insert(application);
        if (submitNow) {
            SubmitOaApplicationDto submitDto = new SubmitOaApplicationDto();
            submitDto.setFirstApproverId(dto.getFirstApproverId());
            submitDto.setDescription(dto.getSubmitDescription());
            submit(application.getApplicationId(), submitDto, currentUserId);
        }
        return application.getApplicationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateOaApplicationDto dto, Long currentUserId) {
        if (dto == null || dto.getApplicationId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "申请ID不能为空");
        }
        OaApplication existing = findOwned(dto.getApplicationId(), currentUserId);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "只有草稿申请可以编辑");
        }
        if (StringUtils.isNotBlank(dto.getApplicationType())) {
            ensureType(dto.getApplicationType());
            existing.setApplicationType(dto.getApplicationType());
        }
        if (StringUtils.isNotBlank(dto.getTitle())) {
            existing.setTitle(dto.getTitle().trim());
        }
        if (dto.getPriority() != null) {
            existing.setPriority(dto.getPriority());
        }
        if (dto.getFormData() != null) {
            existing.setFormData(writeJson(dto.getFormData()));
        }
        if (dto.getAttachmentIds() != null) {
            existing.setAttachmentIds(writeJson(dto.getAttachmentIds()));
        }
        oaApplicationMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long applicationId, SubmitOaApplicationDto dto, Long currentUserId) {
        if (dto == null || dto.getFirstApproverId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "首审人不能为空");
        }
        OaApplication existing = findOwned(applicationId, currentUserId);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "只有草稿申请可以提交");
        }
        ApprovalRecords record = approvalFlowService.submit(new SubmitApprovalDto(
                ApprovalBizType.OA_APPLICATION.getExt(),
                existing.getApplicationId(),
                dto.getFirstApproverId(),
                StringUtils.defaultIfBlank(dto.getDescription(), "OA申请提交")
        ), currentUserId);
        existing.setStatus(STATUS_APPROVING);
        existing.setCurrentRecordId(record.getApprovalRecordId());
        existing.setSubmittedTime(LocalDateTime.now());
        oaApplicationMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long applicationId, Long currentUserId) {
        OaApplication existing = findOwned(applicationId, currentUserId);
        if (!Objects.equals(existing.getStatus(), STATUS_APPROVING)) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "只有审批中的申请可以撤回");
        }
        if (existing.getCurrentRecordId() != null) {
            ApprovalRecords record = new ApprovalRecords();
            record.setApprovalRecordId(existing.getCurrentRecordId());
            record.setInspectionFormStatus(APPROVAL_STATUS_REJECTED);
            record.setApprovalDescription("申请人撤回");
            record.setNextApproverId(null);
            record.setUpdatedTime(LocalDateTime.now());
            if (approvalRecordsMapper.updatePendingResult(record) != 1) {
                throw new BusinessException(ErrorType.OPERATION_FAILED, "该申请已被审批，无法撤回");
            }
        }
        existing.setStatus(STATUS_WITHDRAWN);
        existing.setCurrentRecordId(null);
        oaApplicationMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalFlowService.ApprovalResult approve(ApproveDto dto, Long currentUserId) {
        if (dto == null || dto.getRecordId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "审批记录ID不能为空");
        }
        ApprovalRecords record = approvalRecordsMapper.selectById(dto.getRecordId());
        if (record == null || !ApprovalBizType.OA_APPLICATION.getExt().equals(record.getBizTypeExt())) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "OA 审批记录不存在");
        }

        ApprovalFlowService.ApprovalResult result = approvalFlowService.approve(dto, currentUserId);
        OaApplication application = oaApplicationMapper.selectById(result.bizId);
        if (application == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "申请不存在");
        }
        if (result.rejected) {
            application.setStatus(STATUS_REJECTED);
            application.setCurrentRecordId(null);
        } else if (result.isFinal) {
            application.setStatus(STATUS_APPROVED);
            application.setApprovedTime(LocalDateTime.now());
            application.setCurrentRecordId(null);
        } else {
            application.setStatus(STATUS_APPROVING);
            application.setCurrentRecordId(result.nextRecordId);
        }
        oaApplicationMapper.updateById(application);
        return result;
    }

    private void validateCreate(CreateOaApplicationDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getApplicationType()) || StringUtils.isBlank(dto.getTitle())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "申请类型和标题不能为空");
        }
        ensureType(dto.getApplicationType());
        if (dto.getTitle().length() > 200) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "申请标题不能超过 200 字");
        }
    }

    private OaApplication findOwned(Long applicationId, Long currentUserId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "申请ID不能为空");
        }
        OaApplication application = oaApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "申请不存在");
        }
        if (currentUserId == null || !currentUserId.equals(application.getApplicantId())) {
            throw new BusinessException(ErrorType.NO_AUTH_ERROR, "只能操作自己的申请");
        }
        return application;
    }

    private OaApplication findVisible(Long applicationId, Long currentUserId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "申请ID不能为空");
        }
        OaApplication application = oaApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "申请不存在");
        }
        if (currentUserId != null && (currentUserId.equals(application.getApplicantId())
                || isApplicationApprover(applicationId, currentUserId) || isSuperAdmin())) {
            return application;
        }
        throw new BusinessException(ErrorType.NO_AUTH_ERROR, "您无权查看该申请");
    }

    private boolean isApplicationApprover(Long applicationId, Long currentUserId) {
        Long count = approvalRecordsMapper.selectCount(new LambdaQueryWrapper<ApprovalRecords>()
                .eq(ApprovalRecords::getBizTypeExt, ApprovalBizType.OA_APPLICATION.getExt())
                .eq(ApprovalRecords::getInspectionFormId, applicationId)
                .eq(ApprovalRecords::getApprover, currentUserId));
        return count != null && count > 0;
    }

    private boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    private void ensureType(String type) {
        if (!TYPE_MAP.containsKey(type)) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "未知申请类型：" + type);
        }
    }

    private List<OaApplicationVo> toVos(List<OaApplication> applications) {
        if (applications == null || applications.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = applications.stream()
                .map(OaApplication::getApplicantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Long> recordIds = applications.stream()
                .map(OaApplication::getCurrentRecordId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ApprovalRecords> recordMap = recordIds.isEmpty() ? Collections.emptyMap()
                : approvalRecordsMapper.selectBatchIds(recordIds).stream()
                .collect(Collectors.toMap(ApprovalRecords::getApprovalRecordId, r -> r, (a, b) -> a));
        recordMap.values().stream()
                .map(ApprovalRecords::getApprover)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        Map<Long, SysUser> users = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        return applications.stream().map(application -> {
            OaApplicationTypeVo type = TYPE_MAP.get(application.getApplicationType());
            SysUser applicant = users.get(application.getApplicantId());
            ApprovalRecords currentRecord = application.getCurrentRecordId() == null ? null : recordMap.get(application.getCurrentRecordId());
            Long currentApproverId = currentRecord == null ? null : currentRecord.getApprover();
            SysUser currentApprover = currentApproverId == null ? null : users.get(currentApproverId);
            return OaApplicationVo.builder()
                    .applicationId(application.getApplicationId())
                    .applicationNo(application.getApplicationNo())
                    .applicationType(application.getApplicationType())
                    .applicationTypeLabel(type == null ? application.getApplicationType() : type.getLabel())
                    .title(application.getTitle())
                    .applicantId(application.getApplicantId())
                    .applicantName(applicant == null ? null : StringUtils.defaultIfBlank(applicant.getRealName(), applicant.getUsername()))
                    .status(application.getStatus())
                    .priority(application.getPriority())
                    .formData(readMap(application.getFormData()))
                    .attachmentIds(readLongList(application.getAttachmentIds()))
                    .currentRecordId(application.getCurrentRecordId())
                    .currentApproverId(currentApproverId)
                    .currentApproverName(currentApprover == null ? null : StringUtils.defaultIfBlank(currentApprover.getRealName(), currentApprover.getUsername()))
                    .submittedTime(application.getSubmittedTime())
                    .approvedTime(application.getApprovedTime())
                    .createdTime(application.getCreatedTime())
                    .updatedTime(application.getUpdatedTime())
                    .build();
        }).collect(Collectors.toList());
    }

    private String nextApplicationNo() {
        return "OA" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + StringUtils.right(String.valueOf(System.currentTimeMillis()), 8);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "表单数据格式错误");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("OA 申请表单 JSON 解析失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<Long> readLongList(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("OA 申请附件 JSON 解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
