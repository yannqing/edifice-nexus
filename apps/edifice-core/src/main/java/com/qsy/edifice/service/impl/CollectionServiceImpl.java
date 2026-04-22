package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qsy.edifice.domain.dto.CreateCollectionRecordDto;
import com.qsy.edifice.domain.dto.GetCollectionListDto;
import com.qsy.edifice.domain.dto.UpdateCollectionRecordDto;
import com.qsy.edifice.domain.entity.CollectionRecord;
import com.qsy.edifice.domain.entity.Contract;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectMember;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.CollectionDetailVo;
import com.qsy.edifice.domain.vo.CollectionRecordVo;
import com.qsy.edifice.domain.vo.CollectionStatisticsVo;
import com.qsy.edifice.domain.vo.CollectionSummaryVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.CollectionRecordMapper;
import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.CollectionService;
import com.qsy.edifice.service.ContractService;
import com.qsy.edifice.service.ProjectMemberService;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.ProjectStageService;
import com.qsy.edifice.service.ProjectTypeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 回款管理服务实现类
 *
 * 模型约定：
 * - 单表 collection_record 记录每笔实际收款
 * - 项目应收额 = 合同金额 × 已完成阶段产值比例之和（未完成阶段不计入应收）
 * - 项目状态（未回款 / 部分回款 / 已回款）由已收与应收对比推断
 */
@Slf4j
@Service
public class CollectionServiceImpl implements CollectionService {

    /** 项目经理角色 id（与 ProjectServiceImpl 保持一致） */
    private static final Long ROLE_PROJECT_MANAGER = 101L;

    /** 阶段被视为"已完成"的状态集合：3-已验收 / 6-已完成 */
    private static final Set<Integer> STAGE_COMPLETED_STATUSES = Set.of(3, 6);

    @Resource
    private CollectionRecordMapper collectionRecordMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ContractService contractService;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private SysUserMapper sysUserMapper;

    // ==================== 列表查询 ====================

    @Override
    public Page<CollectionSummaryVo> getCollectionList(GetCollectionListDto dto) {
        Integer current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        Integer pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;

        // 1. 按关键字筛选项目
        LambdaQueryWrapper<Project> pw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeywords())) {
            pw.and(w -> w.like(Project::getProjectName, dto.getKeywords())
                    .or().like(Project::getProjectCode, dto.getKeywords()));
        }
        pw.orderByDesc(Project::getCreatedTime);

        List<Project> allProjects = projectMapper.selectList(pw);
        if (allProjects.isEmpty()) {
            return new Page<>(current, pageSize, 0);
        }

        // 2. 批量预取相关数据，避免 N+1
        Set<Long> projectIds = allProjects.stream().map(Project::getProjectId).collect(Collectors.toSet());
        Map<Long, BigDecimal> collectedMap = loadCollectedAmountByProjects(projectIds);
        Map<Long, ProjectType> typeMap = loadProjectTypeMap(allProjects);

        // 3. 构造汇总
        List<CollectionSummaryVo> all = allProjects.stream()
                .map(p -> buildSummary(p, typeMap.get(p.getProjectType()),
                        collectedMap.getOrDefault(p.getProjectId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        // 4. 状态过滤
        if (dto.getCollectionStatus() != null) {
            all = all.stream()
                    .filter(s -> dto.getCollectionStatus().equals(s.getCollectionStatus()))
                    .collect(Collectors.toList());
        }

        // 5. 内存分页
        long total = all.size();
        int from = (current - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<CollectionSummaryVo> page = from >= all.size() ? Collections.emptyList() : all.subList(from, to);

        Page<CollectionSummaryVo> result = new Page<>(current, pageSize, total);
        result.setRecords(page);
        return result;
    }

    @Override
    public CollectionStatisticsVo getStatistics() {
        List<Project> projects = projectMapper.selectList(null);
        Set<Long> projectIds = projects.stream().map(Project::getProjectId).collect(Collectors.toSet());

        // 批量预取合同 + 阶段，避免 N+1
        Map<Long, Contract> contractMap = loadContractsByProjectIds(projectIds);
        Map<Long, List<ProjectStage>> stagesByProject = loadStagesByProjectIds(projectIds);

        BigDecimal totalExpected = BigDecimal.ZERO;
        for (Project p : projects) {
            totalExpected = totalExpected.add(calcExpectedAmount(
                    contractMap.get(p.getProjectId()),
                    stagesByProject.getOrDefault(p.getProjectId(), Collections.emptyList())));
        }

        BigDecimal totalCollected = BigDecimal.ZERO;
        if (!projectIds.isEmpty()) {
            Map<Long, BigDecimal> collected = loadCollectedAmountByProjects(projectIds);
            totalCollected = collected.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal rate = totalExpected.signum() > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100))
                    .divide(totalExpected, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal pending = totalExpected.subtract(totalCollected);
        if (pending.signum() < 0) pending = BigDecimal.ZERO;

        return CollectionStatisticsVo.builder()
                .totalExpected(totalExpected)
                .totalCollected(totalCollected)
                .overallRate(rate)
                .totalPending(pending)
                .build();
    }

    // ==================== 详情 ====================

    @Override
    public CollectionDetailVo getCollectionDetail(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目ID不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }

        // 汇总
        ProjectType type = project.getProjectType() != null
                ? projectTypeService.getProjectTypeById(project.getProjectType())
                : null;
        BigDecimal collected = sumCollectedAmount(projectId);
        CollectionSummaryVo summary = buildSummary(project, type, collected);

        // 阶段维度
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(projectId);
        Contract contract = contractService.getContractByProjectId(projectId);
        BigDecimal contractAmount = contract != null && contract.getContractAmount() != null
                ? contract.getContractAmount() : BigDecimal.ZERO;

        Map<Long, BigDecimal> collectedByStage = loadCollectedAmountByStages(projectId);

        List<CollectionDetailVo.StageCollectionVo> stageVos = new ArrayList<>();
        if (stages != null) {
            for (ProjectStage s : stages) {
                BigDecimal ratio = s.getStageOutput() != null ? s.getStageOutput() : BigDecimal.ZERO;
                BigDecimal planAmount = contractAmount
                        .multiply(ratio)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal actualAmount = collectedByStage.getOrDefault(s.getProjectStageId(), BigDecimal.ZERO);

                stageVos.add(CollectionDetailVo.StageCollectionVo.builder()
                        .projectStageId(s.getProjectStageId())
                        .stageName(s.getStageName())
                        .stageOutput(ratio)
                        .planAmount(planAmount)
                        .actualAmount(actualAmount)
                        .status(deriveStatus(planAmount, actualAmount))
                        .build());
            }
        }

        // 原始记录
        LambdaQueryWrapper<CollectionRecord> rw = new LambdaQueryWrapper<>();
        rw.eq(CollectionRecord::getProjectId, projectId)
                .orderByDesc(CollectionRecord::getCollectDate)
                .orderByDesc(CollectionRecord::getCreatedTime);
        List<CollectionRecord> rawRecords = collectionRecordMapper.selectList(rw);
        List<CollectionRecordVo> records = rawRecords.stream()
                .map(r -> toRecordVo(r, project, findStage(stages, r.getProjectStageId())))
                .collect(Collectors.toList());

        return CollectionDetailVo.builder()
                .summary(summary)
                .stageCollections(stageVos)
                .records(records)
                .build();
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCollectionRecord(CreateCollectionRecordDto dto, Long userId) {
        if (dto == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (dto.getProjectId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "回款金额必须大于 0");
        }
        if (dto.getCollectDate() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择回款日期");
        }
        if (projectMapper.selectById(dto.getProjectId()) == null) {
            throw new BusinessException(ErrorType.PROJECT_CANNOT_NULL);
        }
        if (dto.getProjectStageId() != null) {
            ProjectStage stage = projectStageService.getProjectStageById(dto.getProjectStageId());
            if (stage == null || !dto.getProjectId().equals(stage.getProjectId())) {
                throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
            }
        }

        CollectionRecord record = CollectionRecord.builder()
                .projectId(dto.getProjectId())
                .projectStageId(dto.getProjectStageId())
                .amount(dto.getAmount())
                .collectDate(dto.getCollectDate())
                .voucherFileId(dto.getVoucherFileId())
                .remark(dto.getRemark())
                .recordUserId(userId)
                .build();
        collectionRecordMapper.insert(record);
        return record.getCollectionRecordId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCollectionRecord(UpdateCollectionRecordDto dto) {
        if (dto == null || dto.getCollectionRecordId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "记录ID不能为空");
        }
        CollectionRecord existing = collectionRecordMapper.selectById(dto.getCollectionRecordId());
        if (existing == null) {
            throw new BusinessException(ErrorType.COLLECTION_RECORD_NOT_FOUND);
        }

        if (dto.getAmount() != null) {
            if (dto.getAmount().signum() <= 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "回款金额必须大于 0");
            }
            existing.setAmount(dto.getAmount());
        }
        if (dto.getCollectDate() != null) existing.setCollectDate(dto.getCollectDate());
        if (dto.getProjectStageId() != null) {
            ProjectStage stage = projectStageService.getProjectStageById(dto.getProjectStageId());
            if (stage == null || !existing.getProjectId().equals(stage.getProjectId())) {
                throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
            }
            existing.setProjectStageId(dto.getProjectStageId());
        }
        if (dto.getVoucherFileId() != null) existing.setVoucherFileId(dto.getVoucherFileId());
        if (dto.getRemark() != null) existing.setRemark(dto.getRemark());

        collectionRecordMapper.updateById(existing);
    }

    @Override
    public void deleteCollectionRecord(Long collectionRecordId) {
        if (collectionRecordId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL);
        }
        if (collectionRecordMapper.selectById(collectionRecordId) == null) {
            throw new BusinessException(ErrorType.COLLECTION_RECORD_NOT_FOUND);
        }
        collectionRecordMapper.deleteById(collectionRecordId);
    }

    // ==================== 私有辅助 ====================

    private CollectionSummaryVo buildSummary(Project project, ProjectType type, BigDecimal collected) {
        Contract contract = contractService.getContractByProjectId(project.getProjectId());
        BigDecimal contractAmount = contract != null && contract.getContractAmount() != null
                ? contract.getContractAmount() : BigDecimal.ZERO;
        BigDecimal expected = calcExpectedAmount(project.getProjectId(), contract);

        BigDecimal rate = expected.signum() > 0
                ? collected.multiply(BigDecimal.valueOf(100))
                    .divide(expected, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String managerName = findManagerName(project.getProjectId());
        String completedPhases = describeCompletedPhases(project.getProjectId());

        return CollectionSummaryVo.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .projectCode(project.getProjectCode())
                .projectTypeCode(type != null ? type.getProjectTypeCode() : null)
                .projectTypeName(type != null ? type.getProjectTypeName() : null)
                .managerName(managerName)
                .contractAmount(contractAmount)
                .completedPhases(completedPhases)
                .expectedAmount(expected)
                .collectedAmount(collected)
                .collectionRate(rate)
                .collectionStatus(deriveStatus(expected, collected))
                .build();
    }

    /** 应收额：合同金额 × 已完成阶段产值比例之和 */
    private BigDecimal calcExpectedAmount(Contract contract, List<ProjectStage> stages) {
        if (contract == null || contract.getContractAmount() == null) return BigDecimal.ZERO;
        if (stages == null || stages.isEmpty()) return BigDecimal.ZERO;

        BigDecimal completedRatioSum = stages.stream()
                .filter(s -> STAGE_COMPLETED_STATUSES.contains(s.getStageStatus()))
                .map(s -> s.getStageOutput() != null ? s.getStageOutput() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return contract.getContractAmount()
                .multiply(completedRatioSum)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcExpectedAmount(Long projectId, Contract contract) {
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(projectId);
        return calcExpectedAmount(contract, stages);
    }

    /** 按项目 ID 集合一次性拉取合同，避免 N+1 */
    private Map<Long, Contract> loadContractsByProjectIds(Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Collections.emptyMap();
        Map<Long, Contract> result = new HashMap<>();
        for (Long pid : projectIds) {
            Contract c = contractService.getContractByProjectId(pid);
            if (c != null) result.put(pid, c);
        }
        return result;
    }

    /** 按项目 ID 集合一次性拉取阶段 */
    private Map<Long, List<ProjectStage>> loadStagesByProjectIds(Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Collections.emptyMap();
        Map<Long, List<ProjectStage>> result = new HashMap<>();
        for (Long pid : projectIds) {
            List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(pid);
            if (stages != null) result.put(pid, stages);
        }
        return result;
    }

    /** 按项目汇总已收金额 */
    private Map<Long, BigDecimal> loadCollectedAmountByProjects(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<CollectionRecord> w = new LambdaQueryWrapper<>();
        w.in(CollectionRecord::getProjectId, projectIds);
        List<CollectionRecord> records = collectionRecordMapper.selectList(w);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (CollectionRecord r : records) {
            BigDecimal amt = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
            result.merge(r.getProjectId(), amt, BigDecimal::add);
        }
        return result;
    }

    private BigDecimal sumCollectedAmount(Long projectId) {
        LambdaQueryWrapper<CollectionRecord> w = new LambdaQueryWrapper<>();
        w.eq(CollectionRecord::getProjectId, projectId);
        return collectionRecordMapper.selectList(w).stream()
                .map(CollectionRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, BigDecimal> loadCollectedAmountByStages(Long projectId) {
        LambdaQueryWrapper<CollectionRecord> w = new LambdaQueryWrapper<>();
        w.eq(CollectionRecord::getProjectId, projectId).isNotNull(CollectionRecord::getProjectStageId);
        List<CollectionRecord> records = collectionRecordMapper.selectList(w);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (CollectionRecord r : records) {
            BigDecimal amt = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
            result.merge(r.getProjectStageId(), amt, BigDecimal::add);
        }
        return result;
    }

    private Map<Long, ProjectType> loadProjectTypeMap(List<Project> projects) {
        Set<Long> typeIds = projects.stream()
                .map(Project::getProjectType)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProjectType> result = new HashMap<>();
        for (Long id : typeIds) {
            ProjectType t = projectTypeService.getProjectTypeById(id);
            if (t != null) result.put(id, t);
        }
        return result;
    }

    private String findManagerName(Long projectId) {
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(projectId);
        if (members == null) return null;
        return members.stream()
                .filter(m -> ROLE_PROJECT_MANAGER.equals(m.getProjectRole()))
                .findFirst()
                .map(m -> {
                    SysUser u = sysUserMapper.selectById(m.getUserId());
                    return u != null ? (u.getRealName() != null ? u.getRealName() : u.getUsername()) : null;
                })
                .orElse(null);
    }

    /** 已完成阶段拼接描述，如 "1-3" 或 "1,3" */
    private String describeCompletedPhases(Long projectId) {
        List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(projectId);
        if (stages == null || stages.isEmpty()) return "-";
        List<Integer> completedIdx = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            if (STAGE_COMPLETED_STATUSES.contains(stages.get(i).getStageStatus())) {
                completedIdx.add(i + 1);
            }
        }
        if (completedIdx.isEmpty()) return "-";
        // 检查是否连续
        boolean sequential = true;
        for (int i = 1; i < completedIdx.size(); i++) {
            if (completedIdx.get(i) != completedIdx.get(i - 1) + 1) {
                sequential = false;
                break;
            }
        }
        if (sequential && completedIdx.size() > 1) {
            return completedIdx.get(0) + "-" + completedIdx.get(completedIdx.size() - 1);
        }
        return completedIdx.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 状态推断：应收 vs 已收 */
    private int deriveStatus(BigDecimal expected, BigDecimal collected) {
        BigDecimal safeExpected = expected != null ? expected : BigDecimal.ZERO;
        BigDecimal safeCollected = collected != null ? collected : BigDecimal.ZERO;
        if (safeCollected.signum() <= 0) return 0; // 未回款
        if (safeCollected.compareTo(safeExpected) >= 0) return 2;  // 已回款
        return 1; // 部分回款
    }

    private ProjectStage findStage(List<ProjectStage> stages, Long stageId) {
        if (stages == null || stageId == null) return null;
        return stages.stream().filter(s -> stageId.equals(s.getProjectStageId())).findFirst().orElse(null);
    }

    private CollectionRecordVo toRecordVo(CollectionRecord r, Project project, ProjectStage stage) {
        String userName = null;
        if (r.getRecordUserId() != null) {
            SysUser u = sysUserMapper.selectById(r.getRecordUserId());
            if (u != null) userName = u.getRealName() != null ? u.getRealName() : u.getUsername();
        }
        return CollectionRecordVo.builder()
                .collectionRecordId(r.getCollectionRecordId())
                .projectId(r.getProjectId())
                .projectName(project != null ? project.getProjectName() : null)
                .projectCode(project != null ? project.getProjectCode() : null)
                .projectStageId(r.getProjectStageId())
                .stageName(stage != null ? stage.getStageName() : null)
                .amount(r.getAmount())
                .collectDate(r.getCollectDate())
                .voucherFileId(r.getVoucherFileId())
                .remark(r.getRemark())
                .recordUserId(r.getRecordUserId())
                .recordUserName(userName)
                .createdTime(r.getCreatedTime())
                .updatedTime(r.getUpdatedTime())
                .build();
    }
}
