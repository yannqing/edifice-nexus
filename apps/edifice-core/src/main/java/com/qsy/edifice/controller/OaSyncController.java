package com.qsy.edifice.controller;

import com.qsy.edifice.common.Code;
import com.qsy.edifice.config.OaUserSyncProperties;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.service.OaUserSyncService;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/oa-sync")
public class OaSyncController {

    @Resource
    private OaUserSyncService oaUserSyncService;

    @Resource
    private OaUserSyncProperties properties;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, Object>> status() {
        return ResultUtils.success(Code.SUCCESS, oaUserSyncService.getStatus(), "success");
    }

    @PostMapping("/users/full")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, Object>> fullSync() {
        int synced = oaUserSyncService.syncFromOa();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", synced);
        return ResultUtils.success(Code.SUCCESS, data, "success");
    }

    @PostMapping("/retry-failed")
    @PreAuthorize("hasAuthority('menu:user-management') or hasRole('SUPER_ADMIN')")
    public BaseResponse<Map<String, Object>> retryFailed() {
        int pushed = oaUserSyncService.processPending();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pushed", pushed);
        return ResultUtils.success(Code.SUCCESS, data, "success");
    }

    @PostMapping("/internal/users/full")
    public BaseResponse<Map<String, Object>> internalFullSync(
            HttpServletRequest request,
            @RequestHeader(value = "X-OA-SYNC-KEY", required = false) String apiKey
    ) {
        assertInternalRequest(request, apiKey);
        int synced = oaUserSyncService.syncFromOa();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", synced);
        return ResultUtils.success(Code.SUCCESS, data, "success");
    }

    @PostMapping("/internal/users/{oaAdminId}")
    public BaseResponse<Map<String, Object>> internalUserSync(
            @PathVariable Integer oaAdminId,
            HttpServletRequest request,
            @RequestHeader(value = "X-OA-SYNC-KEY", required = false) String apiKey
    ) {
        assertInternalRequest(request, apiKey);
        int synced = oaUserSyncService.syncOneFromOa(oaAdminId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", synced);
        return ResultUtils.success(Code.SUCCESS, data, "success");
    }

    private void assertInternalRequest(HttpServletRequest request, String apiKey) {
        if (StringUtils.isNotBlank(properties.getApiKey())) {
            if (!StringUtils.equals(properties.getApiKey(), apiKey)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid sync key");
            }
            return;
        }

        String remoteAddr = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr)
                && !"0:0:0:0:0:0:0:1".equals(remoteAddr)
                && !"::1".equals(remoteAddr)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "internal sync endpoint only accepts loopback requests");
        }
    }
}
