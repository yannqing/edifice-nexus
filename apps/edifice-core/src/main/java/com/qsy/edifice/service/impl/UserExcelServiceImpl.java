package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.excel.UserExcelData;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.UserExcelService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户花名册 Excel 导入服务
 *
 * 约定：
 * - Excel 表头与 {@link UserExcelData} 的 @ExcelProperty 对齐
 * - 用户名（username）不从 Excel 读取，按优先级自动生成：
 *     手机号 → 邮箱本地部分 → 员工编号；三者都空则跳过该行
 * - 默认初始密码 "12345678"（BCrypt），在职状态默认 1，账号状态默认 1
 * - 员工编号 / 用户名重复时跳过该行并在结果中回显
 */
@Slf4j
@Service
public class UserExcelServiceImpl implements UserExcelService {

    private static final String DEFAULT_PASSWORD = "12345678";

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
    };

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PasswordEncoder bCryptPasswordEncoder;

    // ==================== 下载模板 ====================

    @Override
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        List<UserExcelData> template = List.of(
                UserExcelData.builder()
                        .employeeNo("1")
                        .realName("张三")
                        .gender("男")
                        .ethnicity("汉")
                        .birthDate("1990.05.01")
                        .education("本科")
                        .school("示例大学")
                        .major("工程管理")
                        .position("造价员")
                        .professionalTitle("中级")
                        .certificates("一造（土建）")
                        .entryDate("2020.07.01")
                        .idCard("11010519900501001X")
                        .phone("13800138000")
                        .domicile("北京市")
                        .address("北京市朝阳区示例路1号")
                        .email("zhangsan@example.com")
                        .contractEndDate("2026.12.31")
                        .socialInsuranceDate("2020.07.01")
                        .employmentStatus("在职")
                        .resignDate("")
                        .remark("")
                        .build()
        );

        setExcelResponseHeader(response, "用户导入模板");
        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(0, "员工花名册")
                    .head(UserExcelData.class).build();
            writer.write(template, sheet);
        }
    }

    // ==================== 导入 ====================

    @Override
    public String importUsers(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return "上传文件为空";
        }

        List<UserExcelData> rows = EasyExcel.read(file.getInputStream())
                .head(UserExcelData.class)
                .sheet(0)
                .doReadSync();

        if (rows == null || rows.isEmpty()) {
            return "Excel 没有读到任何数据行";
        }

        // 预加载现有用户名和员工编号用于冲突检查
        List<SysUser> existingUsers = sysUserMapper.selectList(null);
        Set<String> existingUsernames = new HashSet<>();
        Set<String> existingEmployeeNos = new HashSet<>();
        for (SysUser u : existingUsers) {
            if (StringUtils.hasText(u.getUsername())) existingUsernames.add(u.getUsername());
            if (StringUtils.hasText(u.getEmployeeNo())) existingEmployeeNos.add(u.getEmployeeNo());
        }

        // 本次导入内部去重，避免 Excel 中自身重复导致二次异常
        Set<String> batchUsernames = new HashSet<>();
        Set<String> batchEmployeeNos = new HashSet<>();

        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            UserExcelData row = rows.get(i);
            int rowNum = i + 2; // 表头占第 1 行

            try {
                // 整行为空跳过
                if (isRowEmpty(row)) continue;

                if (!StringUtils.hasText(row.getRealName())) {
                    errors.add("第" + rowNum + "行：姓名不能为空");
                    failCount++;
                    continue;
                }

                String username = resolveUsername(row);
                if (username == null) {
                    errors.add("第" + rowNum + "行：无法生成用户名（手机号/邮箱/员工编号均为空）");
                    failCount++;
                    continue;
                }
                if (existingUsernames.contains(username) || batchUsernames.contains(username)) {
                    errors.add("第" + rowNum + "行：用户名 " + username + " 已存在");
                    failCount++;
                    continue;
                }

                String employeeNo = trimOrNull(row.getEmployeeNo());
                if (employeeNo != null) {
                    if (existingEmployeeNos.contains(employeeNo) || batchEmployeeNos.contains(employeeNo)) {
                        errors.add("第" + rowNum + "行：员工编号 " + employeeNo + " 已存在");
                        failCount++;
                        continue;
                    }
                }

                SysUser user = new SysUser();
                user.setUsername(username);
                user.setPassword(bCryptPasswordEncoder.encode(DEFAULT_PASSWORD));
                user.setEmployeeNo(employeeNo);
                user.setRealName(trimOrNull(row.getRealName()));
                user.setGender(parseGender(row.getGender()));
                user.setEthnicity(trimOrNull(row.getEthnicity()));
                user.setBirthDate(parseDate(row.getBirthDate()));
                user.setEducation(trimOrNull(row.getEducation()));
                user.setSchool(trimOrNull(row.getSchool()));
                user.setMajor(trimOrNull(row.getMajor()));
                user.setPosition(trimOrNull(row.getPosition()));
                user.setProfessionalTitle(trimOrNull(row.getProfessionalTitle()));
                user.setCertificates(trimOrNull(row.getCertificates()));
                user.setEntryDate(parseDate(row.getEntryDate()));
                user.setIdCard(trimOrNull(row.getIdCard()));
                user.setPhone(normalizePhone(row.getPhone()));
                user.setDomicile(trimOrNull(row.getDomicile()));
                user.setAddress(trimOrNull(row.getAddress()));
                user.setEmail(trimOrNull(row.getEmail()));
                user.setContractEndDate(parseDate(row.getContractEndDate()));
                user.setSocialInsuranceDate(parseDate(row.getSocialInsuranceDate()));
                user.setEmploymentStatus(parseEmploymentStatus(row.getEmploymentStatus()));
                user.setResignDate(parseDate(row.getResignDate()));
                user.setRemark(trimOrNull(row.getRemark()));
                user.setStatus(1);

                sysUserMapper.insert(user);
                successCount++;
                batchUsernames.add(username);
                if (employeeNo != null) batchEmployeeNos.add(employeeNo);

            } catch (Exception e) {
                log.error("导入第{}行失败: {}", rowNum, e.getMessage(), e);
                errors.add("第" + rowNum + "行：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                failCount++;
            }
        }

        StringBuilder result = new StringBuilder("导入完成：成功 ").append(successCount).append(" 条");
        if (failCount > 0) {
            result.append("，失败 ").append(failCount).append(" 条");
            if (!errors.isEmpty()) {
                result.append("。").append(String.join("；", errors.subList(0, Math.min(errors.size(), 5))));
                if (errors.size() > 5) result.append(" …");
            }
        }
        if (successCount > 0) {
            result.append("。新用户初始密码：").append(DEFAULT_PASSWORD);
        }
        return result.toString();
    }

    // ==================== 辅助方法 ====================

    private boolean isRowEmpty(UserExcelData r) {
        return !StringUtils.hasText(r.getRealName())
                && !StringUtils.hasText(r.getEmployeeNo())
                && !StringUtils.hasText(r.getPhone())
                && !StringUtils.hasText(r.getEmail());
    }

    /**
     * 用户名生成：手机号 → 邮箱本地部分 → 员工编号
     */
    private String resolveUsername(UserExcelData r) {
        String phone = normalizePhone(r.getPhone());
        if (phone != null) return phone;

        String email = trimOrNull(r.getEmail());
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).trim();
            if (StringUtils.hasText(local)) return local;
        }

        return trimOrNull(r.getEmployeeNo());
    }

    /**
     * 手机号兼容 Excel 误把数字当 Double（Excel "13800138000.0" 常见）
     */
    private String normalizePhone(String raw) {
        String s = trimOrNull(raw);
        if (s == null) return null;
        // 去掉末尾 ".0" 这类数值残留
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.isEmpty() ? null : s;
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 性别解析："男" → 0，"女" → 1，"其他" / 空 / 未知 → null
     */
    private Integer parseGender(String g) {
        String t = trimOrNull(g);
        if (t == null) return null;
        return switch (t) {
            case "男" -> 0;
            case "女" -> 1;
            case "其他" -> 2;
            default -> null;
        };
    }

    /**
     * 在职状态："在职" → 1，"离职" → 0，其它默认 1
     */
    private Integer parseEmploymentStatus(String s) {
        String t = trimOrNull(s);
        if (t == null) return 1;
        if ("离职".equals(t)) return 0;
        if ("在职".equals(t)) return 1;
        return 1;
    }

    /**
     * 多格式日期解析，解析失败返回 null 不抛异常
     */
    private LocalDate parseDate(String raw) {
        String s = trimOrNull(raw);
        if (s == null) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (Exception ignore) {
                // try next
            }
        }
        log.warn("无法解析日期: {}", s);
        return null;
    }

    private void setExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }
}
