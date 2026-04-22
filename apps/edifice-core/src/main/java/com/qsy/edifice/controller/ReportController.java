package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.mapper.*;
import com.qsy.edifice.service.*;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
    private ContractService contractService;
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

    // ==================== 统计报表 ====================

    @GetMapping("/overview")
    @Operation(summary = "统计总览", description = "项目总数、合同总额、已完成产值、回款率")
    public BaseResponse<Map<String, Object>> getOverview() {
        List<Project> projects = projectMapper.selectList(null);
        int totalProjects = projects.size();

        // 合同总额
        long totalContract = 0;
        for (Project p : projects) {
            Contract c = contractService.getContractByProjectId(p.getProjectId());
            if (c != null && c.getContractAmount() != null) {
                totalContract += c.getContractAmount();
            }
        }

        // 已发放产值
        LambdaQueryWrapper<OutputValue> paidWrapper = new LambdaQueryWrapper<>();
        paidWrapper.eq(OutputValue::getStatus, 3);
        List<OutputValue> paidList = outputValueMapper.selectList(paidWrapper);
        long paidAmount = paidList.stream().mapToLong(OutputValue::getTotalAmount).sum();

        // 全部产值
        List<OutputValue> allOv = outputValueMapper.selectList(null);
        long totalOutputValue = allOv.stream().mapToLong(OutputValue::getTotalAmount).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProjects", totalProjects);
        result.put("totalContractAmount", totalContract);
        result.put("completedOutputValue", paidAmount);
        result.put("totalOutputValue", totalOutputValue);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/project-stats")
    @Operation(summary = "项目产值统计", description = "每个项目的合同额、已完成产值、待处理产值")
    public BaseResponse<List<Map<String, Object>>> getProjectStats() {
        List<Project> projects = projectMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Project p : projects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", String.valueOf(p.getProjectId()));
            item.put("projectName", p.getProjectName());

            // 项目类型
            if (p.getProjectType() != null) {
                ProjectType type = projectTypeService.getProjectTypeById(p.getProjectType());
                item.put("category", type != null ? type.getProjectTypeCode() + "类" : "");
            }

            // 合同金额
            Contract c = contractService.getContractByProjectId(p.getProjectId());
            int contractAmount = c != null && c.getContractAmount() != null ? c.getContractAmount() : 0;
            item.put("contractAmount", contractAmount);

            // 产值统计
            LambdaQueryWrapper<OutputValue> ovWrapper = new LambdaQueryWrapper<>();
            ovWrapper.eq(OutputValue::getProjectId, p.getProjectId());
            List<OutputValue> ovList = outputValueMapper.selectList(ovWrapper);

            long completedAmount = ovList.stream().filter(o -> o.getStatus() == 3).mapToLong(OutputValue::getTotalAmount).sum();
            long pendingAmount = ovList.stream().filter(o -> o.getStatus() < 3).mapToLong(OutputValue::getTotalAmount).sum();
            item.put("completedAmount", completedAmount);
            item.put("outputValue", completedAmount);
            item.put("pendingValue", pendingAmount);

            result.add(item);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/category-stats")
    @Operation(summary = "项目分类统计")
    public BaseResponse<List<Map<String, Object>>> getCategoryStats() {
        List<ProjectType> types = projectTypeService.getAllEnabledProjectTypes();
        List<Project> allProjects = projectMapper.selectList(null);
        int totalCount = allProjects.size();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectType type : types) {
            List<Project> typeProjects = allProjects.stream()
                    .filter(p -> type.getProjectTypeId().equals(p.getProjectType()))
                    .collect(Collectors.toList());

            long contractTotal = 0;
            long completedTotal = 0;
            for (Project p : typeProjects) {
                Contract c = contractService.getContractByProjectId(p.getProjectId());
                if (c != null && c.getContractAmount() != null) contractTotal += c.getContractAmount();

                LambdaQueryWrapper<OutputValue> w = new LambdaQueryWrapper<>();
                w.eq(OutputValue::getProjectId, p.getProjectId()).eq(OutputValue::getStatus, 3);
                List<OutputValue> paid = outputValueMapper.selectList(w);
                completedTotal += paid.stream().mapToLong(OutputValue::getTotalAmount).sum();
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
    public BaseResponse<List<Map<String, Object>>> getPersonnelRanking() {
        // 按用户汇总已发放产值
        LambdaQueryWrapper<OutputValueDistribution> distWrapper = new LambdaQueryWrapper<>();
        List<OutputValueDistribution> allDists = distributionMapper.selectList(distWrapper);

        // 获取已发放的分配单ID集合
        LambdaQueryWrapper<OutputValue> paidWrapper = new LambdaQueryWrapper<>();
        paidWrapper.eq(OutputValue::getStatus, 3);
        Set<Long> paidIds = outputValueMapper.selectList(paidWrapper).stream()
                .map(OutputValue::getOutputValueId).collect(Collectors.toSet());

        // 按用户汇总
        Map<Long, Integer> userAmountMap = new HashMap<>();
        Map<Long, Integer> userProjectCountMap = new HashMap<>();
        for (OutputValueDistribution d : allDists) {
            if (paidIds.contains(d.getOutputValueId())) {
                userAmountMap.merge(d.getUserId(), d.getAmount(), Integer::sum);
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

        LambdaQueryWrapper<OutputValue> paidWrapper = new LambdaQueryWrapper<>();
        paidWrapper.eq(OutputValue::getStatus, 3);
        Set<Long> paidIds = outputValueMapper.selectList(paidWrapper).stream()
                .map(OutputValue::getOutputValueId).collect(Collectors.toSet());

        long paidOutputValue = myDists.stream()
                .filter(d -> paidIds.contains(d.getOutputValueId()))
                .mapToLong(OutputValueDistribution::getAmount).sum();

        long totalOutputValue = myDists.stream()
                .mapToLong(OutputValueDistribution::getAmount).sum();

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
                item.put("outputValue", 0);
                result.add(item);
                continue;
            }
            List<OutputValueDistribution> dists = distributionMapper.selectList(dW);
            long outputValue = dists.stream().mapToLong(OutputValueDistribution::getAmount).sum();
            item.put("outputValue", outputValue);

            result.add(item);
        }

        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/my-payments")
    @Operation(summary = "个人产值发放记录")
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

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    @Operation(summary = "仪表盘数据", description = "首页全局数据仪表盘")
    public BaseResponse<Map<String, Object>> getDashboard(HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long userId = loginUser.getUserId();

        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 统计卡片
        List<Project> allProjects = projectMapper.selectList(null);
        int projectCount = allProjects.size();

        // 产值总额
        List<OutputValue> allOv = outputValueMapper.selectList(null);
        long totalOutputValue = allOv.stream().mapToLong(OutputValue::getTotalAmount).sum();
        long paidOutputValue = allOv.stream().filter(o -> o.getStatus() == 3).mapToLong(OutputValue::getTotalAmount).sum();

        // 待审批验工单数
        long pendingInspections = inspectionFormMapper.selectCount(
                new LambdaQueryWrapper<InspectionForm>().eq(InspectionForm::getInspectionFormStatus, 0));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("projectCount", projectCount);
        stats.put("totalOutputValue", totalOutputValue);
        stats.put("paidOutputValue", paidOutputValue);
        stats.put("pendingInspections", pendingInspections);
        result.put("stats", stats);

        // 2. 关键项目（合同金额最大的前5个）
        List<Map<String, Object>> topProjects = new ArrayList<>();
        List<Project> sorted = new ArrayList<>(allProjects);
        // 按合同金额排序
        Map<Long, Integer> contractAmounts = new HashMap<>();
        for (Project p : sorted) {
            Contract c = contractService.getContractByProjectId(p.getProjectId());
            contractAmounts.put(p.getProjectId(), c != null && c.getContractAmount() != null ? c.getContractAmount() : 0);
        }
        sorted.sort((a, b) -> contractAmounts.getOrDefault(b.getProjectId(), 0) - contractAmounts.getOrDefault(a.getProjectId(), 0));

        for (Project p : sorted.stream().limit(5).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", String.valueOf(p.getProjectId()));
            item.put("projectName", p.getProjectName());

            if (p.getProjectType() != null) {
                ProjectType type = projectTypeService.getProjectTypeById(p.getProjectType());
                item.put("type", type != null ? type.getProjectTypeCode() + "类" : "");
            }

            int contractAmt = contractAmounts.getOrDefault(p.getProjectId(), 0);
            item.put("contractAmount", contractAmt);
            item.put("projectStatus", p.getProjectStatus());

            // 产值完成度
            LambdaQueryWrapper<OutputValue> ovW = new LambdaQueryWrapper<>();
            ovW.eq(OutputValue::getProjectId, p.getProjectId()).eq(OutputValue::getStatus, 3);
            long completed = outputValueMapper.selectList(ovW).stream().mapToLong(OutputValue::getTotalAmount).sum();
            item.put("completedValue", completed);
            item.put("progress", contractAmt > 0 ? (int) (completed * 100 / contractAmt) : 0);

            topProjects.add(item);
        }
        result.put("topProjects", topProjects);

        // 3. 待办事项（当前用户的待审批验工单 + 待确认产值）
        List<Map<String, Object>> todos = new ArrayList<>();

        // 待审批验工单
        LambdaQueryWrapper<InspectionForm> insW = new LambdaQueryWrapper<>();
        insW.eq(InspectionForm::getInspectionFormStatus, 0).orderByDesc(InspectionForm::getCreatedTime).last("LIMIT 5");
        List<InspectionForm> pendingIns = inspectionFormMapper.selectList(insW);
        for (InspectionForm ins : pendingIns) {
            Map<String, Object> todo = new LinkedHashMap<>();
            todo.put("id", String.valueOf(ins.getInspectionFormId()));
            todo.put("type", "验工审批");
            todo.put("title", ins.getInspectionFormCode());

            if (ins.getApplyUserId() != null) {
                SysUser u = sysUserMapper.selectById(ins.getApplyUserId());
                todo.put("from", u != null ? u.getRealName() : "-");
            }
            todo.put("time", ins.getCreatedTime());
            todos.add(todo);
        }

        // 待确认产值分配
        LambdaQueryWrapper<OutputValue> ovPendingW = new LambdaQueryWrapper<>();
        ovPendingW.eq(OutputValue::getStatus, 0).orderByDesc(OutputValue::getCreatedTime).last("LIMIT 3");
        List<OutputValue> pendingOv = outputValueMapper.selectList(ovPendingW);
        for (OutputValue ov : pendingOv) {
            Map<String, Object> todo = new LinkedHashMap<>();
            todo.put("id", String.valueOf(ov.getOutputValueId()));
            todo.put("type", "产值确认");

            Project p = projectMapper.selectById(ov.getProjectId());
            todo.put("title", p != null ? p.getProjectName() + " 产值分配" : "产值分配");
            todo.put("from", "系统");
            todo.put("time", ov.getCreatedTime());
            todos.add(todo);
        }
        result.put("todos", todos);

        // 4. 我的项目进度（当前用户参与的项目）
        List<Long> myProjectIds = projectMapper.selectProjectIdsByUserId(userId);
        List<Map<String, Object>> myProjects = new ArrayList<>();
        if (myProjectIds != null) {
            for (Long pid : myProjectIds.stream().limit(4).toList()) {
                Project p = projectMapper.selectById(pid);
                if (p == null) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("projectId", String.valueOf(pid));
                item.put("projectName", p.getProjectName());
                item.put("projectStatus", p.getProjectStatus());

                if (p.getProjectType() != null) {
                    ProjectType type = projectTypeService.getProjectTypeById(p.getProjectType());
                    item.put("category", type != null ? type.getProjectTypeCode() + "类" : "");
                }

                // 阶段进度
                List<ProjectStage> stages = projectStageService.getProjectStagesByProjectId(pid);
                item.put("phases", stages != null ? stages.size() : 0);
                int completed = stages != null ? (int) stages.stream().filter(s -> s.getStageStatus() == 3 || s.getStageStatus() == 6).count() : 0;
                item.put("currentPhase", completed);

                myProjects.add(item);
            }
        }
        result.put("myProjects", myProjects);

        // 5. 项目分类分布
        List<ProjectType> types = projectTypeService.getAllEnabledProjectTypes();
        List<Map<String, Object>> categoryDist = new ArrayList<>();
        for (ProjectType type : types) {
            long count = allProjects.stream().filter(p -> type.getProjectTypeId().equals(p.getProjectType())).count();
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
