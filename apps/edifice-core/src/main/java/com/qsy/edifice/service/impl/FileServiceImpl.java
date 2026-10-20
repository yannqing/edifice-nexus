package com.qsy.edifice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.mapper.SysUserMapper;
import com.qsy.edifice.service.FileService;
import com.qsy.edifice.service.UnifiedFileUploadService;
import com.qsy.edifice.service.support.FileDownloadResponse;
import com.qsy.edifice.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


@Slf4j
@Service
public class FileServiceImpl extends ServiceImpl<FilesMapper, Files> implements FileService {

    private static final Set<String> BROAD_FILE_AUTHORITIES = Set.of(
            "menu:all-projects",
            "menu:contract-management",
            "menu:project-files-approval",
            "menu:inspection-approval",
            "menu:output-value",
            "menu:collection",
            "menu:project-archive",
            "menu:project-lifecycle",
            "menu:bids",
            "menu:oa-applications",
            "ROLE_SUPER_ADMIN"
    );

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource private UnifiedFileUploadService unifiedUploads;
    @Resource private FileDownloadResponse downloads;

    @Override
    public FilesVo uploadImageAndReturnVo(MultipartFile image, HttpServletRequest request) throws IOException {
        return unifiedUploads.uploadDirect(image, jwtUtils.getUserIdFromToken(request.getHeader("token")));
    }

    @Override
    public FilesVo uploadAudioAndReturnVo(MultipartFile audio, HttpServletRequest request) throws IOException {
        return unifiedUploads.uploadDirect(audio, jwtUtils.getUserIdFromToken(request.getHeader("token")));
    }

    @Override
    public FilesVo uploadDocumentAndReturnVo(MultipartFile document, HttpServletRequest request) throws IOException {
        return unifiedUploads.uploadDirect(document, jwtUtils.getUserIdFromToken(request.getHeader("token")));
    }

    @Override
    public Files authorizedFile(Long fileId, HttpServletRequest request) {
        Optional.ofNullable(fileId)
                .orElseThrow(() -> new BusinessException(ErrorType.ARGS_NOT_NULL));

        Files downloadFile = this.getById(fileId);

        Optional.ofNullable(downloadFile)
                .orElseThrow(() -> new BusinessException(ErrorType.FILE_NOT_FOUND));

        assertCanDownload(downloadFile, request);
        return downloadFile;
    }

    @Override
    public ResponseEntity<StreamingResponseBody> downloadFile(Long fileId, HttpServletRequest request) {
        return downloads.response(authorizedFile(fileId, request), request);
    }

    private void assertCanDownload(Files file, HttpServletRequest request) {
        if (file.getPermissionLevel() != null && file.getPermissionLevel() == 0) {
            return;
        }
        Long currentUserId = currentUserIdFromRequest(request);
        if (currentUserId != null && Objects.equals(file.getUploadUserId(), currentUserId)) {
            return;
        }
        if (hasAnyAuthority(BROAD_FILE_AUTHORITIES)) {
            return;
        }
        Set<Long> linkedProjectIds = resolveLinkedProjectIds(file.getFileId());
        if (!linkedProjectIds.isEmpty() && isMemberOfAnyProject(currentUserId, linkedProjectIds)) {
            return;
        }
        if (isOaApplicationApplicant(file.getFileId(), currentUserId) || isBidOwner(file.getFileId(), currentUserId)) {
            return;
        }
        throw new BusinessException(ErrorType.NO_AUTH_ERROR, "无权下载该文件");
    }

    private boolean hasAnyAuthority(Set<String> expectedAuthorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expectedAuthorities.contains(authority.getAuthority()));
    }

    private Long currentUserIdFromRequest(HttpServletRequest request) {
        try {
            return jwtUtils.getUserIdFromToken(JwtUtils.requestToken(request));
        } catch (Exception e) {
            log.warn("解析下载用户 token 失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isMemberOfAnyProject(Long userId, Collection<Long> projectIds) {
        if (userId == null || projectIds == null || projectIds.isEmpty()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM project_member WHERE user_id = ? AND is_delete = 0 AND project_id IN ("
                        + projectIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","))
                        + ")",
                Integer.class,
                buildArgs(userId, projectIds)
        );
        return count != null && count > 0;
    }

    private Set<Long> resolveLinkedProjectIds(Long fileId) {
        Set<Long> projectIds = new HashSet<>();
        projectIds.addAll(queryProjectIds(
                "SELECT CAST(project_id AS SIGNED) FROM project_files " +
                        "WHERE file_id = ? AND is_delete = 0 AND project_id REGEXP '^[0-9]+$'",
                fileId));
        projectIds.addAll(queryProjectIds(
                "SELECT project_id FROM contract WHERE is_delete = 0 AND " +
                        "(contract_file = ? OR CONCAT(',', REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(contract_other_files, ''), '[', ''), ']', ''), '\"', ''), ' ', ''), ',') LIKE ?)",
                fileId, fileContainsPattern(fileId)));
        projectIds.addAll(queryProjectIds(
                "SELECT CAST(project_id AS SIGNED) FROM inspection_form WHERE is_delete = 0 " +
                        "AND project_id REGEXP '^[0-9]+$' " +
                        "AND CONCAT(',', REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(file_ids, ''), '[', ''), ']', ''), '\"', ''), ' ', ''), ',') LIKE ?",
                fileContainsPattern(fileId)));
        projectIds.addAll(queryProjectIds(
                "SELECT project_id FROM project_acceptance WHERE is_delete = 0 " +
                        "AND CONCAT(',', REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(file_ids, ''), '[', ''), ']', ''), '\"', ''), ' ', ''), ',') LIKE ?",
                fileContainsPattern(fileId)));
        projectIds.addAll(queryProjectIds(
                "SELECT project_id FROM collection_record WHERE is_delete = 0 AND voucher_file_id = ?",
                fileId));
        return projectIds;
    }

    private List<Long> queryProjectIds(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForList(sql, args).stream()
                    .map(row -> row.values().stream().findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .map(value -> value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)))
                    .toList();
        } catch (Exception e) {
            log.warn("查询文件关联项目失败: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isOaApplicationApplicant(Long fileId, Long userId) {
        if (fileId == null || userId == null) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM oa_application WHERE applicant_id = ? AND is_delete = 0 " +
                            "AND CONCAT(',', REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(attachment_ids, ''), '[', ''), ']', ''), '\"', ''), ' ', ''), ',') LIKE ?",
                    Integer.class,
                    userId,
                    fileContainsPattern(fileId)
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("查询 OA 申请附件权限失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean isBidOwner(Long fileId, Long userId) {
        if (fileId == null || userId == null) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM bid_file bf INNER JOIN bid b ON b.bid_id = bf.bid_id " +
                            "WHERE bf.file_id = ? AND bf.is_delete = 0 AND b.is_delete = 0 AND b.owner_user_id = ?",
                    Integer.class,
                    fileId,
                    userId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("查询投标附件权限失败: {}", e.getMessage());
            return false;
        }
    }

    private Object[] buildArgs(Long userId, Collection<Long> projectIds) {
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(projectIds);
        return args.toArray();
    }

    private String fileContainsPattern(Long fileId) {
        return "%," + fileId + ",%";
    }

}
