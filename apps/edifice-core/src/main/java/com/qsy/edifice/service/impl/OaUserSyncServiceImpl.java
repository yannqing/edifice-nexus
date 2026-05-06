package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qsy.edifice.config.OaUserSyncProperties;
import com.qsy.edifice.domain.entity.SysDepartment;
import com.qsy.edifice.domain.entity.SysPosition;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.entity.SysUserDepartment;
import com.qsy.edifice.mapper.SysDepartmentMapper;
import com.qsy.edifice.mapper.SysPositionMapper;
import com.qsy.edifice.mapper.SysUserDepartmentMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.OaUserSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class OaUserSyncServiceImpl implements OaUserSyncService {

    private static final String SYNC_SOURCE = "OA";

    @Resource
    private OaUserSyncProperties properties;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private SysDepartmentMapper sysDepartmentMapper;

    @Resource
    private SysPositionMapper sysPositionMapper;

    @Resource
    private SysUserDepartmentMapper sysUserDepartmentMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public void enqueueUpsert(SysUser user) {
        // 新 OA 已作为主数据源，edifice 本地用户变更不再反向推送，避免双写冲突。
    }

    @Override
    public void enqueueDelete(Long userId) {
        // 新 OA 已作为主数据源，edifice 本地用户删除不再反向推送。
    }

    @Override
    public int enqueueFullSync() {
        return syncFromOa();
    }

    @Override
    public int processPending() {
        return 0;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("mode", "OA_TO_EDIFICE_DB_PULL");
        status.put("officeDatabase", properties.getOfficeDatabase());
        status.put("baseUrl", properties.getBaseUrl());
        status.put("pending", 0);
        return status;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncFromOa() {
        if (!properties.isEnabled()) {
            return 0;
        }
        String db = quoteDatabase(properties.getOfficeDatabase());
        LocalDateTime now = LocalDateTime.now();

        int departments = syncDepartments(db, now);
        int positions = syncPositions(db, now);
        int users = syncUsers(db, now);

        log.info("OA 主数据同步完成 departments={}, positions={}, users={}", departments, positions, users);
        return departments + positions + users;
    }

    @Scheduled(
            fixedDelayString = "${oa.sync.full-sync-delay-ms:1800000}",
            initialDelayString = "${oa.sync.full-sync-initial-delay-ms:60000}"
    )
    public void scheduledFullSync() {
        try {
            syncFromOa();
        } catch (Exception e) {
            log.warn("OA 主数据同步任务失败: {}", e.getMessage());
        }
    }

    private int syncDepartments(String db, LocalDateTime now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, title, pid, leader_ids, sort, remark, status
                FROM %s.oa_department
                WHERE status >= 0
                ORDER BY pid ASC, id ASC
                """.formatted(db));

        int count = 0;
        for (Map<String, Object> row : rows) {
            Integer oaId = intValue(row.get("id"));
            if (oaId == null) continue;

            SysDepartment department = findDepartmentByOaId(oaId);
            if (department == null) {
                department = new SysDepartment();
                department.setOaDepartmentId(oaId);
                department.setIsDelete(0);
            }
            department.setName(StringUtils.defaultIfBlank(str(row.get("title")), "未命名部门"));
            department.setOaParentId(intValue(row.get("pid")));
            department.setParentId(resolveDepartmentId(department.getOaParentId()));
            department.setSort(defaultInt(row.get("sort"), 0));
            department.setRemark(str(row.get("remark")));
            department.setStatus(toEnabledStatus(intValue(row.get("status"))));
            department.setSyncedAt(now);

            if (department.getDepartmentId() == null) {
                sysDepartmentMapper.insert(department);
            } else {
                sysDepartmentMapper.updateById(department);
            }
            count++;
        }

        // 第二遍修正父级，避免父部门在第一遍尚未插入时 parent_id 只能落到 0。
        for (Map<String, Object> row : rows) {
            Integer oaId = intValue(row.get("id"));
            Integer oaParentId = intValue(row.get("pid"));
            SysDepartment department = findDepartmentByOaId(oaId);
            if (department == null) continue;
            Long parentId = resolveDepartmentId(oaParentId);
            if (!Objects.equals(department.getParentId(), parentId)) {
                department.setParentId(parentId);
                sysDepartmentMapper.updateById(department);
            }
        }
        return count;
    }

    private int syncPositions(String db, LocalDateTime now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, title, remark, status
                FROM %s.oa_position
                WHERE status >= 0
                ORDER BY id ASC
                """.formatted(db));

        int count = 0;
        for (Map<String, Object> row : rows) {
            Integer oaId = intValue(row.get("id"));
            if (oaId == null) continue;

            SysPosition position = findPositionByOaId(oaId);
            if (position == null) {
                position = new SysPosition();
                position.setOaPositionId(oaId);
                position.setIsDelete(0);
            }
            position.setName(StringUtils.defaultIfBlank(str(row.get("title")), "未命名岗位"));
            position.setRemark(str(row.get("remark")));
            position.setStatus(toEnabledStatus(intValue(row.get("status"))));
            position.setSyncedAt(now);

            if (position.getPositionId() == null) {
                sysPositionMapper.insert(position);
            } else {
                sysPositionMapper.updateById(position);
            }
            count++;
        }
        return count;
    }

    private int syncUsers(String db, LocalDateTime now) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, userid, username, name, nickname, email, mobile, sex, did,
                       position_id, position_name, job_number, birthday, nation,
                       education, speciality, idcard, entry_time, resident_place,
                       current_address, home_address, status, delete_time
                FROM %s.oa_admin
                ORDER BY id ASC
                """.formatted(db));

        int count = 0;
        for (Map<String, Object> row : rows) {
            Integer oaAdminId = intValue(row.get("id"));
            if (oaAdminId == null) continue;

            SysUser user = findUserForOaRow(row);
            boolean isNew = user == null;
            if (isNew) {
                user = new SysUser();
                user.setUsername(resolveUniqueUsername(row));
                user.setPassword(passwordEncoder.encode(properties.getDefaultPassword()));
                user.setIsDelete(0);
            }

            Integer oaDepartmentId = intValue(row.get("did"));
            Integer oaPositionId = intValue(row.get("position_id"));
            Long departmentId = resolveDepartmentId(oaDepartmentId);
            SysPosition localPosition = findPositionByOaId(oaPositionId);
            Long positionId = localPosition == null ? null : localPosition.getPositionId();
            String positionName = localPosition == null ? str(row.get("position_name")) : localPosition.getName();
            Integer oaStatus = intValue(row.get("status"));
            boolean deleted = longValue(row.get("delete_time")) != null && longValue(row.get("delete_time")) > 0;

            user.setOaAdminId(oaAdminId);
            user.setOaUserid(str(row.get("userid")));
            user.setEmployeeNo(str(row.get("job_number")));
            user.setRealName(StringUtils.defaultIfBlank(str(row.get("name")), str(row.get("nickname"))));
            user.setGender(mapGender(intValue(row.get("sex"))));
            user.setEthnicity(str(row.get("nation")));
            user.setBirthDate(toLocalDate(row.get("birthday")));
            user.setIdCard(str(row.get("idcard")));
            user.setEmail(str(row.get("email")));
            user.setPhone(str(row.get("mobile")));
            user.setEducation(str(row.get("education")));
            user.setMajor(str(row.get("speciality")));
            user.setPosition(StringUtils.defaultIfBlank(positionName, user.getPosition()));
            user.setDepartmentId(departmentId);
            user.setOaDepartmentId(oaDepartmentId);
            user.setPositionId(positionId);
            user.setOaPositionId(oaPositionId);
            user.setEntryDate(toLocalDate(row.get("entry_time")));
            user.setDomicile(str(row.get("resident_place")));
            user.setAddress(StringUtils.defaultIfBlank(str(row.get("current_address")), str(row.get("home_address"))));
            user.setStatus(deleted ? 0 : toUserLoginStatus(oaStatus));
            user.setEmploymentStatus(deleted ? 0 : toEmploymentStatus(oaStatus));
            user.setSyncSource(SYNC_SOURCE);
            user.setSyncedAt(now);

            if (user.getStatus() == null) user.setStatus(1);
            if (user.getEmploymentStatus() == null) user.setEmploymentStatus(1);

            if (isNew) {
                sysUserMapper.insert(user);
            } else {
                sysUserMapper.updateById(user);
            }
            syncUserDepartments(db, user.getUserId(), oaAdminId, oaDepartmentId, departmentId);
            count++;
        }
        return count;
    }

    private void syncUserDepartments(String db, Long userId, Integer oaAdminId, Integer primaryOaDepartmentId, Long primaryDepartmentId) {
        if (userId == null) return;

        sysUserDepartmentMapper.delete(new LambdaUpdateWrapper<SysUserDepartment>()
                .eq(SysUserDepartment::getUserId, userId));

        Set<Integer> oaDepartmentIds = new LinkedHashSet<>();
        if (primaryOaDepartmentId != null && primaryOaDepartmentId > 0) {
            oaDepartmentIds.add(primaryOaDepartmentId);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT department_id
                FROM %s.oa_department_admin
                WHERE admin_id = ?
                """.formatted(db), oaAdminId);
        for (Map<String, Object> row : rows) {
            Integer oaDepartmentId = intValue(row.get("department_id"));
            if (oaDepartmentId != null && oaDepartmentId > 0) {
                oaDepartmentIds.add(oaDepartmentId);
            }
        }

        for (Integer oaDepartmentId : oaDepartmentIds) {
            Long departmentId = Objects.equals(oaDepartmentId, primaryOaDepartmentId)
                    ? primaryDepartmentId
                    : resolveDepartmentId(oaDepartmentId);
            if (departmentId == null || departmentId == 0L) continue;
            sysUserDepartmentMapper.insert(SysUserDepartment.builder()
                    .userId(userId)
                    .departmentId(departmentId)
                    .oaDepartmentId(oaDepartmentId)
                    .isPrimary(Objects.equals(oaDepartmentId, primaryOaDepartmentId) ? 1 : 0)
                    .isDelete(0)
                    .build());
        }
    }

    private SysUser findUserForOaRow(Map<String, Object> row) {
        Integer oaAdminId = intValue(row.get("id"));
        SysUser user = selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOaAdminId, oaAdminId));
        if (user != null) return user;

        String mobile = str(row.get("mobile"));
        if (StringUtils.isNotBlank(mobile)) {
            user = selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, mobile));
            if (user != null) return user;
        }

        String email = str(row.get("email"));
        if (StringUtils.isNotBlank(email)) {
            user = selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
            if (user != null) return user;
        }

        String username = str(row.get("username"));
        if (StringUtils.isNotBlank(username)) {
            user = selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (user != null) return user;
        }

        String jobNumber = str(row.get("job_number"));
        if (StringUtils.isNotBlank(jobNumber)) {
            user = selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmployeeNo, jobNumber));
        }
        return user;
    }

    private String resolveUniqueUsername(Map<String, Object> row) {
        Integer oaAdminId = intValue(row.get("id"));
        List<String> candidates = java.util.stream.Stream.of(
                        str(row.get("username")),
                        str(row.get("mobile")),
                        str(row.get("email")),
                        str(row.get("job_number")),
                        "oa_" + oaAdminId
                )
                .filter(StringUtils::isNotBlank)
                .map(this::normalizeUsername)
                .filter(StringUtils::isNotBlank)
                .toList();

        for (String candidate : candidates) {
            if (!sysUserMapper.exists(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, candidate))) {
                return candidate;
            }
        }
        return "oa_" + oaAdminId;
    }

    private String normalizeUsername(String value) {
        String username = StringUtils.trimToEmpty(value);
        int at = username.indexOf('@');
        if (at > 0) {
            username = username.substring(0, at);
        }
        return username.length() > 64 ? username.substring(0, 64) : username;
    }

    private SysDepartment findDepartmentByOaId(Integer oaDepartmentId) {
        if (oaDepartmentId == null || oaDepartmentId <= 0) return null;
        return sysDepartmentMapper.selectOne(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getOaDepartmentId, oaDepartmentId)
                .last("limit 1"));
    }

    private SysPosition findPositionByOaId(Integer oaPositionId) {
        if (oaPositionId == null || oaPositionId <= 0) return null;
        return sysPositionMapper.selectOne(new LambdaQueryWrapper<SysPosition>()
                .eq(SysPosition::getOaPositionId, oaPositionId)
                .last("limit 1"));
    }

    private Long resolveDepartmentId(Integer oaDepartmentId) {
        SysDepartment department = findDepartmentByOaId(oaDepartmentId);
        return department == null ? 0L : department.getDepartmentId();
    }

    private Long resolvePositionId(Integer oaPositionId) {
        SysPosition position = findPositionByOaId(oaPositionId);
        return position == null ? null : position.getPositionId();
    }

    private SysUser selectOne(LambdaQueryWrapper<SysUser> wrapper) {
        return sysUserMapper.selectOne(wrapper.last("limit 1"));
    }

    private int toEnabledStatus(Integer status) {
        return status != null && status == 1 ? 1 : 0;
    }

    private Integer toUserLoginStatus(Integer oaStatus) {
        if (oaStatus == null) return 1;
        return oaStatus == 1 ? 1 : 0;
    }

    private Integer toEmploymentStatus(Integer oaStatus) {
        if (oaStatus == null) return 1;
        return oaStatus == 2 ? 0 : 1;
    }

    private Integer mapGender(Integer oaSex) {
        if (oaSex == null || oaSex == 0) return null;
        if (oaSex == 1) return 0;
        if (oaSex == 2) return 1;
        return 2;
    }

    private String quoteDatabase(String database) {
        if (!StringUtils.defaultString(database).matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法 OA 数据库名: " + database);
        }
        return "`" + database + "`";
    }

    private String str(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intValue(Object value) {
        Long longValue = longValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private int defaultInt(Object value, int fallback) {
        Integer parsed = intValue(value);
        return parsed == null ? fallback : parsed;
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.util.Date d) {
            if (d instanceof Date sqlDate) return sqlDate.toLocalDate();
            return Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        Long epoch = longValue(value);
        if (epoch != null && epoch > 0) {
            long millis = epoch > 10_000_000_000L ? epoch : epoch * 1000;
            return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = str(value);
        if (s == null || "0000-00-00".equals(s)) return null;
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception ignored) {
            return null;
        }
    }
}
