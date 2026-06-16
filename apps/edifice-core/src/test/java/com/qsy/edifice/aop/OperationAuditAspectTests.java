package com.qsy.edifice.aop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.OperationAuditLog;
import com.qsy.edifice.service.OperationAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationAuditAspectTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldMarkBusinessFailureAndRedactSensitiveFields() throws Throwable {
        OperationAuditLogService service = mock(OperationAuditLogService.class);
        OperationAuditAspect aspect = createAspect(service);
        HttpServletRequest request = request("POST", "/test/update", "127.0.0.1", "203.0.113.10");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "visible");
        payload.put("password", "plain-password");
        payload.put("apiKey", "plain-key");
        payload.put("phone", "13800138000");

        ProceedingJoinPoint joinPoint = joinPoint(request, payload, new BaseResponse<>(500, null, "业务失败"));

        aspect.audit(joinPoint);

        OperationAuditLog log = captureLog(service);
        assertEquals(0, log.getStatus());
        assertEquals("业务失败", log.getErrorMessage());
        assertEquals("203.0.113.10", log.getClientIp());

        JsonNode summary = objectMapper.readTree(log.getRequestSummary()).get("payload");
        assertEquals("visible", summary.get("name").asText());
        assertEquals("***", summary.get("password").asText());
        assertEquals("***", summary.get("apiKey").asText());
        assertEquals("***", summary.get("phone").asText());
    }

    @Test
    void shouldIgnoreSpoofedForwardedIpForDirectPublicRequest() throws Throwable {
        OperationAuditLogService service = mock(OperationAuditLogService.class);
        OperationAuditAspect aspect = createAspect(service);
        HttpServletRequest request = request("PUT", "/test/update", "198.51.100.22", "203.0.113.10");
        ProceedingJoinPoint joinPoint = joinPoint(request, Map.of("name", "visible"), new BaseResponse<>(200, true, "成功"));

        aspect.audit(joinPoint);

        OperationAuditLog log = captureLog(service);
        assertEquals(1, log.getStatus());
        assertEquals("198.51.100.22", log.getClientIp());
    }

    @Test
    void shouldRedactSensitiveStandaloneParameter() throws Throwable {
        OperationAuditLogService service = mock(OperationAuditLogService.class);
        OperationAuditAspect aspect = createAspect(service);
        HttpServletRequest request = request("POST", "/test/update", "127.0.0.1", null);
        ProceedingJoinPoint joinPoint = joinPoint(request, "plain-token", new BaseResponse<>(200, true, "成功"), "token");

        aspect.audit(joinPoint);

        OperationAuditLog log = captureLog(service);
        assertEquals("***", objectMapper.readTree(log.getRequestSummary()).get("token").asText());
    }

    @Test
    void shouldRecordAccessDeniedWithoutInternalExceptionDetails() throws Throwable {
        OperationAuditLogService service = mock(OperationAuditLogService.class);
        OperationAuditAspect aspect = createAspect(service);
        HttpServletRequest request = request("POST", "/test/update", "127.0.0.1", null);
        ProceedingJoinPoint joinPoint = joinPoint(request, Map.of("name", "visible"), null);
        when(joinPoint.proceed()).thenThrow(new org.springframework.security.access.AccessDeniedException("internal details"));

        try {
            aspect.audit(joinPoint);
        } catch (org.springframework.security.access.AccessDeniedException ignored) {
            // 业务异常必须继续交给统一权限处理器。
        }

        OperationAuditLog log = captureLog(service);
        assertEquals(0, log.getStatus());
        assertEquals("无权限访问", log.getErrorMessage());
    }

    private OperationAuditAspect createAspect(OperationAuditLogService service) {
        OperationAuditAspect aspect = new OperationAuditAspect();
        ReflectionTestUtils.setField(aspect, "operationAuditLogService", service);
        ReflectionTestUtils.setField(aspect, "objectMapper", objectMapper);
        return aspect;
    }

    private HttpServletRequest request(String httpMethod, String uri, String remoteAddr, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(httpMethod);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }

    private ProceedingJoinPoint joinPoint(HttpServletRequest request, Map<String, Object> payload, Object result) throws Throwable {
        return joinPoint(request, payload, result, "payload");
    }

    private ProceedingJoinPoint joinPoint(HttpServletRequest request, Object payload, Object result, String parameterName) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = TestController.class.getMethod("update", Map.class, HttpServletRequest.class);

        when(joinPoint.getArgs()).thenReturn(new Object[]{payload, request});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new TestController());
        when(joinPoint.proceed()).thenReturn(result);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{parameterName, "request"});
        return joinPoint;
    }

    private OperationAuditLog captureLog(OperationAuditLogService service) {
        ArgumentCaptor<OperationAuditLog> captor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(service).saveQuietly(captor.capture());
        return captor.getValue();
    }

    @Tag(name = "测试模块")
    static class TestController {
        @Operation(summary = "测试更新")
        public void update(Map<String, Object> payload, HttpServletRequest request) {
        }
    }
}
