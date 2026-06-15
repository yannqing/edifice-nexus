package com.qsy.edifice.controller;

import com.qsy.edifice.common.Code;
import com.qsy.edifice.config.OaUserSyncProperties;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.OaContractProjectCreateDto;
import com.qsy.edifice.service.OaContractProjectService;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/oa-sync/internal/contracts")
public class OaContractProjectController {

    @Resource
    private OaContractProjectService oaContractProjectService;

    @Resource
    private OaUserSyncProperties properties;

    @GetMapping("/project-types")
    public BaseResponse<List<Map<String, Object>>> projectTypes(HttpServletRequest request) {
        assertInternalRequest(request);
        return ResultUtils.success(Code.SUCCESS, oaContractProjectService.listEnabledProjectTypes(), "success");
    }

    @GetMapping("/{oaContractId}/project")
    public BaseResponse<Map<String, Object>> status(
            @PathVariable Integer oaContractId,
            HttpServletRequest request
    ) {
        assertInternalRequest(request);
        return ResultUtils.success(Code.SUCCESS, oaContractProjectService.getProjectStatus(oaContractId), "success");
    }

    @PostMapping("/{oaContractId}/project")
    public BaseResponse<Map<String, Object>> create(
            @PathVariable Integer oaContractId,
            @RequestBody OaContractProjectCreateDto dto,
            HttpServletRequest request
    ) {
        assertInternalRequest(request);
        dto.setOaContractId(oaContractId);
        return ResultUtils.success(Code.SUCCESS, oaContractProjectService.createProject(dto), "工程项目创建成功");
    }

    private void assertInternalRequest(HttpServletRequest request) {
        String apiKey = request.getHeader("X-OA-SYNC-KEY");
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
