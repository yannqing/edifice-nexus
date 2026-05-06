package com.qsy.edifice.security;

import com.qsy.edifice.config.OaUserSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

@Slf4j
public class OaAwarePasswordEncoder implements PasswordEncoder {

    private static final String OA_PREFIX = "{edifice-oa}";

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final JdbcTemplate jdbcTemplate;
    private final OaUserSyncProperties properties;

    public OaAwarePasswordEncoder(JdbcTemplate jdbcTemplate, OaUserSyncProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || StringUtils.isBlank(encodedPassword)) {
            return false;
        }
        if (!encodedPassword.startsWith(OA_PREFIX)) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }

        ParsedPassword parsed = parse(encodedPassword);
        if (parsed == null) {
            return false;
        }
        if (StringUtils.isNotBlank(parsed.localPasswordHash()) && bcrypt.matches(rawPassword, parsed.localPasswordHash())) {
            return true;
        }
        return matchesOaPassword(rawPassword.toString(), parsed.oaAdminId());
    }

    private ParsedPassword parse(String encodedPassword) {
        String payload = encodedPassword.substring(OA_PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        try {
            Integer oaAdminId = Integer.parseInt(payload.substring(0, separator));
            String localPasswordHash = payload.substring(separator + 1);
            return new ParsedPassword(oaAdminId, localPasswordHash);
        } catch (NumberFormatException e) {
            log.warn("解析 OA 密码代理标识失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean matchesOaPassword(String rawPassword, Integer oaAdminId) {
        if (oaAdminId == null || oaAdminId <= 0 || !properties.isEnabled()) {
            return false;
        }
        String db = quoteDatabase(properties.getOfficeDatabase());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT pwd, salt, status, delete_time
                FROM %s.oa_admin
                WHERE id = ?
                LIMIT 1
                """.formatted(db), oaAdminId);
        if (rows.isEmpty()) {
            return false;
        }

        Map<String, Object> row = rows.get(0);
        Integer status = intValue(row.get("status"));
        Long deleteTime = longValue(row.get("delete_time"));
        if (status == null || status != 1 || (deleteTime != null && deleteTime > 0)) {
            return false;
        }

        String salt = StringUtils.trimToEmpty((String) row.get("salt"));
        String pwd = StringUtils.trimToEmpty((String) row.get("pwd"));
        if (StringUtils.isAnyBlank(salt, pwd)) {
            return false;
        }
        String hashed = DigestUtils.md5Hex(DigestUtils.md5Hex(rawPassword + salt) + salt);
        return pwd.equalsIgnoreCase(hashed);
    }

    private String quoteDatabase(String database) {
        if (!StringUtils.defaultString(database).matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法 OA 数据库名: " + database);
        }
        return "`" + database + "`";
    }

    private Integer intValue(Object value) {
        Long parsed = longValue(value);
        return parsed == null ? null : parsed.intValue();
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

    private record ParsedPassword(Integer oaAdminId, String localPasswordHash) {
    }
}
