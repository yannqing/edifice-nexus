package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.vo.PersonnelQuarterSummaryVo;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.*;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "报表统计")
@RestController
@RequestMapping("/report")
public class ReportController {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectTypeService projectTypeService;
    @Resource
    private ProjectTypeMapper projectTypeMapper;
    @Resource
    private ContractService contractService;
    @Resource
    private ContractMapper contractMapper;
    @Resource
    private ProjectStageMapper projectStageMapper;
    @Resource
    private OutputValueMapper outputValueMapper;
    @Resource
    private OutputValueDistributionMapper distributionMapper;
    @Resource
    private TimesheetMapper timesheetMapper;
    @Resource
    private ProjectMemberService projectMemberService;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private InspectionFormMapper inspectionFormMapper;
    @Autowired
    private JwtUtils jwtUtils;

    /** 根据项目 ID 集合一次性拉取合同，避免 N+1 */
    private Map<Long, Contract> loadContractsByProjectIds(Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<Contract> w = new LambdaQueryWrapper<>();
        w.in(Contract::getProjectId, projectIds);
        return contractMapper.selectList(w).stream()
                .collect(Collectors.toMap(Contract::getProjectId, c -> c, (a, b) -> a));
    }

    /** 根据项目 ID 集合一次性拉取产值分配单 */
    private Map<Long, List<OutputValue>> loadOutputValuesByProjectIds(Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<OutputValue> w = new LambdaQueryWrapper<>();
        w.in(OutputValue::getProjectId, projectIds);
        return outputValueMapper.selectList(w).stream()
                .collect(Collectors.groupingBy(OutputValue::getProjectId));
    }

    private Map<Long, ProjectType> loadTypesByIds(Collection<Long> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) return Collections.emptyMap();
        return projectTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(ProjectType::getProjectTypeId, t -> t, (a, b) -> a));
    }

    // ==================== 统计报表 ====================

    @GetMapping("/overview")
    @Operation(summary = "统计总览", description = "项目总数、合同总额、已完成产值、回款率")
    @PreAuthorize("hasAuthority('menu:statistics') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, Object>> getOverview() {
        List<Project> projects = projectMapper.selectList(null);
        int totalProjects = projects.size();

        // 合同总额（批量）
        Set<Long> projectIds = projects.stream().map(Project::getProjectId).collect(Collectors.toSet());
        Map<Long, Contract> contractMap = loadContractsByProjectIds(projectIds);
        BigDecimal totalContract = contractMap.values().stream()
                .map(Contract::getContractAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 已确认产值（status >= 2，即已审批 + 已发放）
        LambdaQueryWrapper<OutputValue> confirmedWrapper = new LambdaQueryWrapper<>();
        confirmedWrapper.ge(OutputValue::getStatus, 2);
        List<OutputValue> confirmedList = outputValueMapper.selectList(confirmedWrapper);
        BigDecimal confirmedAmount = confirmedList.stream()
                .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 全部产值
        List<OutputValue> allOv = outputValueMapper.selectList(null);
        BigDecimal totalOutputValue = allOv.stream()
                .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProjects", totalProjects);
        result.put("totalContractAmount", totalContract);
        result.put("completedOutputValue", confirmedAmount);
        result.put("totalOutputValue", totalOutputValue);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/project-stats")
    @Operation(summary = "项目产值统计", description = "每个项目的合同额、已完成产值、待处理产值")
    @PreAuthorize("hasAuthority('menu:statistics') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<Map<String, Object>>> getProjectStats() {
        List<Project> projects = projectMapper.selectList(null);
        if (projects.isEmpty()) return ResultUtils.success(Code.SUCCESS, Collections.emptyList());

        // 批量预取：合同 / 类型 / 产值分配单
        Set<Long> projectIds = projects.stream().map(Project::getProjectId).collect(Collectors.toSet());
        Set<Long> typeIds = projects.stream().map(Project::getProjectType)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Contract> contractMap = loadContractsByProjectIds(projectIds);
        Map<Long, ProjectType> typeMap = loadTypesByIds(typeIds);
        Map<Long, List<OutputValue>> ovByProject = loadOutputValuesByProjectIds(projectIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", String.valueOf(p.getProjectId()));
            item.put("projectName", p.getProjectName());

            ProjectType type = p.getProjectType() == null ? null : typeMap.get(p.getProjectType());
            item.put("category", type != null ? type.getProjectTypeCode() + "类" : "");

            Contract c = contractMap.get(p.getProjectId());
            BigDecimal contractAmount = c != null && c.getContractAmount() != null
                    ? c.getContractAmount() : BigDecimal.ZERO;
            item.put("contractAmount", contractAmount);

            // 已确认产值 = status >= 2（已审批或已发放）；待处理 = status < 2
            List<OutputValue> ovList = ovByProject.getOrDefault(p.getProjectId(), Collections.emptyList());
            BigDecimal completedAmount = ovList.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() >= 2)
                    .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pendingAmount = ovList.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() < 2)
                    .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.put("completedAmount", completedAmount);
            item.put("outputValue", completedAmount);
            item.put("pendingValue", pendingAmount);

            result.add(item);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/category-stats")
    @Operation(summary = "项目分类统计")
    @PreAuthorize("hasAuthority('menu:statistics') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<Map<String, Object>>> getCategoryStats() {
        List<ProjectType> types = projectTypeService.getAllEnabledProjectTypes();
        List<Project> allProjects = projectMapper.selectList(null);
        int totalCount = allProjects.size();

        // 批量预取合同 + 已发放产值
        Set<Long> projectIds = allProjects.stream().map(Project::getProjectId).collect(Collectors.toSet());
        Map<Long, Contract> contractMap = loadContractsByProjectIds(projectIds);
        Map<Long, List<OutputValue>> ovByProject = loadOutputValuesByProjectIds(projectIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectType type : types) {
            List<Project> typeProjects = allProjects.stream()
                    .filter(p -> type.getProjectTypeId().equals(p.getProjectType()))
                    .collect(Collectors.toList());

            BigDecimal contractTotal = BigDecimal.ZERO;
            BigDecimal completedTotal = BigDecimal.ZERO;
            for (Project p : typeProjects) {
                Contract c = contractMap.get(p.getProjectId());
                if (c != null && c.getContractAmount() != null) {
                    contractTotal = contractTotal.add(c.getContractAmount());
                }

                BigDecimal projectCompleted = ovByProject.getOrDefault(p.getProjectId(), Collections.emptyList()).stream()
                        .filter(o -> o.getStatus() != null && o.getStatus() >= 2)
                        .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                completedTotal = completedTotal.add(projectCompleted);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", type.getProjectTypeCode() + "类");
            item.put("name", type.getProjectTypeName());
            item.put("count", typeProjects.size());
            item.put("contractTotal", contractTotal);
            item.put("completedTotal", completedTotal);
            item.put("percentage", totalCount > 0
                    ? BigDecimal.valueOf(typeProjects.size() * 100.0 / totalCount).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            result.add(item);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/personnel-ranking")
    @Operation(summary = "人员产值排名")
    @PreAuthorize("hasAuthority('menu:statistics') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<Map<String, Object>>> getPersonnelRanking() {
        // 按用户汇总已发放产值
        LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
        List<OutputValueDistribution> allDists = distributionMapper.selectList(distWrapper);

        // 获取已确认（已审批 + 已发放，status >= 2）的分配单ID集合
        LambdaQueryWrapper<OutputValue> confirmedWrapper = new LambdaQueryWrapper<>();
        confirmedWrapper.ge(OutputValue::getStatus, 2);
        Set<Long> confirmedIds = outputValueMapper.selectList(confirmedWrapper).stream()
                .map(OutputValue::getOutputValueId).collect(Collectors.toSet());

        // 按用户汇总
        Map<Long, BigDecimal> userAmountMap = new HashMap<>();
        for (OutputValueDistribution d : allDists) {
            if (confirmedIds.contains(d.getOutputValueId())) {
                BigDecimal amt = d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO;
                userAmountMap.merge(d.getUserId(), amt, BigDecimal::add);
            }
        }

        // 统计每个用户参与的项目数
        List<ProjectMember> allMembers = projectMemberService.getProjectMembersByProjectId(null);
        if (allMembers == null) allMembers = new ArrayList<>();
        // 用 mapper 查所有成员
        List<SysUser> allUsers = sysUserMapper.selectList(null);
        Map<Long, String> userNameMap = allUsers.stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));

        List<Map<String, Object>> result = userAmountMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", String.valueOf(entry.getKey()));
                    item.put("name", userNameMap.getOrDefault(entry.getKey(), "未知"));
                    item.put("outputValue", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());

        // 设置排名
        for (int i = 0; i < result.size(); i++) {
            result.get(i).put("rank", i + 1);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    // ==================== 个人绩效 ====================

    @GetMapping("/my-performance")
    @Operation(summary = "个人绩效总览")
    @PreAuthorize("hasAuthority('menu:performance') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, Object>> getMyPerformance(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long userId = loginUser.getUserId();

        // 参与项目数
        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);
        int projectCount = projectIds != null ? projectIds.size() : 0;

        // 总工时
        LambdaQueryWrapper<Timesheet> tsWrapper = new LambdaQueryWrapper<>();
        tsWrapper.eq(Timesheet::getUserId, userId);
        List<Timesheet> timesheets = timesheetMapper.selectList(tsWrapper);
        BigDecimal totalHours = timesheets.stream()
                .map(Timesheet::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 工作类型分布
        BigDecimal mgmtHours = BigDecimal.ZERO, basicHours = BigDecimal.ZERO, intellectHours = BigDecimal.ZERO;
        for (Timesheet t : timesheets) {
            switch (t.getWorkType()) {
                case 0 -> mgmtHours = mgmtHours.add(t.getHours());
                case 1 -> basicHours = basicHours.add(t.getHours());
                case 2 -> intellectHours = intellectHours.add(t.getHours());
            }
        }

        // 产值：从分配明细中查我的已发放产值
        LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
        distWrapper.eq(OutputValueDistribution::getUserId, userId);
        List<OutputValueDistribution> myDists = distributionMapper.selectList(distWrapper);

        // 已确认的分配单 ID（status >= 2：已审批 + 已发放）
        LambdaQueryWrapper<OutputValue> confirmedWrapper = new LambdaQueryWrapper<>();
        confirmedWrapper.ge(OutputValue::getStatus, 2);
        Set<Long> confirmedIds = outputValueMapper.selectList(confirmedWrapper).stream()
                .map(OutputValue::getOutputValueId).collect(Collectors.toSet());

        BigDecimal paidOutputValue = myDists.stream()
                .filter(d -> confirmedIds.contains(d.getOutputValueId()))
                .map(OutputValueDistribution::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutputValue = myDists.stream()
                .map(OutputValueDistribution::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectCount", projectCount);
        result.put("totalHours", totalHours);
        result.put("managementHours", mgmtHours);
        result.put("basicHours", basicHours);
        result.put("intellectualHours", intellectHours);
        result.put("paidOutputValue", paidOutputValue);
        result.put("totalOutputValue", totalOutputValue);

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-project-details")
    @Operation(summary = "个人参与项目明细")
    @PreAuthorize("hasAuthority('menu:performance') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<Map<String, Object>>> getMyProjectDetails(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long userId = loginUser.getUserId();

        List<Long> projectIds = projectMapper.selectProjectIdsByUserId(userId);
        if (projectIds == null || projectIds.isEmpty()) {
            return ResultUtils.success(Code.SUCCESS, new ArrayList<>());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long pid : projectIds) {
            Project p = projectMapper.selectById(pid);
            if (p == null) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", String.valueOf(pid));
            item.put("projectName", p.getProjectName());
            item.put("projectCode", p.getProjectCode());
            item.put("projectStatus", p.getProjectStatus());

            // 分类
            if (p.getProjectType() != null) {
                ProjectType type = projectTypeService.getProjectTypeById(p.getProjectType());
                item.put("category", type != null ? type.getProjectTypeCode() + "类" : "");
            }

            // 角色
            ProjectMember membership = projectMemberService.getProjectMemberByProjectIdAndUserId(pid, userId);
            item.put("role", membership != null && Long.valueOf(101L).equals(membership.getProjectRole()) ? "项目经理" : "项目成员");

            // 工时
            LambdaQueryWrapper<Timesheet> tsW = new LambdaQueryWrapper<>();
            tsW.eq(Timesheet::getUserId, userId).eq(Timesheet::getProjectId, pid);
            List<Timesheet> ts = timesheetMapper.selectList(tsW);
            BigDecimal hours = ts.stream().map(Timesheet::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            item.put("totalHours", hours);

            // 产值
            LambdaQueryWrapper<OutputValue> ovW = new LambdaQueryWrapper<>();
            ovW.eq(OutputValue::getProjectId, pid);
            List<OutputValue> ovList = outputValueMapper.selectList(ovW);
            Set<Long> ovIds = ovList.stream().map(OutputValue::getOutputValueId).collect(Collectors.toSet());

            LambdaQueryWrapper<OutputValueDistribution> dW = new LambdaQueryWrapper<>();
            dW.eq(OutputValueDistribution::getUserId, userId);
            if (!ovIds.isEmpty()) {
                dW.in(OutputValueDistribution::getOutputValueId, ovIds);
            } else {
                item.put("outputValue", BigDecimal.ZERO);
                result.add(item);
                continue;
            }
            List<OutputValueDistribution> dists = distributionMapper.selectList(dW);
            BigDecimal outputValue = dists.stream()
                    .map(OutputValueDistribution::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.put("outputValue", outputValue);

            result.add(item);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-payments")
    @Operation(summary = "个人产值发放记录")
    @PreAuthorize("hasAuthority('menu:performance') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<Map<String, Object>>> getMyPayments(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long userId = loginUser.getUserId();

        // 查我的所有分配明细
        LambdaQueryWrapper<OutputValueDistribution> dW = new LambdaQueryWrapper<>();
        dW.eq(OutputValueDistribution::getUserId, userId);
        List<OutputValueDistribution> myDists = distributionMapper.selectList(dW);

        List<Map<String, Object>> result = new ArrayList<>();
        for (OutputValueDistribution d : myDists) {
            OutputValue ov = outputValueMapper.selectById(d.getOutputValueId());
            if (ov == null) continue;

            Project p = projectMapper.selectById(ov.getProjectId());
            ProjectStage stage = projectStageService.getProjectStageById(ov.getProjectStageId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("distributionId", String.valueOf(d.getDistributionId()));
            item.put("projectName", p != null ? p.getProjectName() : "-");
            item.put("stageName", stage != null ? stage.getStageName() : "-");
            item.put("amount", d.getAmount());
            item.put("status", ov.getStatus()); // 0待确认/1待审核/2已审批/3已发放
            item.put("paidTime", ov.getPaidTime());
            result.add(item);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @Resource
    private ProjectStageService projectStageService;

    // ==================== 人员季度分配汇总表（v0.2） ====================

    @GetMapping("/personnel-quarter-summary")
    @Operation(summary = "人员季度分配汇总表",
            description = "按季度聚合所有已确认（status>=2）产值分配单，按用户维度汇总应得/实得金额")
    @PreAuthorize("hasAuthority('menu:personnel-quarter') or hasRole('SUPER_ADMIN')")
    public BaseResponse<List<PersonnelQuarterSummaryVo>> getPersonnelQuarterSummary(
            @RequestParam(value = "quarter", required = false) String quarter) {

        // 1. 筛选目标产值分配单：已确认（status>=2）；若带 quarter 则再加季度过滤
        LambdaQueryWrapper<OutputValue> ovW = new LambdaQueryWrapper<>();
        ovW.ge(OutputValue::getStatus, 2);
        if (quarter != null && !quarter.trim().isEmpty()) {
            ovW.eq(OutputValue::getQuarter, quarter.trim());
        }
        List<OutputValue> ovs = outputValueMapper.selectList(ovW);
        if (ovs.isEmpty()) return ResultUtils.success(Code.SUCCESS, Collections.emptyList());

        Set<Long> ovIds = ovs.stream().map(OutputValue::getOutputValueId).collect(Collectors.toSet());
        Map<Long, Long> ovToProject = ovs.stream()
                .collect(Collectors.toMap(OutputValue::getOutputValueId, OutputValue::getProjectId, (a, b) -> a));
        Map<Long, BigDecimal> ovToTotal = ovs.stream()
                .collect(Collectors.toMap(OutputValue::getOutputValueId, OutputValue::getTotalAmount, (a, b) -> a));

        // 2. 拉取这些分配单下所有明细
        LambdaQueryWrapper<OutputValueDistribution> dW = new LambdaQueryWrapper<>();
        dW.in(OutputValueDistribution::getOutputValueId, ovIds);
        List<OutputValueDistribution> dists = distributionMapper.selectList(dW);
        if (dists.isEmpty()) return ResultUtils.success(Code.SUCCESS, Collections.emptyList());

        // 3. 按 userId 聚合
        Map<Long, BigDecimal> userAlloc = new HashMap<>();        // 应得（planned）
        Map<Long, BigDecimal> userCompletion = new HashMap<>();   // 实得
        Map<Long, Set<Long>> userProjects = new HashMap<>();      // 参与项目

        // v0.4：员工池 = total × 40%（与 OutputValueServiceImpl.PERSONAL_POOL_RATE 保持一致）
        BigDecimal pool40 = new BigDecimal("0.40");
        BigDecimal bd100 = new BigDecimal("100");

        for (OutputValueDistribution d : dists) {
            Long uid = d.getUserId();
            BigDecimal total = ovToTotal.get(d.getOutputValueId());
            if (total == null) continue;

            // planned = total × 40% × allocRatio%
            BigDecimal alloc = d.getAllocRatio() != null ? d.getAllocRatio() : BigDecimal.ZERO;
            BigDecimal planned = total.multiply(pool40)
                    .multiply(alloc).divide(bd100, 2, RoundingMode.HALF_UP);

            BigDecimal actual = d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO;

            userAlloc.merge(uid, planned, BigDecimal::add);
            userCompletion.merge(uid, actual, BigDecimal::add);
            userProjects.computeIfAbsent(uid, k -> new HashSet<>()).add(ovToProject.get(d.getOutputValueId()));
        }

        // 4. 批量查姓名
        Set<Long> userIds = userAlloc.keySet();
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        // 5. 组装 VO，按应得金额倒序
        List<PersonnelQuarterSummaryVo> result = userIds.stream()
                .map(uid -> {
                    SysUser u = userMap.get(uid);
                    String name = u == null ? "-" :
                            (u.getRealName() != null ? u.getRealName() : u.getUsername());
                    return PersonnelQuarterSummaryVo.builder()
                            .userId(uid)
                            .realName(name)
                            .projectCount(userProjects.getOrDefault(uid, Collections.emptySet()).size())
                            .allocAmount(userAlloc.getOrDefault(uid, BigDecimal.ZERO))
                            .completionAmount(userCompletion.getOrDefault(uid, BigDecimal.ZERO))
                            // TODO: 等 LEADER 角色上线后按角色归集
                            .leaderShare(BigDecimal.ZERO)
                            .companyShare(BigDecimal.ZERO)
                            .build();
                })
                .sorted((a, b) -> b.getAllocAmount().compareTo(a.getAllocAmount()))
                .collect(Collectors.toList());

        return ResultUtils.success(Code.SUCCESS, result);
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    @Operation(summary = "仪表盘数据", description = "首页全局数据仪表盘")
    @PreAuthorize("hasAuthority('menu:workbench') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, Object>> getDashboard(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long userId = loginUser.getUserId();

        Map<String, Object> result = new LinkedHashMap<>();

        List<Long> myProjectIds = projectMapper.selectProjectIdsByUserId(userId);
        if (myProjectIds == null) {
            myProjectIds = Collections.emptyList();
        }
        Set<Long> myProjectIdSet = new LinkedHashSet<>(myProjectIds);
        Set<String> myProjectIdStringSet = myProjectIdSet.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        List<Project> scopedProjects = myProjectIdSet.isEmpty()
                ? Collections.emptyList()
                : projectMapper.selectBatchIds(myProjectIdSet);

        // 1. 统计卡片：仅统计当前用户参与的项目
        int projectCount = (int) scopedProjects.stream()
                .filter(p -> !Objects.equals(p.getProjectStatus(), 4))
                .count();

        // 产值总额
        List<OutputValue> scopedOv = Collections.emptyList();
        if (!myProjectIdSet.isEmpty()) {
            LambdaQueryWrapper<OutputValue> ovW = new LambdaQueryWrapper<>();
            ovW.in(OutputValue::getProjectId, myProjectIdSet);
            scopedOv = outputValueMapper.selectList(ovW);
        }
        BigDecimal totalOutputValue = scopedOv.stream()
                .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 已发放产值 = status = 3
        BigDecimal paidOutputValue = scopedOv.stream()
                .filter(o -> Objects.equals(o.getStatus(), 3))
                .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 待审批验工单数
        long pendingInspections = 0L;
        if (!myProjectIdStringSet.isEmpty()) {
            pendingInspections = inspectionFormMapper.selectCount(
                    new LambdaQueryWrapper<InspectionForm>()
                            .in(InspectionForm::getProjectId, myProjectIdStringSet)
                            .eq(InspectionForm::getInspectionFormStatus, 0));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("projectCount", projectCount);
        stats.put("totalOutputValue", totalOutputValue);
        stats.put("paidOutputValue", paidOutputValue);
        stats.put("pendingInspections", pendingInspections);
        result.put("stats", stats);

        // 2. 关键项目（当前用户参与项目中，合同金额最大的前5个）
        Map<Long, Contract> contractMap = loadContractsByProjectIds(myProjectIdSet);
        Map<Long, List<OutputValue>> ovByProject = loadOutputValuesByProjectIds(myProjectIdSet);
        Set<Long> allTypeIds = scopedProjects.stream().map(Project::getProjectType)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProjectType> typeMap = loadTypesByIds(allTypeIds);

        Map<Long, BigDecimal> contractAmounts = new HashMap<>();
        for (Project p : scopedProjects) {
            Contract c = contractMap.get(p.getProjectId());
            contractAmounts.put(p.getProjectId(),
                    c != null && c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO);
        }

        List<Project> sorted = new ArrayList<>(scopedProjects);
        sorted.sort((a, b) -> contractAmounts.getOrDefault(b.getProjectId(), BigDecimal.ZERO)
                .compareTo(contractAmounts.getOrDefault(a.getProjectId(), BigDecimal.ZERO)));

        List<Map<String, Object>> topProjects = new ArrayList<>();
        for (Project p : sorted.stream().limit(5).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", String.valueOf(p.getProjectId()));
            item.put("projectName", p.getProjectName());

            ProjectType type = p.getProjectType() == null ? null : typeMap.get(p.getProjectType());
            item.put("type", type != null ? type.getProjectTypeCode() + "类" : "");

            BigDecimal contractAmt = contractAmounts.getOrDefault(p.getProjectId(), BigDecimal.ZERO);
            item.put("contractAmount", contractAmt);
            item.put("projectStatus", p.getProjectStatus());

            // 已确认产值 = status >= 2
            BigDecimal completed = ovByProject.getOrDefault(p.getProjectId(), Collections.emptyList()).stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() >= 2)
                    .map(OutputValue::getTotalAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.put("completedValue", completed);
            int progress = contractAmt.signum() > 0
                    ? completed.multiply(BigDecimal.valueOf(100))
                        .divide(contractAmt, 0, RoundingMode.DOWN).intValue()
                    : 0;
            item.put("progress", progress);

            topProjects.add(item);
        }
        result.put("topProjects", topProjects);

        // 3. 待办事项（当前用户参与项目内的待审批验工单 + 待确认产值）
        List<Map<String, Object>> todos = new ArrayList<>();

        // 待审批验工单（批量查申请人姓名）
        List<InspectionForm> pendingIns = Collections.emptyList();
        if (!myProjectIdStringSet.isEmpty()) {
            LambdaQueryWrapper<InspectionForm> insW = new LambdaQueryWrapper<>();
            insW.in(InspectionForm::getProjectId, myProjectIdStringSet)
                    .eq(InspectionForm::getInspectionFormStatus, 0)
                    .orderByDesc(InspectionForm::getCreatedTime)
                    .last("LIMIT 5");
            pendingIns = inspectionFormMapper.selectList(insW);
        }
        Set<Long> applyUserIds = pendingIns.stream()
                .map(InspectionForm::getApplyUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> applyUserMap = applyUserIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(applyUserIds).stream()
                    .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));
        for (InspectionForm ins : pendingIns) {
            Map<String, Object> todo = new LinkedHashMap<>();
            todo.put("id", String.valueOf(ins.getInspectionFormId()));
            todo.put("type", "验工审批");
            todo.put("title", ins.getInspectionFormCode());
            if (ins.getApplyUserId() != null) {
                SysUser u = applyUserMap.get(ins.getApplyUserId());
                todo.put("from", u != null ? u.getRealName() : "-");
            }
            todo.put("time", ins.getCreatedTime());
            todos.add(todo);
        }

        // 待确认产值分配（批量查项目名）
        List<OutputValue> pendingOv = Collections.emptyList();
        if (!myProjectIdSet.isEmpty()) {
            LambdaQueryWrapper<OutputValue> ovPendingW = new LambdaQueryWrapper<>();
            ovPendingW.in(OutputValue::getProjectId, myProjectIdSet)
                    .eq(OutputValue::getStatus, 0)
                    .orderByDesc(OutputValue::getCreatedTime)
                    .last("LIMIT 3");
            pendingOv = outputValueMapper.selectList(ovPendingW);
        }
        Set<Long> pendingOvProjectIds = pendingOv.stream()
                .map(OutputValue::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Project> pendingOvProjectMap = pendingOvProjectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(pendingOvProjectIds).stream()
                    .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));
        for (OutputValue ov : pendingOv) {
            Map<String, Object> todo = new LinkedHashMap<>();
            todo.put("id", String.valueOf(ov.getOutputValueId()));
            todo.put("type", "产值确认");
            Project p = pendingOvProjectMap.get(ov.getProjectId());
            todo.put("title", p != null ? p.getProjectName() + " 产值分配" : "产值分配");
            todo.put("from", "系统");
            todo.put("time", ov.getCreatedTime());
            todos.add(todo);
        }
        result.put("todos", todos);

        // 4. 我的项目进度（当前用户参与的项目）
        List<Map<String, Object>> myProjects = new ArrayList<>();
        if (!myProjectIds.isEmpty()) {
            List<Long> limited = myProjectIds.stream().limit(4).toList();

            // 批量取项目
            Map<Long, Project> myProjectMap = projectMapper.selectBatchIds(limited).stream()
                    .collect(Collectors.toMap(Project::getProjectId, p -> p, (a, b) -> a));

            // 批量取阶段
            LambdaQueryWrapper<ProjectStage> sw = new LambdaQueryWrapper<>();
            sw.in(ProjectStage::getProjectId, limited);
            Map<Long, List<ProjectStage>> stagesGrouped = projectStageMapper.selectList(sw).stream()
                    .collect(Collectors.groupingBy(ProjectStage::getProjectId));

            for (Long pid : limited) {
                Project p = myProjectMap.get(pid);
                if (p == null) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("projectId", String.valueOf(pid));
                item.put("projectName", p.getProjectName());
                item.put("projectStatus", p.getProjectStatus());

                ProjectType type = p.getProjectType() == null ? null : typeMap.get(p.getProjectType());
                item.put("category", type != null ? type.getProjectTypeCode() + "类" : "");

                List<ProjectStage> stages = stagesGrouped.getOrDefault(pid, Collections.emptyList());
                item.put("phases", stages.size());
                int completed = (int) stages.stream().filter(s -> s.getStageStatus() == 3 || s.getStageStatus() == 6).count();
                item.put("currentPhase", completed);

                myProjects.add(item);
            }
        }
        result.put("myProjects", myProjects);

        // 5. 项目分类分布（当前用户参与项目）
        List<ProjectType> types = projectTypeService.getAllEnabledProjectTypes();
        List<Map<String, Object>> categoryDist = new ArrayList<>();
        for (ProjectType type : types) {
            long count = scopedProjects.stream().filter(p -> type.getProjectTypeId().equals(p.getProjectType())).count();
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("category", type.getProjectTypeCode() + "类");
            cat.put("name", type.getProjectTypeName());
            cat.put("count", count);
            categoryDist.add(cat);
        }
        result.put("categoryDistribution", categoryDist);

        return ResultUtils.success(Code.SUCCESS, result);
    }
}
