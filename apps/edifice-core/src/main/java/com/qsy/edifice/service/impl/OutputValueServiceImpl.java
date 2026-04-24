package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.CreateOutputValueDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.OutputValueVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.OutputValueDistributionMapper;
import com.qsy.edifice.mapper.OutputValueMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 产值分配服务实现（v0.2）
 *
 * 核心公式：
 *   companyReserve = totalAmount × 40%
 *   personalPool   = totalAmount × 60%
 *   planned_i      = personalPool × allocRatio_i / 100
 *   actual_i       = planned_i × completionRatio_i / 100       （在职员工）
 *   leaderExtra   += planned_i - actual_i                      （降档差额）
 *   otherAmount   += planned_i                                 （离职成员未发）
 * 守恒：companyReserve + Σ actual + leaderExtra + otherAmount = totalAmount
 */
@Slf4j
@Service
public class OutputValueServiceImpl implements OutputValueService {

    private static final Long ROLE_MANAGER_ID = 101L;

    /** 公司留存比例（%） */
    private static final BigDecimal COMPANY_RESERVE_RATE = new BigDecimal("40");
    /** 个人池比例（%） */
    private static final BigDecimal PERSONAL_POOL_RATE = new BigDecimal("60");
    private static final BigDecimal BD_100 = new BigDecimal("100");
    /** 守恒校验容差（元），避免 BigDecimal 舍入带来的微分误差误报 */
    private static final BigDecimal INVARIANT_TOLERANCE = new BigDecimal("0.05");
    /** 分配比例合计校验容差（百分点） */
    private static final BigDecimal ALLOC_TOLERANCE = new BigDecimal("0.01");

    /** 分配类型常量 */
    private static final int DIST_TYPE_NORMAL = 0;
    private static final int DIST_TYPE_DOWNGRADE = 1;
    private static final int DIST_TYPE_OTHER = 4;

    @Resource
    private OutputValueMapper outputValueMapper;

    @Resource
    private OutputValueDistributionMapper distributionMapper;

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectStageService projectStageService;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private SysUserMapper sysUserMapper;

    // ==================== 查询 ====================

    @Override
    public List<OutputValueVo> getOutputValueList(Integer status) {
        LambdaQueryWrapper<OutputValue> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(OutputValue::getStatus, status);
        }
        wrapper.orderByDesc(OutputValue::getCreatedTime);

        List<OutputValue> list = outputValueMapper.selectList(wrapper);
        return list.stream().map(this::convertToVo).collect(Collectors.toList());
    }

    // ==================== 创建（v0.2 新公式） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOutputValue(CreateOutputValueDto dto, Long userId) {
        if (dto.getProjectId() == null || dto.getProjectStageId() == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择项目和阶段");
        }
        if (dto.getTotalAmount() == null || dto.getTotalAmount().signum() <= 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID, "产值总额必须大于 0");
        }
        if (dto.getDistributions() == null || dto.getDistributions().isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请添加至少一条分配明细");
        }
        if (!StringUtils.hasText(dto.getQuarter())) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL, "请选择所属季度");
        }

        BigDecimal total = dto.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal companyReserve = total.multiply(COMPANY_RESERVE_RATE)
                .divide(BD_100, 2, RoundingMode.HALF_UP);
        BigDecimal personalPool = total.multiply(PERSONAL_POOL_RATE)
                .divide(BD_100, 2, RoundingMode.HALF_UP);

        // 校验分配比例合计 ≈ 100
        BigDecimal sumAllocRatio = dto.getDistributions().stream()
                .map(i -> i.getAllocRatio() == null ? BigDecimal.ZERO : i.getAllocRatio())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumAllocRatio.subtract(BD_100).abs().compareTo(ALLOC_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.ARGS_INVALID,
                    "分配比例合计应为 100%，当前为 " + sumAllocRatio.stripTrailingZeros().toPlainString() + "%");
        }

        // 逐条计算并构建分配明细
        BigDecimal leaderExtra = BigDecimal.ZERO;
        BigDecimal otherAmount = BigDecimal.ZERO;
        List<OutputValueDistribution> distEntities = new ArrayList<>();

        for (int i = 0; i < dto.getDistributions().size(); i++) {
            CreateOutputValueDto.DistributionItem item = dto.getDistributions().get(i);
            int rowNum = i + 1;

            if (item.getUserId() == null) {
                throw new BusinessException(ErrorType.ARGS_NOT_NULL, "第 " + rowNum + " 行分配对象为空");
            }
            BigDecimal alloc = item.getAllocRatio() == null ? BigDecimal.ZERO : item.getAllocRatio();
            BigDecimal completion = item.getCompletionRatio() == null ? BD_100 : item.getCompletionRatio();

            if (alloc.signum() < 0 || alloc.compareTo(BD_100) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "第 " + rowNum + " 行分配比例应在 0-100 之间");
            }
            if (completion.signum() < 0 || completion.compareTo(BD_100) > 0) {
                throw new BusinessException(ErrorType.ARGS_INVALID, "第 " + rowNum + " 行完成比例应在 0-100 之间");
            }

            // 在职快照：优先 DTO，其次查用户表
            int isActive = resolveIsActive(item.getUserId(), item.getIsActive());

            BigDecimal planned = personalPool.multiply(alloc)
                    .divide(BD_100, 2, RoundingMode.HALF_UP);

            int distType;
            BigDecimal actualAmount;

            if (isActive == 0) {
                // 离职：应得全部归"其他金额"
                distType = DIST_TYPE_OTHER;
                actualAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                otherAmount = otherAmount.add(planned);
            } else if (completion.compareTo(BD_100) < 0) {
                // 降档：实得 = planned × completion%，差额归领导
                distType = DIST_TYPE_DOWNGRADE;
                actualAmount = planned.multiply(completion)
                        .divide(BD_100, 2, RoundingMode.HALF_UP);
                leaderExtra = leaderExtra.add(planned.subtract(actualAmount));
            } else {
                // 正常全额
                distType = DIST_TYPE_NORMAL;
                actualAmount = planned;
            }

            distEntities.add(OutputValueDistribution.builder()
                    .userId(item.getUserId())
                    .workType(item.getWorkType() != null ? item.getWorkType() : 1)
                    .ratio(alloc)            // 兼容旧字段：沿用 allocRatio 值
                    .allocRatio(alloc)
                    .completionRatio(completion)
                    .distType(distType)
                    .isActive(isActive)
                    .amount(actualAmount)
                    .build());
        }

        // 守恒校验：companyReserve + Σ actual + leaderExtra + otherAmount ≈ total
        BigDecimal sumPersonal = distEntities.stream()
                .map(OutputValueDistribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = companyReserve.add(sumPersonal).add(leaderExtra).add(otherAmount);
        if (sum.subtract(total).abs().compareTo(INVARIANT_TOLERANCE) > 0) {
            throw new BusinessException(ErrorType.OPERATION_FAILED,
                    "金额守恒失败：公司留存 " + companyReserve + " + 员工实得 " + sumPersonal
                            + " + 领导兜底 " + leaderExtra + " + 其他 " + otherAmount
                            + " = " + sum + "，应为 " + total);
        }

        // 保存
        OutputValue ov = OutputValue.builder()
                .projectId(dto.getProjectId())
                .projectStageId(dto.getProjectStageId())
                .quarter(dto.getQuarter().trim())
                .totalAmount(total)
                .companyReserve(companyReserve)
                .leaderExtra(leaderExtra)
                .otherAmount(otherAmount)
                .subsidyAmount(dto.getSubsidyAmount() != null
                        ? dto.getSubsidyAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .status(0) // 待确认
                .submitUserId(userId)
                .submitTime(LocalDateTime.now())
                .build();
        outputValueMapper.insert(ov);

        for (OutputValueDistribution d : distEntities) {
            d.setOutputValueId(ov.getOutputValueId());
            distributionMapper.insert(d);
        }

        log.info("创建产值分配单 id={} 季度={} 合计={} 公司留存={} 员工实得={} 领导兜底={} 其他={}",
                ov.getOutputValueId(), ov.getQuarter(), total, companyReserve, sumPersonal, leaderExtra, otherAmount);

        return ov.getOutputValueId();
    }

    /** 在职状态：优先 DTO 传入，其次查 sys_user.employment_status，默认 1（在职） */
    private int resolveIsActive(Long userId, Integer override) {
        if (override != null) return override;
        SysUser u = sysUserMapper.selectById(userId);
        if (u != null && u.getEmploymentStatus() != null) {
            return u.getEmploymentStatus();
        }
        return 1;
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOutputValue(Long outputValueId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);
        if (ov.getStatus() != 0) throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法确认");

        ov.setStatus(1); // 待审核
        outputValueMapper.updateById(ov);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOutputValue(Long outputValueId, Long userId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);
        if (ov.getStatus() != 1) throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法审批");

        ov.setStatus(2); // 已审批
        ov.setApprovedTime(LocalDateTime.now());
        outputValueMapper.updateById(ov);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOutputValue(Long outputValueId) {
        OutputValue ov = outputValueMapper.selectById(outputValueId);
        if (ov == null) throw new BusinessException(ErrorType.OUTPUT_VALUE_NOT_FOUND);
        if (ov.getStatus() != 2) throw new BusinessException(ErrorType.OUTPUT_VALUE_STATUS_INVALID, "当前状态无法发放");

        ov.setStatus(3); // 已发放
        ov.setPaidTime(LocalDateTime.now());
        outputValueMapper.updateById(ov);
    }

    // ==================== 统计 ====================

    @Override
    public Map<String, Object> getStatistics() {
        List<OutputValue> all = outputValueMapper.selectList(null);
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingCount", all.stream().filter(o -> o.getStatus() == 0 || o.getStatus() == 1).count());
        stats.put("approvedCount", all.stream().filter(o -> o.getStatus() == 2).count());
        // "已发放"仅统计 status=3 的记录，作为现金动作统计
        stats.put("paidAmount", all.stream()
                .filter(o -> o.getStatus() == 3)
                .map(OutputValue::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("totalAmount", all.stream()
                .map(OutputValue::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return stats;
    }

    // ==================== VO 转换 ====================

    private OutputValueVo convertToVo(OutputValue ov) {
        OutputValueVo vo = new OutputValueVo();
        vo.setOutputValueId(ov.getOutputValueId());
        vo.setProjectId(ov.getProjectId());
        vo.setProjectStageId(ov.getProjectStageId());
        vo.setQuarter(ov.getQuarter());
        vo.setTotalAmount(ov.getTotalAmount());
        vo.setCompanyReserve(ov.getCompanyReserve());
        vo.setLeaderExtra(ov.getLeaderExtra());
        vo.setOtherAmount(ov.getOtherAmount());
        vo.setSubsidyAmount(ov.getSubsidyAmount());
        vo.setStatus(ov.getStatus());
        vo.setSubmitTime(ov.getSubmitTime());
        vo.setApprovedTime(ov.getApprovedTime());
        vo.setPaidTime(ov.getPaidTime());
        vo.setCreatedTime(ov.getCreatedTime());

        // 项目信息
        Project project = projectService.getProjectById(ov.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getProjectName());
            vo.setProjectCode(project.getProjectCode());
            if (project.getProjectType() != null) {
                ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
                if (type != null) vo.setProjectTypeName(type.getProjectTypeName());
            }
        }

        // 阶段信息
        ProjectStage stage = projectStageService.getProjectStageById(ov.getProjectStageId());
        if (stage != null) {
            vo.setStageName(stage.getStageName());
            vo.setStageOutput(stage.getStageOutput());
        }

        // 提交人
        if (ov.getSubmitUserId() != null) {
            SysUser user = sysUserMapper.selectById(ov.getSubmitUserId());
            if (user != null) vo.setSubmitUserName(user.getRealName());
        }

        // 分配明细
        LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
        distWrapper.eq(OutputValueDistribution::getOutputValueId, ov.getOutputValueId());
        List<OutputValueDistribution> dists = distributionMapper.selectList(distWrapper);

        // 项目成员角色（用于展示"项目经理/项目成员"）
        List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(ov.getProjectId());
        Map<Long, Long> userRoleMap = new HashMap<>();
        if (members != null) {
            for (ProjectMember m : members) {
                userRoleMap.put(m.getUserId(), m.getProjectRole());
            }
        }

        List<OutputValueVo.DistributionItemVo> distVos = dists.stream().map(d -> {
            OutputValueVo.DistributionItemVo item = new OutputValueVo.DistributionItemVo();
            item.setDistributionId(d.getDistributionId());
            item.setUserId(d.getUserId());
            item.setWorkType(d.getWorkType());
            item.setRatio(d.getRatio());
            item.setAllocRatio(d.getAllocRatio());
            item.setCompletionRatio(d.getCompletionRatio());
            item.setDistType(d.getDistType());
            item.setIsActive(d.getIsActive());
            item.setAmount(d.getAmount());

            SysUser u = sysUserMapper.selectById(d.getUserId());
            if (u != null) item.setUserName(u.getRealName());

            Long roleId = userRoleMap.get(d.getUserId());
            item.setUserRole(ROLE_MANAGER_ID.equals(roleId) ? "项目经理" : "项目成员");

            return item;
        }).collect(Collectors.toList());

        vo.setDistributions(distVos);
        return vo;
    }
}
