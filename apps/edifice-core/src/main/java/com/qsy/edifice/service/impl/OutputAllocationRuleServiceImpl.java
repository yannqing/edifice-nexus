package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qsy.edifice.domain.dto.SaveOutputAllocationRuleDto;
import com.qsy.edifice.domain.entity.OutputAllocationRuleItem;
import com.qsy.edifice.domain.entity.OutputAllocationRulePoolRate;
import com.qsy.edifice.domain.entity.OutputAllocationRuleVersion;
import com.qsy.edifice.domain.entity.OutputValueWorkPool;
import com.qsy.edifice.domain.entity.Project;
import com.qsy.edifice.domain.entity.ProjectStage;
import com.qsy.edifice.domain.entity.ProjectStageTemplate;
import com.qsy.edifice.domain.entity.ProjectType;
import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.domain.vo.OutputAllocationRuleVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputAllocationRuleItemMapper;
import com.qsy.edifice.mapper.OutputAllocationRulePoolRateMapper;
import com.qsy.edifice.mapper.OutputAllocationRuleVersionMapper;
import com.qsy.edifice.mapper.OutputValueWorkPoolMapper;
import com.qsy.edifice.mapper.ProjectStageMapper;
import com.qsy.edifice.mapper.ProjectStageTemplateMapper;
import com.qsy.edifice.service.OutputAllocationRuleService;
import com.qsy.edifice.service.ProjectService;
import com.qsy.edifice.service.ProjectStageService;
import com.qsy.edifice.service.ProjectStageTemplateService;
import com.qsy.edifice.service.ProjectTypeService;
import com.qsy.edifice.service.support.OutputAllocationCalculator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OutputAllocationRuleServiceImpl implements OutputAllocationRuleService {

    private static final BigDecimal BD_100 = new BigDecimal("100");
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.01");
    private static final Set<Integer> WORK_TYPES = Set.of(0, 1, 2);
    private static final Map<Integer, String> WORK_TYPE_NAMES = Map.of(
            0, "管理工作", 1, "基础工作", 2, "智励工作"
    );

    @Resource
    private OutputAllocationRuleVersionMapper ruleVersionMapper;

    @Resource
    private OutputAllocationRuleItemMapper ruleItemMapper;

    @Resource
    private OutputAllocationRulePoolRateMapper poolRateMapper;

    @Resource
    private OutputValueWorkPoolMapper workPoolMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ProjectStageTemplateService stageTemplateService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectStageTemplateMapper stageTemplateMapper;

    @Resource
    private ProjectStageMapper projectStageMapper;

    @Override
    public OutputAllocationRuleVo getActiveRule(Long projectTypeId) {
        if (projectTypeId == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "项目类型不能为空");
        }
        ProjectType projectType = projectTypeService.getProjectTypeById(projectTypeId);
        if (projectType == null) {
            throw new BusinessException(ErrorType.PROJECT_TYPE_NOT_FOUND);
        }
        OutputAllocationRuleVersion version = findActiveVersion(projectTypeId);
        if (version == null) {
            return toDraftVo(projectType);
        }
        return toVo(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutputAllocationRuleVo saveRule(Long projectTypeId,
                                           SaveOutputAllocationRuleDto dto,
                                           Long userId) {
        List<ProjectStageTemplate> templates = validateRule(projectTypeId, dto);
        syncStageOutputs(projectTypeId, dto, templates);

        LambdaQueryWrapper<OutputAllocationRuleVersion> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(OutputAllocationRuleVersion::getProjectTypeId, projectTypeId)
                .orderByDesc(OutputAllocationRuleVersion::getVersionNo)
                .last("LIMIT 1");
        OutputAllocationRuleVersion latest = ruleVersionMapper.selectOne(versionWrapper);
        int nextVersion = latest == null ? 1 : latest.getVersionNo() + 1;

        LambdaUpdateWrapper<OutputAllocationRuleVersion> deactivate = new LambdaUpdateWrapper<>();
        deactivate.eq(OutputAllocationRuleVersion::getProjectTypeId, projectTypeId)
                .eq(OutputAllocationRuleVersion::getStatus, 1)
                .set(OutputAllocationRuleVersion::getStatus, 0);
        ruleVersionMapper.update(null, deactivate);

        OutputAllocationRuleVersion version = OutputAllocationRuleVersion.builder()
                .projectTypeId(projectTypeId)
                .versionNo(nextVersion)
                .employeePoolRate(dto.getEmployeePoolRate())
                .companyBaseRate(dto.getCompanyBaseRate())
                .status(1)
                .createdBy(userId)
                .effectiveTime(LocalDateTime.now())
                .build();
        ruleVersionMapper.insert(version);

        for (SaveOutputAllocationRuleDto.WorkRate workRate : dto.getWorkRates()) {
            poolRateMapper.insert(OutputAllocationRulePoolRate.builder()
                    .ruleVersionId(version.getRuleVersionId())
                    .workType(workRate.getWorkType())
                    .grossRate(workRate.getGrossRate())
                    .projectRate(workRate.getProjectRate())
                    .companyRate(workRate.getCompanyRate())
                    .build());
        }

        for (SaveOutputAllocationRuleDto.StageRule stage : dto.getStages()) {
            for (SaveOutputAllocationRuleDto.WorkRule workRule : stage.getWorkRules()) {
                ruleItemMapper.insert(OutputAllocationRuleItem.builder()
                        .ruleVersionId(version.getRuleVersionId())
                        .stageName(stage.getStageName().trim())
                        .stageOrder(stage.getStageOrder())
                        .workType(workRule.getWorkType())
                        .workWeight(workRule.getWorkWeight())
                        .projectCapRate(workRule.getProjectCapRate())
                        .build());
            }
        }
        return toVo(version);
    }

    @Override
    public OutputAllocationContext calculate(Long projectId,
                                             Long projectStageId,
                                             BigDecimal totalAmount) {
        Project project = projectService.getProjectById(projectId);
        ProjectStage stage = projectStageService.getProjectStageById(projectStageId);
        if (project == null || stage == null || !projectId.equals(stage.getProjectId())) {
            throw new BusinessException(ErrorType.STAGE_NOT_FOUND);
        }
        OutputAllocationRuleVersion version = findActiveVersion(project.getProjectType());
        if (version == null) {
            throw new BusinessException(ErrorType.OPERATION_FAILED, "当前项目类型未配置产值分配规则，请联系管理员");
        }

        LambdaQueryWrapper<OutputAllocationRuleItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OutputAllocationRuleItem::getRuleVersionId, version.getRuleVersionId())
                .eq(OutputAllocationRuleItem::getStageName, stage.getStageName())
                .orderByAsc(OutputAllocationRuleItem::getWorkType);
        List<OutputAllocationRuleItem> items = ruleItemMapper.selectList(itemWrapper);
        if (items.size() != WORK_TYPES.size()) {
            List<ProjectStage> projectStages = projectStageService.getProjectStagesByProjectId(projectId);
            int stageIndex = -1;
            for (int index = 0; index < projectStages.size(); index++) {
                if (projectStageId.equals(projectStages.get(index).getProjectStageId())) {
                    stageIndex = index;
                    break;
                }
            }
            if (stageIndex >= 0) {
                LambdaQueryWrapper<OutputAllocationRuleItem> orderFallback = new LambdaQueryWrapper<>();
                orderFallback.eq(OutputAllocationRuleItem::getRuleVersionId, version.getRuleVersionId())
                        .eq(OutputAllocationRuleItem::getStageOrder, stageIndex + 1)
                        .orderByAsc(OutputAllocationRuleItem::getWorkType);
                items = ruleItemMapper.selectList(orderFallback);
            }
        }
        if (items.size() != WORK_TYPES.size()) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "阶段[" + stage.getStageName() + "]未配置完整的工作类型权重");
        }

        LambdaQueryWrapper<OutputAllocationRulePoolRate> rateWrapper = new LambdaQueryWrapper<>();
        rateWrapper.eq(OutputAllocationRulePoolRate::getRuleVersionId, version.getRuleVersionId())
                .orderByAsc(OutputAllocationRulePoolRate::getWorkType);
        List<OutputAllocationRulePoolRate> rates = poolRateMapper.selectList(rateWrapper);
        if (rates.size() != WORK_TYPES.size()) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "当前项目类型未配置完整的固定汇总比例，请联系管理员");
        }

        List<OutputAllocationCalculator.StageWeightRule> stageRules = items.stream()
                .map(item -> new OutputAllocationCalculator.StageWeightRule(
                        item.getWorkType(), item.getWorkWeight()))
                .toList();
        List<OutputAllocationCalculator.PoolRateRule> poolRateRules = rates.stream()
                .map(rate -> new OutputAllocationCalculator.PoolRateRule(
                        rate.getWorkType(), rate.getGrossRate(), rate.getProjectRate(), rate.getCompanyRate()))
                .toList();
        return OutputAllocationCalculator.calculate(
                totalAmount,
                version.getRuleVersionId(),
                version.getVersionNo(),
                version.getEmployeePoolRate(),
                version.getCompanyBaseRate(),
                stageRules,
                poolRateRules
        );
    }

    @Override
    public List<OutputValueWorkPool> getWorkPools(Long outputValueId) {
        LambdaQueryWrapper<OutputValueWorkPool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutputValueWorkPool::getOutputValueId, outputValueId)
                .orderByAsc(OutputValueWorkPool::getWorkType);
        return workPoolMapper.selectList(wrapper);
    }

    private OutputAllocationRuleVersion findActiveVersion(Long projectTypeId) {
        LambdaQueryWrapper<OutputAllocationRuleVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutputAllocationRuleVersion::getProjectTypeId, projectTypeId)
                .eq(OutputAllocationRuleVersion::getStatus, 1)
                .orderByDesc(OutputAllocationRuleVersion::getVersionNo)
                .last("LIMIT 1");
        return ruleVersionMapper.selectOne(wrapper);
    }

    private List<ProjectStageTemplate> validateRule(Long projectTypeId, SaveOutputAllocationRuleDto dto) {
        ProjectType projectType = projectTypeService.getProjectTypeById(projectTypeId);
        if (projectType == null) {
            throw new BusinessException(ErrorType.PROJECT_TYPE_NOT_FOUND);
        }
        if (dto == null || dto.getEmployeePoolRate() == null || dto.getCompanyBaseRate() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "员工池和公司基础比例不能为空");
        }
        if (dto.getEmployeePoolRate().signum() < 0 || dto.getCompanyBaseRate().signum() < 0
                || dto.getEmployeePoolRate().add(dto.getCompanyBaseRate()).subtract(BD_100).abs()
                .compareTo(RATE_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "员工池比例与公司基础比例合计必须为100%");
        }
        validateWorkRates(dto);
        if (dto.getStages() == null || dto.getStages().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "阶段分配规则不能为空");
        }

        List<ProjectStageTemplate> templates = stageTemplateService.getEnabledByProjectTypeId(projectTypeId);
        Set<String> expectedStages = new HashSet<>();
        for (ProjectStageTemplate template : templates) {
            expectedStages.add(template.getStageName());
        }
        Set<String> submittedStages = new HashSet<>();
        Set<Integer> submittedOrders = new HashSet<>();
        BigDecimal stageOutputSum = BigDecimal.ZERO;

        for (SaveOutputAllocationRuleDto.StageRule stage : dto.getStages()) {
            if (stage == null || !StringUtils.hasText(stage.getStageName()) || stage.getStageOrder() == null
                    || stage.getStageOrder() <= 0 || !submittedOrders.add(stage.getStageOrder())) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "阶段名称和顺序不能为空");
            }
            String stageName = stage.getStageName().trim();
            if (!submittedStages.add(stageName)) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "阶段[" + stageName + "]重复配置");
            }
            if (stage.getStageOutput() == null || stage.getStageOutput().signum() < 0
                    || stage.getStageOutput().compareTo(BD_100) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID,
                        "阶段[" + stageName + "]产值比例应在0-100之间");
            }
            stageOutputSum = stageOutputSum.add(stage.getStageOutput());
            if (stage.getWorkRules() == null || stage.getWorkRules().size() != WORK_TYPES.size()) {
                throw new BusinessException(ErrorType.ARGS_INVALID,
                        "阶段[" + stageName + "]必须配置管理、基础、智励三类工作");
            }
            Set<Integer> workTypes = new HashSet<>();
            BigDecimal weightSum = BigDecimal.ZERO;
            for (SaveOutputAllocationRuleDto.WorkRule workRule : stage.getWorkRules()) {
                if (workRule == null || !WORK_TYPES.contains(workRule.getWorkType())
                        || !workTypes.add(workRule.getWorkType()) || workRule.getWorkWeight() == null
                        || workRule.getWorkWeight().signum() < 0 || workRule.getWorkWeight().compareTo(BD_100) > 0) {
                    throw new BusinessException(ErrorType.ARGS_INVALID,
                            "阶段[" + stageName + "]工作类型或权重无效");
                }
                if (workRule.getProjectCapRate() != null
                        && (workRule.getProjectCapRate().signum() < 0
                        || workRule.getProjectCapRate().compareTo(BD_100) > 0)) {
                    throw new BusinessException(ErrorType.ARGS_INVALID,
                            "阶段[" + stageName + "]项目分配上限应在0-100之间");
                }
                weightSum = weightSum.add(workRule.getWorkWeight());
            }
            if (weightSum.subtract(BD_100).abs().compareTo(RATE_TOLERANCE) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID,
                        "阶段[" + stageName + "]三类工作权重合计必须为100%，当前为" + weightSum + "%");
            }
        }
        if (!expectedStages.equals(submittedStages)) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "规则阶段必须与当前启用的阶段模板完全一致");
        }
        if (stageOutputSum.subtract(BD_100).abs().compareTo(RATE_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    "各阶段产值比例合计必须为100%，当前为" + stageOutputSum + "%");
        }
        return templates;
    }

    private void syncStageOutputs(Long projectTypeId,
                                  SaveOutputAllocationRuleDto dto,
                                  List<ProjectStageTemplate> templates) {
        Map<String, ProjectStageTemplate> templateByName = new HashMap<>();
        for (ProjectStageTemplate template : templates) {
            templateByName.put(template.getStageName(), template);
        }
        for (SaveOutputAllocationRuleDto.StageRule stage : dto.getStages()) {
            String stageName = stage.getStageName().trim();
            ProjectStageTemplate template = templateByName.get(stageName);
            stageTemplateMapper.updateById(ProjectStageTemplate.builder()
                    .stageId(template.getStageId())
                    .stageOutput(stage.getStageOutput())
                    .build());
            projectStageMapper.updateUnallocatedStageOutput(
                    projectTypeId, stageName, stage.getStageOutput());
        }
    }

    private void validateWorkRates(SaveOutputAllocationRuleDto dto) {
        if (dto.getWorkRates() == null || dto.getWorkRates().size() != WORK_TYPES.size()) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "必须配置管理、基础、智励三类固定汇总比例");
        }
        Set<Integer> submittedTypes = new HashSet<>();
        BigDecimal grossTotal = BigDecimal.ZERO;
        for (SaveOutputAllocationRuleDto.WorkRate rate : dto.getWorkRates()) {
            if (rate == null || !WORK_TYPES.contains(rate.getWorkType())
                    || !submittedTypes.add(rate.getWorkType())
                    || rate.getGrossRate() == null || rate.getProjectRate() == null || rate.getCompanyRate() == null
                    || rate.getGrossRate().signum() < 0 || rate.getProjectRate().signum() < 0
                    || rate.getCompanyRate().signum() < 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "固定汇总比例配置无效");
            }
            if (rate.getProjectRate().add(rate.getCompanyRate()).subtract(rate.getGrossRate()).abs()
                    .compareTo(RATE_TOLERANCE) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID,
                        WORK_TYPE_NAMES.get(rate.getWorkType()) + "占总收入比例必须等于项目与公司比例之和");
            }
            grossTotal = grossTotal.add(rate.getGrossRate());
        }
        if (grossTotal.subtract(dto.getEmployeePoolRate()).abs().compareTo(RATE_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    "三类工作占总收入比例合计必须等于名义员工池比例");
        }
    }

    private OutputAllocationRuleVo toVo(OutputAllocationRuleVersion version) {
        ProjectType projectType = projectTypeService.getProjectTypeById(version.getProjectTypeId());
        List<OutputAllocationRuleVo.WorkRateVo> workRates = getWorkRateVos(
                version.getRuleVersionId(), projectType == null ? null : projectType.getProjectTypeCode());
        LambdaQueryWrapper<OutputAllocationRuleItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OutputAllocationRuleItem::getRuleVersionId, version.getRuleVersionId())
                .orderByAsc(OutputAllocationRuleItem::getStageOrder)
                .orderByAsc(OutputAllocationRuleItem::getWorkType);
        List<OutputAllocationRuleItem> items = ruleItemMapper.selectList(itemWrapper);

        Map<String, List<OutputAllocationRuleItem>> groupedByName = new HashMap<>();
        Map<Integer, List<OutputAllocationRuleItem>> groupedByOrder = new HashMap<>();
        for (OutputAllocationRuleItem item : items) {
            groupedByName.computeIfAbsent(item.getStageName(), key -> new ArrayList<>()).add(item);
            groupedByOrder.computeIfAbsent(item.getStageOrder(), key -> new ArrayList<>()).add(item);
        }
        List<ProjectStageTemplate> templates = stageTemplateService.getEnabledByProjectTypeId(version.getProjectTypeId());
        List<OutputAllocationRuleVo.StageRuleVo> stages = new ArrayList<>();
        for (int index = 0; index < templates.size(); index++) {
            ProjectStageTemplate template = templates.get(index);
            int stageOrder = index + 1;
            List<OutputAllocationRuleItem> stageItems = groupedByName.get(template.getStageName());
            if (stageItems == null || stageItems.size() != WORK_TYPES.size()) {
                stageItems = groupedByOrder.get(stageOrder);
            }
            List<OutputAllocationRuleVo.WorkRuleVo> workRules = stageItems == null
                    || stageItems.size() != WORK_TYPES.size()
                    ? defaultWorkRules()
                    : stageItems.stream()
                    .sorted(Comparator.comparing(OutputAllocationRuleItem::getWorkType))
                    .map(item -> new OutputAllocationRuleVo.WorkRuleVo(
                            item.getWorkType(),
                            WORK_TYPE_NAMES.get(item.getWorkType()),
                            item.getWorkWeight(),
                            item.getProjectCapRate()))
                    .toList();
            stages.add(new OutputAllocationRuleVo.StageRuleVo(
                    template.getStageName(), stageOrder, template.getStageOutput(), workRules));
        }

        return new OutputAllocationRuleVo(
                version.getRuleVersionId(),
                version.getProjectTypeId(),
                projectType == null ? null : projectType.getProjectTypeCode(),
                projectType == null ? null : projectType.getProjectTypeName(),
                version.getVersionNo(),
                version.getEmployeePoolRate(),
                version.getCompanyBaseRate(),
                version.getEffectiveTime(),
                workRates,
                stages
        );
    }

    private OutputAllocationRuleVo toDraftVo(ProjectType projectType) {
        List<ProjectStageTemplate> templates = stageTemplateService.getEnabledByProjectTypeId(
                projectType.getProjectTypeId());
        List<OutputAllocationRuleVo.StageRuleVo> stages = new ArrayList<>();
        for (int index = 0; index < templates.size(); index++) {
            ProjectStageTemplate template = templates.get(index);
            stages.add(new OutputAllocationRuleVo.StageRuleVo(
                    template.getStageName(), index + 1, template.getStageOutput(), defaultWorkRules()));
        }
        return new OutputAllocationRuleVo(
                null,
                projectType.getProjectTypeId(),
                projectType.getProjectTypeCode(),
                projectType.getProjectTypeName(),
                0,
                new BigDecimal("40"),
                new BigDecimal("60"),
                null,
                defaultWorkRates(projectType.getProjectTypeCode()),
                stages
        );
    }

    private List<OutputAllocationRuleVo.WorkRuleVo> defaultWorkRules() {
        return List.of(
                new OutputAllocationRuleVo.WorkRuleVo(0, WORK_TYPE_NAMES.get(0), BigDecimal.ZERO, new BigDecimal("4")),
                new OutputAllocationRuleVo.WorkRuleVo(1, WORK_TYPE_NAMES.get(1), new BigDecimal("100"), null),
                new OutputAllocationRuleVo.WorkRuleVo(2, WORK_TYPE_NAMES.get(2), BigDecimal.ZERO, new BigDecimal("4"))
        );
    }

    private List<OutputAllocationRuleVo.WorkRateVo> getWorkRateVos(Long ruleVersionId, String projectTypeCode) {
        LambdaQueryWrapper<OutputAllocationRulePoolRate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutputAllocationRulePoolRate::getRuleVersionId, ruleVersionId)
                .orderByAsc(OutputAllocationRulePoolRate::getWorkType);
        List<OutputAllocationRulePoolRate> rates = poolRateMapper.selectList(wrapper);
        if (rates.size() != WORK_TYPES.size()) {
            return defaultWorkRates(projectTypeCode);
        }
        return rates.stream()
                .map(rate -> new OutputAllocationRuleVo.WorkRateVo(
                        rate.getWorkType(),
                        WORK_TYPE_NAMES.get(rate.getWorkType()),
                        rate.getGrossRate(),
                        rate.getProjectRate(),
                        rate.getCompanyRate()))
                .toList();
    }

    private List<OutputAllocationRuleVo.WorkRateVo> defaultWorkRates(String projectTypeCode) {
        return switch (projectTypeCode == null ? "" : projectTypeCode) {
            case "A" -> workRates("8.06", "4", "4.06", "20.28", "20.28", "0", "11.66", "4", "7.66");
            case "B" -> workRates("8.20", "4", "4.20", "20", "20", "0", "11.80", "4", "7.80");
            case "C" -> workRates("8", "4", "4", "20.80", "20.80", "0", "11.20", "4", "7.20");
            case "D" -> workRates("9.20", "4", "5.20", "18", "18", "0", "12.80", "4", "8.80");
            case "E" -> workRates("7.60", "4", "3.60", "20.40", "20.40", "0", "12", "4", "8");
            default -> workRates("0", "0", "0", "40", "40", "0", "0", "0", "0");
        };
    }

    private List<OutputAllocationRuleVo.WorkRateVo> workRates(String managementGross,
                                                               String managementProject,
                                                               String managementCompany,
                                                               String baseGross,
                                                               String baseProject,
                                                               String baseCompany,
                                                               String wisdomGross,
                                                               String wisdomProject,
                                                               String wisdomCompany) {
        return List.of(
                workRate(0, managementGross, managementProject, managementCompany),
                workRate(1, baseGross, baseProject, baseCompany),
                workRate(2, wisdomGross, wisdomProject, wisdomCompany)
        );
    }

    private OutputAllocationRuleVo.WorkRateVo workRate(Integer workType,
                                                         String grossRate,
                                                         String projectRate,
                                                         String companyRate) {
        return new OutputAllocationRuleVo.WorkRateVo(
                workType,
                WORK_TYPE_NAMES.get(workType),
                new BigDecimal(grossRate),
                new BigDecimal(projectRate),
                new BigDecimal(companyRate));
    }
}
