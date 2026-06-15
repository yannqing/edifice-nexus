package com.qsy.edifice.service.impl;

import com.qsy.edifice.config.OaUserSyncProperties;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.dto.OaContractProjectCreateDto;
import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.service.OaContractProjectService;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.ProjectTypeService;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OaContractProjectServiceImpl implements OaContractProjectService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private OaUserSyncProperties properties;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private FilesMapper filesMapper;

    @Override
    public List<Map<String, Object>> listEnabledProjectTypes() {
        return projectTypeService.getAllEnabledProjectTypes().stream()
                .map(type -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("projectTypeId", type.getProjectTypeId().toString());
                    item.put("projectTypeName", type.getProjectTypeName());
                    item.put("projectTypeCode", type.getProjectTypeCode());
                    return item;
                })
                .toList();
    }

    @Override
    public Map<String, Object> getProjectStatus(Integer oaContractId) {
        if (oaContractId == null || oaContractId <= 0) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "OA 合同 ID 不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.oa_contract_id, m.edifice_project_id, m.edifice_contract_id, " +
                        "p.project_name, p.project_code, p.project_status " +
                        "FROM oa_contract_project_mapping m " +
                        "JOIN project p ON p.project_id = m.edifice_project_id AND p.is_delete = 0 " +
                        "WHERE m.oa_contract_id = ? AND m.is_delete = 0 LIMIT 1",
                oaContractId
        );
        if (rows.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", false);
            result.put("oaContractId", oaContractId);
            return result;
        }
        return mappingResult(rows.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createProject(OaContractProjectCreateDto dto) {
        if (dto == null || dto.getOaContractId() == null || dto.getProjectTypeId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "OA 合同和项目类型不能为空");
        }

        Map<String, Object> existing = getProjectStatus(dto.getOaContractId());
        if (Boolean.TRUE.equals(existing.get("exists"))) {
            return existing;
        }

        ProjectType projectType = projectTypeService.getProjectTypeById(dto.getProjectTypeId());
        if (projectType == null || !Objects.equals(projectType.getProjectTypeStatus(), 1)) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "所选项目类型不存在或已停用");
        }

        Map<String, Object> oaContract = loadApprovedOaContract(dto.getOaContractId());
        String projectCode = stringValue(oaContract.get("code"));
        Project codeConflict = StringUtils.hasText(projectCode) ? projectMapper.selectByProjectCode(projectCode) : null;
        if (codeConflict != null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "OA 合同编号已被其他工程项目使用：" + projectCode);
        }

        List<Integer> oaAdminIds = collectOaAdminIds(oaContract);
        Map<Integer, Long> userMappings = loadEdificeUserMappings(oaAdminIds);
        Long managerId = firstMappedUser(userMappings,
                intValue(oaContract.get("sign_uid")),
                intValue(oaContract.get("admin_id")),
                intValue(oaContract.get("prepared_uid")),
                intValue(oaContract.get("keeper_uid"))
        );
        if (managerId == null) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "合同签订人或录入人尚未同步到 Edifice，无法指定项目经理");
        }

        LinkedHashSet<Long> memberIds = oaAdminIds.stream()
                .map(userMappings::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        memberIds.remove(managerId);

        BigDecimal contractAmount = decimalValue(oaContract.get("cost"));
        if (contractAmount == null || contractAmount.signum() <= 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "OA 合同金额必须大于 0");
        }
        int contractType = Objects.equals(dto.getContractType(), 1) ? 1 : 0;
        BigDecimal baseAmount = contractType == 0
                ? contractAmount
                : defaultAmount(dto.getBaseAmount(), contractAmount);
        BigDecimal benefitAmount = contractType == 1
                ? defaultAmount(dto.getBenefitAmount(), BigDecimal.ZERO)
                : null;

        Files sourceFile = buildSourceContractFile(dto, oaContract, managerId);
        filesMapper.insert(sourceFile);

        CreateProjectDto createDto = new CreateProjectDto();
        createDto.setProjectName(stringValue(oaContract.get("name")));
        createDto.setProjectCode(projectCode);
        createDto.setProjectType(dto.getProjectTypeId());
        createDto.setContractType(contractType);
        createDto.setContractAmount(contractAmount);
        createDto.setBaseAmount(baseAmount);
        createDto.setBenefitAmount(benefitAmount);
        createDto.setContractFile(sourceFile.getFileId());
        createDto.setProjectCharges(List.of(managerId));
        createDto.setProjectMembers(new ArrayList<>(memberIds));
        createDto.setSigningTime(epochToLocalDateTime(oaContract.get("sign_time")));
        createDto.setPreStartTime(epochToLocalDateTime(oaContract.get("start_time")));
        createDto.setPreEndTime(epochToLocalDateTime(oaContract.get("end_time")));

        Long projectId = projectService.createProject(createDto, managerId);
        Long contractId = jdbcTemplate.queryForObject(
                "SELECT contract_id FROM contract WHERE project_id = ? AND is_delete = 0 LIMIT 1",
                Long.class,
                projectId
        );
        jdbcTemplate.update(
                "INSERT INTO oa_contract_project_mapping " +
                        "(oa_contract_id, edifice_project_id, edifice_contract_id, created_by) VALUES (?, ?, ?, ?)",
                dto.getOaContractId(), projectId, contractId, managerId
        );
        return getProjectStatus(dto.getOaContractId());
    }

    private Map<String, Object> loadApprovedOaContract(Integer oaContractId) {
        String database = safeOfficeDatabase();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, code, name, cost, check_status, sign_uid, admin_id, prepared_uid, keeper_uid, " +
                        "share_ids, file_ids, sign_time, start_time, end_time, stop_time, void_time " +
                        "FROM `" + database + "`.oa_contract WHERE id = ? AND delete_time = 0 LIMIT 1",
                oaContractId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "OA 合同不存在或已删除");
        }
        Map<String, Object> contract = rows.get(0);
        if (intValue(contract.get("check_status")) != 2) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "仅审批通过的 OA 合同可以创建工程项目");
        }
        if (longValue(contract.get("stop_time")) > 0 || longValue(contract.get("void_time")) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "已中止或已作废的 OA 合同不能创建工程项目");
        }
        if (!StringUtils.hasText(stringValue(contract.get("name")))) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "OA 合同名称不能为空");
        }
        return contract;
    }

    private Map<Integer, Long> loadEdificeUserMappings(List<Integer> oaAdminIds) {
        if (oaAdminIds.isEmpty()) {
            return Map.of();
        }
        String database = safeOfficeDatabase();
        String placeholders = oaAdminIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, userid FROM `" + database + "`.oa_admin " +
                        "WHERE id IN (" + placeholders + ") AND delete_time = 0 AND status = 1",
                oaAdminIds.toArray()
        );
        Map<Integer, Long> mappings = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String userId = stringValue(row.get("userid"));
            if (userId.matches("\\d+")) {
                mappings.put(intValue(row.get("id")), Long.parseLong(userId));
            }
        }
        return mappings;
    }

    private List<Integer> collectOaAdminIds(Map<String, Object> contract) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        addPositive(ids, intValue(contract.get("sign_uid")));
        addPositive(ids, intValue(contract.get("admin_id")));
        addPositive(ids, intValue(contract.get("prepared_uid")));
        addPositive(ids, intValue(contract.get("keeper_uid")));
        String shares = stringValue(contract.get("share_ids"));
        if (StringUtils.hasText(shares)) {
            Arrays.stream(shares.split(","))
                    .map(String::trim)
                    .filter(value -> value.matches("\\d+"))
                    .map(Integer::parseInt)
                    .filter(value -> value > 0)
                    .forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }

    private Files buildSourceContractFile(OaContractProjectCreateDto dto, Map<String, Object> oaContract, Long userId) {
        String contractUrl = dto.getContractUrl();
        if (!StringUtils.hasText(contractUrl)
                || (!contractUrl.startsWith("http://") && !contractUrl.startsWith("https://"))) {
            contractUrl = properties.getBaseUrl().replaceAll("/+$", "") + "/contract/contract/view?id=" + dto.getOaContractId();
        }
        String name = stringValue(oaContract.get("name")) + "-OA合同";
        Files file = new Files();
        file.setUploadUserId(userId);
        file.setFileType("document");
        file.setFileName(name);
        file.setOriginalName(name);
        file.setDisplayName(name);
        file.setFileExtension("html");
        file.setStorageType("oa-link");
        file.setFileUrl(contractUrl);
        file.setFilePath(contractUrl);
        file.setFileSize(0L);
        file.setMimeType("text/html");
        file.setAccessCount(0);
        file.setDownloadCount(0);
        file.setPreviewCount(0);
        file.setShareCount(0);
        file.setStatus(1);
        file.setPermissionLevel(1);
        file.setIsDeleted(0);
        return file;
    }

    private Map<String, Object> mappingResult(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("oaContractId", row.get("oa_contract_id"));
        result.put("projectId", String.valueOf(row.get("edifice_project_id")));
        result.put("contractId", String.valueOf(row.get("edifice_contract_id")));
        result.put("projectName", row.get("project_name"));
        result.put("projectCode", row.get("project_code"));
        result.put("projectStatus", row.get("project_status"));
        return result;
    }

    private String safeOfficeDatabase() {
        String database = properties.getOfficeDatabase();
        if (!StringUtils.hasText(database) || !database.matches("[A-Za-z0-9_]+")) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "OA 数据库配置无效");
        }
        return database;
    }

    private Long firstMappedUser(Map<Integer, Long> mappings, Integer... oaAdminIds) {
        for (Integer oaAdminId : oaAdminIds) {
            Long userId = mappings.get(oaAdminId);
            if (userId != null) {
                return userId;
            }
        }
        return null;
    }

    private void addPositive(Set<Integer> ids, int value) {
        if (value > 0) {
            ids.add(value);
        }
    }

    private BigDecimal defaultAmount(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    private LocalDateTime epochToLocalDateTime(Object value) {
        long epoch = longValue(value);
        return epoch <= 0 ? LocalDateTime.now() : LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
