package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.config.OaUserSyncProperties;
import com.qsy.edifice.domain.dto.OaUserSyncPayload;
import com.qsy.edifice.domain.entity.OaUserSyncOutbox;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.mapper.OaUserSyncOutboxMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.OaUserSyncService;
import com.qsy.edifice.utils.HttpClientUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OaUserSyncServiceImpl implements OaUserSyncService {

    private static final String EVENT_UPSERT = "upsert";
    private static final String EVENT_DELETE = "delete";

    @Resource
    private OaUserSyncProperties properties;

    @Resource
    private OaUserSyncOutboxMapper outboxMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private HttpClientUtil httpClientUtil;

    @Override
    public void enqueueUpsert(SysUser user) {
        if (!properties.isEnabled() || user == null || user.getUserId() == null || StringUtils.isBlank(user.getUsername())) {
            return;
        }
        enqueue(buildUpsertPayload(user));
    }

    @Override
    public void enqueueDelete(Long userId) {
        if (!properties.isEnabled() || userId == null) {
            return;
        }
        enqueue(OaUserSyncPayload.builder()
                .event(EVENT_DELETE)
                .userId(userId)
                .username(String.valueOf(userId))
                .build());
    }

    @Override
    public int enqueueFullSync() {
        if (!properties.isEnabled()) {
            return 0;
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .isNotNull(SysUser::getUserId)
                .isNotNull(SysUser::getUsername));
        users.forEach(this::enqueueUpsert);
        return users.size();
    }

    @Override
    public int processPending() {
        if (!properties.isEnabled() || StringUtils.isBlank(properties.getApiKey())) {
            return 0;
        }
        List<OaUserSyncOutbox> events = outboxMapper.selectList(new LambdaQueryWrapper<OaUserSyncOutbox>()
                .in(OaUserSyncOutbox::getStatus, OaUserSyncOutbox.STATUS_PENDING, OaUserSyncOutbox.STATUS_FAILED)
                .lt(OaUserSyncOutbox::getRetryCount, properties.getMaxRetryCount())
                .orderByAsc(OaUserSyncOutbox::getCreatedTime)
                .last("limit " + Math.max(1, properties.getBatchSize())));

        int successCount = 0;
        for (OaUserSyncOutbox event : events) {
            if (push(event)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("baseUrl", properties.getBaseUrl());
        status.put("apiKeyConfigured", StringUtils.isNotBlank(properties.getApiKey()));
        status.put("pending", countByStatus(OaUserSyncOutbox.STATUS_PENDING));
        status.put("success", countByStatus(OaUserSyncOutbox.STATUS_SUCCESS));
        status.put("failed", countByStatus(OaUserSyncOutbox.STATUS_FAILED));
        return status;
    }

    @Scheduled(fixedDelayString = "${oa.sync.retry-delay-ms:30000}")
    public void scheduledRetry() {
        try {
            processPending();
        } catch (Exception e) {
            log.warn("OA 用户同步重试任务失败: {}", e.getMessage());
        }
    }

    @Scheduled(
            fixedDelayString = "${oa.sync.full-sync-delay-ms:1800000}",
            initialDelayString = "${oa.sync.full-sync-initial-delay-ms:60000}"
    )
    public void scheduledFullSync() {
        try {
            int count = enqueueFullSync();
            if (count > 0) {
                log.info("已加入 OA 用户全量同步队列: {} 条", count);
            }
        } catch (Exception e) {
            log.warn("OA 用户全量同步任务失败: {}", e.getMessage());
        }
    }

    private void enqueue(OaUserSyncPayload payload) {
        try {
            outboxMapper.insert(OaUserSyncOutbox.builder()
                    .eventType(payload.getEvent())
                    .userId(payload.getUserId())
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OaUserSyncOutbox.STATUS_PENDING)
                    .retryCount(0)
                    .build());
        } catch (Exception e) {
            log.warn("写入 OA 用户同步队列失败 userId={}: {}", payload.getUserId(), e.getMessage());
        }
    }

    private OaUserSyncPayload buildUpsertPayload(SysUser user) {
        return OaUserSyncPayload.builder()
                .event(EVENT_UPSERT)
                .userId(user.getUserId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .password(user.getPassword())
                .build();
    }

    private boolean push(OaUserSyncOutbox event) {
        try {
            OaUserSyncPayload payload = objectMapper.readValue(event.getPayload(), OaUserSyncPayload.class);
            String response = httpClientUtil.post(syncUrl(), payload, String.class,
                    Map.of("X-API-Key", properties.getApiKey()),
                    Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())));

            JsonNode body = objectMapper.readTree(response);
            if (body.path("code").asInt(200) != 200) {
                throw new IllegalStateException(body.path("msg").asText("OA sync failed"));
            }

            event.setStatus(OaUserSyncOutbox.STATUS_SUCCESS);
            event.setLastError(null);
            outboxMapper.updateById(event);
            return true;
        } catch (Exception e) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            event.setRetryCount(retryCount + 1);
            event.setStatus(OaUserSyncOutbox.STATUS_FAILED);
            event.setLastError(StringUtils.left(e.getMessage(), 1000));
            outboxMapper.updateById(event);
            log.warn("推送 OA 用户同步失败 eventId={}, userId={}: {}", event.getId(), event.getUserId(), e.getMessage());
            return false;
        }
    }

    private String syncUrl() {
        return StringUtils.removeEnd(properties.getBaseUrl(), "/") + "/system/user/sync";
    }

    private Long countByStatus(Integer status) {
        return outboxMapper.selectCount(new LambdaQueryWrapper<OaUserSyncOutbox>()
                .eq(OaUserSyncOutbox::getStatus, status));
    }
}
