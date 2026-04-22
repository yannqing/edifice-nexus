package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.dto.CreateProjectDto;
import com.qsy.edifice.domain.entity.*;
import com.qsy.edifice.domain.excel.ProjectExcelData;
import com.qsy.edifice.domain.excel.ProjectMemberExcelData;

import com.qsy.edifice.mapper.ProjectMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目 Excel 导入导出服务实现类
 *
 * Excel 结构：
 * - Sheet1「项目信息」：一个项目一行
 * - Sheet2「项目成员」：一个成员一行，通过项目编码关联
 * - Sheet3「用户参考表」：仅模板中提供，列出系统所有用户
 *
 * 导入时成员匹配优先级：手机号 > 邮箱 > 姓名
 */
@Slf4j
@Service
public class ProjectExcelServiceImpl implements ProjectExcelService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ContractService contractService;

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private ProjectService projectService;

    @Resource
    private SysUserMapper sysUserMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<Integer, String> STATUS_MAP = Map.of(
            0, "未开始", 1, "进行中", 2, "待验收", 3, "验收中", 4, "已完成"
    );

    private static final Map<Integer, String> CONTRACT_TYPE_MAP = Map.of(
            0, "基本收费", 1, "基本+效益"
    );

    private static final Map<String, Integer> CONTRACT_TYPE_REVERSE_MAP = Map.of(
            "基本收费", 0, "基本+效益", 1
    );

    private static final String ROLE_MANAGER = "项目经理";
    private static final String ROLE_MEMBER = "项目成员";
    private static final Long ROLE_MANAGER_ID = 101L;
    private static final Long ROLE_MEMBER_ID = 102L;

    // ==================== 导出 ====================

    @Override
    public void exportProjects(List<Long> projectIds, HttpServletResponse response) throws IOException {
        // 查询项目
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (projectIds != null && !projectIds.isEmpty()) {
            wrapper.in(Project::getProjectId, projectIds);
        }
        wrapper.orderByDesc(Project::getCreatedTime);
        List<Project> projects = projectMapper.selectList(wrapper);

        // Sheet1: 项目信息
        List<ProjectExcelData> projectDataList = projects.stream()
                .map(this::convertToExcelData)
                .collect(Collectors.toList());

        // Sheet2: 项目成员（所有项目的成员汇总）
        List<ProjectMemberExcelData> memberDataList = new ArrayList<>();
        for (Project project : projects) {
            List<ProjectMember> members = projectMemberService.getProjectMembersByProjectId(project.getProjectId());
            if (members == null) continue;
            for (ProjectMember pm : members) {
                SysUser user = sysUserMapper.selectById(pm.getUserId());
                memberDataList.add(ProjectMemberExcelData.builder()
                        .projectCode(project.getProjectCode())
                        .realName(user != null ? user.getRealName() : "")
                        .phone(user != null ? user.getPhone() : "")
                        .email(user != null ? user.getEmail() : "")
                        .role(ROLE_MANAGER_ID.equals(pm.getProjectRole()) ? ROLE_MANAGER : ROLE_MEMBER)
                        .build());
            }
        }

        setExcelResponseHeader(response, "项目数据");

        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build()) {
            WriteSheet sheet1 = EasyExcel.writerSheet(0, "项目信息")
                    .head(ProjectExcelData.class).build();
            writer.write(projectDataList, sheet1);

            WriteSheet sheet2 = EasyExcel.writerSheet(1, "项目成员")
                    .head(ProjectMemberExcelData.class).build();
            writer.write(memberDataList, sheet2);
        }
    }

    // ==================== 下载模板 ====================

    @Override
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        // Sheet1: 项目信息示例
        List<ProjectExcelData> templateData = List.of(
                ProjectExcelData.builder()
                        .projectName("示例项目名称")
                        .projectCode("PJ2026-001")
                        .projectTypeCode("A")
                        .projectStatus("")
                        .contractType("基本+效益")
                        .contractAmount(new java.math.BigDecimal("500000"))
                        .baseAmount(new java.math.BigDecimal("300000"))
                        .benefitRules("按审减额5%")
                        .preStartDate("2026-01-01")
                        .preEndDate("2026-12-31")
                        .build()
        );

        // Sheet2: 项目成员示例
        List<ProjectMemberExcelData> memberTemplate = new ArrayList<>();
        memberTemplate.add(ProjectMemberExcelData.builder()
                .projectCode("PJ2026-001")
                .realName("张三")
                .phone("13800138000")
                .email("")
                .role(ROLE_MANAGER)
                .build());
        memberTemplate.add(ProjectMemberExcelData.builder()
                .projectCode("PJ2026-001")
                .realName("李四")
                .phone("13900139000")
                .email("")
                .role(ROLE_MEMBER)
                .build());

        setExcelResponseHeader(response, "项目导入模板");

        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build()) {
            WriteSheet sheet1 = EasyExcel.writerSheet(0, "项目信息")
                    .head(ProjectExcelData.class).build();
            writer.write(templateData, sheet1);

            WriteSheet sheet2 = EasyExcel.writerSheet(1, "项目成员")
                    .head(ProjectMemberExcelData.class).build();
            writer.write(memberTemplate, sheet2);
        }
    }

    // ==================== 导入 ====================

    @Override
    public String importProjects(MultipartFile file, Long userId) throws IOException {
        InputStream inputStream = file.getInputStream();

        // 读取 Sheet1: 项目信息
        List<ProjectExcelData> projectDataList = EasyExcel.read(file.getInputStream())
                .head(ProjectExcelData.class)
                .sheet(0)
                .doReadSync();

        // 读取 Sheet2: 项目成员
        List<ProjectMemberExcelData> memberDataList = EasyExcel.read(file.getInputStream())
                .head(ProjectMemberExcelData.class)
                .sheet(1)
                .doReadSync();

        if (projectDataList == null || projectDataList.isEmpty()) {
            return "Excel 项目信息为空";
        }

        // 按项目编码分组成员
        Map<String, List<ProjectMemberExcelData>> membersByCode = new HashMap<>();
        if (memberDataList != null) {
            for (ProjectMemberExcelData m : memberDataList) {
                if (StringUtils.hasText(m.getProjectCode())) {
                    membersByCode.computeIfAbsent(m.getProjectCode().trim(), k -> new ArrayList<>()).add(m);
                }
            }
        }

        // 预加载映射
        List<ProjectType> allTypes = projectTypeService.getAllEnabledProjectTypes();
        Map<String, Long> typeCodeToIdMap = allTypes.stream()
                .collect(Collectors.toMap(ProjectType::getProjectTypeCode, ProjectType::getProjectTypeId, (a, b) -> a));

        List<SysUser> allUsers = sysUserMapper.selectList(null);
        Map<String, SysUser> phoneMap = allUsers.stream()
                .filter(u -> StringUtils.hasText(u.getPhone()))
                .collect(Collectors.toMap(SysUser::getPhone, u -> u, (a, b) -> a));
        Map<String, SysUser> emailMap = allUsers.stream()
                .filter(u -> StringUtils.hasText(u.getEmail()))
                .collect(Collectors.toMap(SysUser::getEmail, u -> u, (a, b) -> a));
        Map<String, SysUser> nameMap = allUsers.stream()
                .filter(u -> StringUtils.hasText(u.getRealName()))
                .collect(Collectors.toMap(SysUser::getRealName, u -> u, (a, b) -> a));

        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < projectDataList.size(); i++) {
            ProjectExcelData data = projectDataList.get(i);
            int rowNum = i + 2;

            try {
                // 校验
                if (!StringUtils.hasText(data.getProjectName())) {
                    errors.add("项目信息第" + rowNum + "行：项目名称不能为空");
                    failCount++;
                    continue;
                }
                if (!StringUtils.hasText(data.getProjectTypeCode())) {
                    errors.add("项目信息第" + rowNum + "行：项目类型编码不能为空");
                    failCount++;
                    continue;
                }

                Long typeId = typeCodeToIdMap.get(data.getProjectTypeCode().trim().toUpperCase());
                if (typeId == null) {
                    errors.add("项目信息第" + rowNum + "行：项目类型编码 '" + data.getProjectTypeCode() + "' 不存在");
                    failCount++;
                    continue;
                }

                Integer contractType = CONTRACT_TYPE_REVERSE_MAP.getOrDefault(
                        data.getContractType() != null ? data.getContractType().trim() : "", 0);

                // 解析该项目的成员
                String projectCode = StringUtils.hasText(data.getProjectCode()) ? data.getProjectCode().trim() : null;
                List<Long> chargeIds = new ArrayList<>();
                List<Long> memberIds = new ArrayList<>();

                if (projectCode != null && membersByCode.containsKey(projectCode)) {
                    for (ProjectMemberExcelData m : membersByCode.get(projectCode)) {
                        SysUser matched = resolveUser(m.getPhone(), m.getEmail(), m.getRealName(), phoneMap, emailMap, nameMap);
                        if (matched != null) {
                            if (ROLE_MANAGER.equals(m.getRole() != null ? m.getRole().trim() : "")) {
                                chargeIds.add(matched.getUserId());
                            } else {
                                memberIds.add(matched.getUserId());
                            }
                        } else {
                            log.warn("项目 {} 成员匹配失败: name={}, phone={}, email={}",
                                    projectCode, m.getRealName(), m.getPhone(), m.getEmail());
                        }
                    }
                }

                // 没有经理则用当前导入用户
                if (chargeIds.isEmpty()) {
                    chargeIds.add(userId);
                }

                CreateProjectDto dto = new CreateProjectDto();
                dto.setProjectName(data.getProjectName().trim());
                dto.setProjectCode(projectCode);
                dto.setProjectType(typeId);
                dto.setContractType(contractType);
                dto.setContractAmount(data.getContractAmount() != null ? data.getContractAmount() : java.math.BigDecimal.ZERO);
                dto.setBaseAmount(data.getBaseAmount());
                dto.setBenefitRule(data.getBenefitRules());
                dto.setProjectCharges(chargeIds);
                dto.setProjectMembers(memberIds);
                dto.setPreStartTime(parseDate(data.getPreStartDate()));
                dto.setPreEndTime(parseDate(data.getPreEndDate()));

                projectService.createProject(dto, userId);
                successCount++;

            } catch (Exception e) {
                log.error("导入第{}行失败: {}", rowNum, e.getMessage());
                errors.add("项目信息第" + rowNum + "行：" + e.getMessage());
                failCount++;
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("导入完成：成功 ").append(successCount).append(" 条");
        if (failCount > 0) {
            result.append("，失败 ").append(failCount).append(" 条");
            if (!errors.isEmpty()) {
                result.append("。").append(String.join("；", errors.subList(0, Math.min(errors.size(), 5))));
            }
        }
        return result.toString();
    }

    // ==================== 用户匹配 ====================

    /**
     * 匹配单个用户，优先级：手机号 > 邮箱 > 姓名
     */
    private SysUser resolveUser(
            String phone, String email, String name,
            Map<String, SysUser> phoneMap,
            Map<String, SysUser> emailMap,
            Map<String, SysUser> nameMap
    ) {
        if (StringUtils.hasText(phone)) {
            SysUser u = phoneMap.get(phone.trim());
            if (u != null) return u;
        }
        if (StringUtils.hasText(email)) {
            SysUser u = emailMap.get(email.trim());
            if (u != null) return u;
        }
        if (StringUtils.hasText(name)) {
            SysUser u = nameMap.get(name.trim());
            if (u != null) return u;
        }
        return null;
    }

    // ==================== 导出转换 ====================

    private ProjectExcelData convertToExcelData(Project project) {
        ProjectExcelData data = new ProjectExcelData();
        data.setProjectName(project.getProjectName());
        data.setProjectCode(project.getProjectCode());
        data.setProjectStatus(STATUS_MAP.getOrDefault(project.getProjectStatus(), "未开始"));

        if (project.getProjectType() != null) {
            ProjectType type = projectTypeService.getProjectTypeById(project.getProjectType());
            if (type != null) {
                data.setProjectTypeCode(type.getProjectTypeCode());
            }
        }

        Contract contract = contractService.getContractByProjectId(project.getProjectId());
        if (contract != null) {
            data.setContractType(CONTRACT_TYPE_MAP.getOrDefault(contract.getContractType(), "基本收费"));
            data.setContractAmount(contract.getContractAmount());
            data.setBaseAmount(contract.getBaseAmount());
            data.setBenefitRules(contract.getBenefitRules());
            if (contract.getPreStartDate() != null) {
                data.setPreStartDate(contract.getPreStartDate().format(DATE_FMT));
            }
            if (contract.getPreEndDate() != null) {
                data.setPreEndDate(contract.getPreEndDate().format(DATE_FMT));
            }
        }

        return data;
    }

    // ==================== 工具方法 ====================

    private void setExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private LocalDateTime parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr.trim() + "T00:00:00");
        } catch (Exception e) {
            return null;
        }
    }
}
