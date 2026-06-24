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
import com.qsy.edifice.utils.FileUtils;
import com.qsy.edifice.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.qsy.edifice.common.Constant.*;

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

    @Value("${app.url}")
    private String appUrl;

    @Value("${file.upload-image-url}")
    private String uploadImagePath;

    @Value("${file.upload-audio-url}")
    private String uploadAudioPath;

    @Value("${file.upload-document-url}")
    private String uploadDocumentPath;

    @Value("${file.storage-type:local}")
    private String storageType;

    @Value("${file.upload-common-url:.}")
    private String uploadCommonPath;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private FileUtils fileUtils;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public FilesVo uploadImageAndReturnVo(MultipartFile image, HttpServletRequest request) throws IOException {
        return uploadFile(image, uploadImagePath, IMAGE_FILE_TYPE, request);
    }

    @Override
    public FilesVo uploadAudioAndReturnVo(MultipartFile audio, HttpServletRequest request) throws IOException {
        return uploadFile(audio, uploadAudioPath, AUDIO_FILE_TYPE, request);
    }

    @Override
    public FilesVo uploadDocumentAndReturnVo(MultipartFile document, HttpServletRequest request) throws IOException {
        return uploadFile(document, uploadDocumentPath, DOCUMENT_FILE_TYPE, request);
    }

    @Override
    public ResponseEntity<FileSystemResource> downloadFile(Long fileId, HttpServletRequest request) {
        Optional.ofNullable(fileId)
                .orElseThrow(() -> new BusinessException(ErrorType.ARGS_NOT_NULL));

        Files downloadFile = this.getById(fileId);

        Optional.ofNullable(downloadFile)
                .orElseThrow(() -> new BusinessException(ErrorType.FILE_NOT_FOUND));

        assertCanDownload(downloadFile, request);

        ResponseEntity<FileSystemResource> downloadFileResult = fileUtils.downloadFile(downloadFile);

        // 更新文件的下载次数 +1
        downloadFile.setDownloadCount(downloadFile.getDownloadCount() + 1);
        this.updateById(downloadFile);

        return downloadFileResult;
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
            return jwtUtils.getUserIdFromToken(request.getHeader("token"));
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

    /**
     * 通用文件上传方法（返回FilesVo，支持自定义原始文件名）TODO 后续同步为 kafka 异步解耦处理
     * @param file 文件
     * @param subPath 子路径（如image, audio）
     * @param type 文件类型
     * @return FilesVo
     * @throws IOException IO异常
     */
    private FilesVo uploadFile(MultipartFile file, String subPath, String type, HttpServletRequest request) throws IOException {
        // 参数校验
        if (file == null) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL.getMessage());
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException(ErrorType.ARGS_NOT_NULL.getMessage());
        }
        
        // 校验文件类型
        String fileExtension = getFileExtension(fileName);

        // 生成新 UUID 文件名
        UUID uuid = UUID.randomUUID();
        String newFilename = replaceFilename(fileName, uuid.toString());

        // 获取当前登录用户ID
        Long uploadUserId = jwtUtils.getUserIdFromToken(request.getHeader("token"));

        String accessUrl = fileUtils.uploadFile(file, subPath, type, fileName, newFilename, fileExtension);

        // 图片类型生成缩略图
        String thumbnailUrl = null;
        if (IMAGE_FILE_TYPE.equals(type)) {
            File sourceFile = new File(uploadCommonPath + File.separator + subPath
                    + File.separator + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                    + File.separator + newFilename);
            thumbnailUrl = fileUtils.generateThumbnail(sourceFile, subPath, newFilename);
        }

        // 获取文件 md5 和 SHA-256
        String fileMd5 = DigestUtils.md5Hex(file.getInputStream());
        String fileHash = DigestUtils.sha256Hex(file.getInputStream());

        // 根据存储类型生成完整的访问 URL
        String fullUrl = appUrl + accessUrl;

        // 保存文件信息到数据库并返回FilesVo
        FilesVo filesVo = saveFileRecord(
            uploadUserId,
            newFilename,
            fileName,
            fileExtension,
            accessUrl,
            fullUrl,
            thumbnailUrl,
            fileMd5,
            fileHash,
            file.getSize(),
            file.getContentType(),
            type,
            request
        );
        
        log.info("文件上传成功并保存到数据库，文件ID：{}", filesVo.getFileId());
        return filesVo;
    }

    /**
     * 自定义方法，用来替换文件名
     * @param filename 原文件名
     * @param uuid 新文件名
     * @return 替换结果
     */
    private String replaceFilename(String filename, String uuid) {
        int index = filename.lastIndexOf(".");
        return uuid + filename.substring(index);
    }

    /**
     * 获取文件扩展名
     * @param filename 文件名
     * @return 文件扩展名（不包含点号）
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 保存文件到数据库
     */
    private FilesVo saveFileRecord(Long uploadUserId, String fileName, String OriginalName, String fileExtension, String filePath, String fileUrl, String thumbnailUrl, String fileMd5, String fileHash, Long fileSize, String mimeType, String fileType, HttpServletRequest request) {
        Files file = new Files();
        file.setUploadUserId(uploadUserId);
        file.setFileType(fileType);
        file.setFileName(fileName);
        file.setOriginalName(OriginalName);
        file.setDisplayName(OriginalName);
        file.setFileExtension(fileExtension);
        file.setFileUrl(fileUrl);
        file.setFilePath(filePath);
        file.setThumbnailUrl(thumbnailUrl);
        file.setFileSize(fileSize);
        file.setFileMd5(fileMd5);
        file.setFileHash(fileHash);
        file.setMimeType(mimeType);
        file.setDownloadCount(0);
        file.setPreviewCount(0);
        file.setAccessCount(0);
        file.setShareCount(0);
        file.setStatus(1); // 1-成功
        file.setUploadIp(getRealClientIp(request));

        this.save(file);

        log.info("保存文件记录成功: uploadUserId={}, fileName={}, fileUrl={}", uploadUserId, fileName, fileUrl);

        return FilesVo.objToVo(file);
    }

    /**
     * 获取真实 ip TODO 后续换位置（多个地方使用）
     * @param request
     * @return
     */
    private String getRealClientIp(HttpServletRequest request) {
        // 优先从 X-Forwarded-For 获取
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 最后才使用 getRemoteAddr
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For 可能包含多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
