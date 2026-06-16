package com.qsy.edifice.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.OperationAuditLog;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.security.SecurityUser;
import com.qsy.edifice.service.OperationAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class OperationAuditAspect {

    private static final int REQUEST_SUMMARY_LIMIT = 4000;
    private static final int ERROR_MESSAGE_LIMIT = 1000;

    @Resource
    private OperationAuditLogService operationAuditLogService;

    @Resource
    private ObjectMapper objectMapper;

    @Around("execution(* com.qsy.edifice.controller.*.*(..))")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest(joinPoint.getArgs());
        if (request == null || shouldSkip(request)) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            AuditResult auditResult = resolveAuditResult(result);
            saveAuditLog(joinPoint, request, auditResult.status(), System.currentTimeMillis() - start, auditResult.errorMessage());
            return result;
        } catch (Throwable ex) {
            saveAuditLog(joinPoint, request, 0, System.currentTimeMillis() - start, safeExceptionMessage(ex));
            throw ex;
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/auth/")
                || path.startsWith("/admin/oa-sync/internal/")
                || path.startsWith("/file/download")) {
            return true;
        }

        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        return false;
    }

    private void saveAuditLog(ProceedingJoinPoint joinPoint,
                              HttpServletRequest request,
                              Integer status,
                              long costMs,
                              String errorMessage) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Operation operation = AnnotationUtils.findAnnotation(method, Operation.class);
            Tag tag = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), Tag.class);

            OperationAuditLog auditLog = OperationAuditLog.builder()
                    .operatorId(getOperatorId())
                    .operatorName(getOperatorName())
                    .moduleName(tag != null && StringUtils.hasText(tag.name()) ? tag.name() : joinPoint.getTarget().getClass().getSimpleName())
                    .operationName(operation != null && StringUtils.hasText(operation.summary()) ? operation.summary() : method.getName())
                    .httpMethod(request.getMethod().toUpperCase(Locale.ROOT))
                    .requestPath(request.getRequestURI())
                    .clientIp(getClientIp(request))
                    .status(status)
                    .costMs(costMs)
                    .requestSummary(buildRequestSummary(signature.getParameterNames(), joinPoint.getArgs()))
                    .errorMessage(truncate(errorMessage, ERROR_MESSAGE_LIMIT))
                    .build();
            operationAuditLogService.saveQuietly(auditLog);
        } catch (Exception e) {
            log.warn("构建操作审计日志失败: {}", e.getMessage());
        }
    }

    private HttpServletRequest getCurrentRequest(Object[] args) {
        return Arrays.stream(args)
                .filter(HttpServletRequest.class::isInstance)
                .map(HttpServletRequest.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    try {
                        return ((org.springframework.web.context.request.ServletRequestAttributes)
                                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
                    } catch (Exception ignored) {
                        return null;
                    }
                });
    }

    private Long getOperatorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            SysUser sysUser = securityUser.getSysUser();
            return sysUser != null ? sysUser.getUserId() : null;
        }
        return null;
    }

    private String getOperatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return "anonymous";
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            SysUser sysUser = securityUser.getSysUser();
            if (sysUser != null && StringUtils.hasText(sysUser.getRealName())) return sysUser.getRealName();
            if (sysUser != null && StringUtils.hasText(sysUser.getUsername())) return sysUser.getUsername();
        }
        return authentication.getName();
    }

    private String buildRequestSummary(String[] parameterNames, Object[] args) {
        ObjectNode root = objectMapper.createObjectNode();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (isIgnoredArg(arg)) continue;

            String name = parameterNames != null && i < parameterNames.length && StringUtils.hasText(parameterNames[i])
                    ? parameterNames[i]
                    : "arg" + i;
            if (isSensitiveKey(name.toLowerCase(Locale.ROOT))) {
                root.put(name, "***");
                continue;
            }
            if (arg instanceof MultipartFile multipartFile) {
                root.set(name, multipartSummary(multipartFile));
                continue;
            }
            if (arg instanceof MultipartFile[] multipartFiles) {
                ArrayNode files = objectMapper.createArrayNode();
                for (MultipartFile multipartFile : multipartFiles) {
                    files.add(multipartSummary(multipartFile));
                }
                root.set(name, files);
                continue;
            }
            try {
                root.set(name, sanitize(objectMapper.valueToTree(arg)));
            } catch (IllegalArgumentException ignored) {
                root.put(name, String.valueOf(arg));
            }
        }
        try {
            return truncate(objectMapper.writeValueAsString(root), REQUEST_SUMMARY_LIMIT);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private boolean isIgnoredArg(Object arg) {
        return arg == null
                || arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof BindingResult
                || arg instanceof InputStream
                || arg instanceof OutputStream;
    }

    private JsonNode sanitize(JsonNode node) {
        if (node == null || node.isNull()) return node;
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (isSensitiveKey(key)) {
                    objectNode.put(entry.getKey(), "***");
                } else {
                    objectNode.set(entry.getKey(), sanitize(entry.getValue()));
                }
            }
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, sanitize(arrayNode.get(i)));
            }
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        return key.contains("password")
                || key.contains("pwd")
                || key.contains("token")
                || key.contains("secret")
                || key.contains("key")
                || key.contains("authorization")
                || key.contains("cookie")
                || key.contains("idcard")
                || key.contains("identity")
                || key.contains("bankcard")
                || key.contains("phone")
                || key.contains("mobile")
                || key.contains("email");
    }

    private ObjectNode multipartSummary(MultipartFile file) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("filename", file.getOriginalFilename());
        summary.put("contentType", file.getContentType());
        summary.put("size", file.getSize());
        return summary;
    }

    private AuditResult resolveAuditResult(Object result) {
        if (result instanceof BaseResponse<?> response && !Code.SUCCESS.equals(response.getCode())) {
            return new AuditResult(0, response.getMsg());
        }
        return new AuditResult(1, null);
    }

    private String safeExceptionMessage(Throwable throwable) {
        if (throwable instanceof BusinessException && StringUtils.hasText(throwable.getMessage())) {
            return truncate(throwable.getMessage(), ERROR_MESSAGE_LIMIT);
        }
        if (throwable instanceof org.springframework.security.access.AccessDeniedException) {
            return "无权限访问";
        }
        return throwable.getClass().getSimpleName();
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp;
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String ip) {
        if (!StringUtils.hasText(ip)) return false;
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || isPrivate172Address(ip);
    }

    private boolean isPrivate172Address(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4 || !"172".equals(parts[0])) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private record AuditResult(Integer status, String errorMessage) {
    }
}
