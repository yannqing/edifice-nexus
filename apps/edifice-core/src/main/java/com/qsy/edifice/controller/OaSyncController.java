package com.qsy.edifice.controller;

import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.service.OaUserSyncService;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/oa-sync")
public class OaSyncController {

    @Resource
    private OaUserSyncService oaUserSyncService;

    @GetMapping("/status")
    public BaseResponse<Map<String, Object>> status() {
        return ResultUtils.success(Code.SUCCESS, oaUserSyncService.getStatus(), "success");
    }

    @PostMapping("/users/full")
    public BaseResponse<Map<String, Object>> fullSync() {
        int queued = oaUserSyncService.enqueueFullSync();
        int pushed = oaUserSyncService.processPending();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("queued", queued);
        data.put("pushed", pushed);
        return ResultUtils.success(Code.SUCCESS, data, "success");
    }

    @PostMapping("/retry-failed")
    public BaseResponse<Map<String, Object>> retryFailed() {
        int pushed = oaUserSyncService.processPending();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pushed", pushed);
        return ResultUtils.success(Code.SUCCESS, data, "success");
    }
}
