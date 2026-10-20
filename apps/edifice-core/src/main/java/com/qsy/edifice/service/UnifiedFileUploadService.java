package com.qsy.edifice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.domain.entity.FileUploadSession;
import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FileUploadSessionMapper;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.service.support.FileObjectStorage;
import com.qsy.edifice.service.support.FileUploadPolicy;
import com.qsy.edifice.utils.PathMultipartFile;
import jakarta.annotation.Resource;
import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class UnifiedFileUploadService {
    @Resource private FileUploadSessionMapper sessionMapper;
    @Resource private FilesMapper filesMapper;
    @Resource private JdbcTemplate jdbc;
    @Resource private FileObjectStorage storage;
    @Value("${file.uploads-enabled:true}") private boolean uploadsEnabled;
    private final Semaphore transfers = new Semaphore(3, true);

    @Data
    public static class InitRequest {
        private String fileName;
        private long totalSize;
        private String purpose;
    }

    public record SessionVo(String uploadId, long totalSize, int chunkSize, int totalParts,
                            String status, List<Integer> uploadedParts, Map<Integer, String> partHashes, FilesVo file) {}
    private record Part(int number, long size, String etag, String sha256) {}

    @Transactional(rollbackFor = Exception.class)
    public FilesVo uploadDirect(MultipartFile file, Long userId) throws IOException {
        InitRequest body = new InitRequest(); body.setFileName(file.getOriginalFilename());
        body.setTotalSize(file.getSize()); body.setPurpose("project");
        SessionVo session = init(body, userId);
        Path dir = storage.localPath(".file-uploads/" + session.uploadId());
        java.nio.file.Files.createDirectories(dir);
        try (InputStream in = file.getInputStream()) {
            for (int part = 1; part <= session.totalParts(); part++) {
                Path temporary = java.nio.file.Files.createTempFile(dir, "legacy-", ".tmp");
                try {
                    long expected = Math.min(session.chunkSize(), session.totalSize() - (long) (part - 1) * session.chunkSize());
                    try (OutputStream out = java.nio.file.Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[64 * 1024]; long left = expected;
                        while (left > 0) {
                            int n = in.read(buffer, 0, (int) Math.min(left, buffer.length));
                            if (n < 0) throw new IOException("上传未完成");
                            out.write(buffer, 0, n); left -= n;
                        }
                    }
                    uploadPart(session.uploadId(), part, new PathMultipartFile(temporary, "part", "application/octet-stream"), userId);
                } finally { java.nio.file.Files.deleteIfExists(temporary); }
            }
        }
        return complete(session.uploadId(), userId).file();
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionVo init(InitRequest request, Long userId) throws IOException {
        requireUploadsEnabled();
        FileUploadPolicy.validate(request.getFileName(), request.getTotalSize(), request.getPurpose());
        requirePurposeAuthority(request.getPurpose());
        Long count = sessionMapper.selectCount(new LambdaQueryWrapper<FileUploadSession>()
                .eq(FileUploadSession::getUserId, userId).eq(FileUploadSession::getStatus, "uploading")
                .gt(FileUploadSession::getExpiresTime, LocalDateTime.now()));
        if (count >= 20) throw new BusinessException("同时上传的文件不能超过20个，请完成或取消已有上传");
        String id = UUID.randomUUID().toString();
        String key = "objects/" + LocalDate.now().toString().replace('-', '/') + "/" + id
                + "." + FileUploadPolicy.extension(request.getFileName());
        FileUploadSession session = new FileUploadSession();
        session.setUploadId(id); session.setUserId(userId); session.setFileName(request.getFileName());
        session.setPurpose(request.getPurpose()); session.setTotalSize(request.getTotalSize());
        session.setChunkSize(FileUploadPolicy.CHUNK_SIZE);
        session.setTotalParts((int) ((request.getTotalSize() + FileUploadPolicy.CHUNK_SIZE - 1) / FileUploadPolicy.CHUNK_SIZE));
        session.setStorageType(storage.currentType()); session.setObjectKey(key);
        session.setStatus("uploading"); session.setExpiresTime(LocalDateTime.now().plusDays(7));
        if ("minio".equals(session.getStorageType())) {
            session.setStorageBucket(storage.bucket()); session.setMultipartId(storage.begin(key));
        }
        sessionMapper.insert(session);
        return vo(session);
    }

    public SessionVo status(String id, Long userId) {
        return vo(owned(sessionMapper.selectById(id), userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionVo uploadPart(String id, int number, MultipartFile chunk, Long userId) throws IOException {
        requireUploadsEnabled();
        if (!transfers.tryAcquire()) throw new BusinessException("上传通道繁忙，请稍后重试");
        Path temp = null;
        try {
            FileUploadSession session = owned(sessionMapper.lock(id), userId);
            requirePending(session);
            if (number < 1 || number > session.getTotalParts()) throw new BusinessException("分片序号不合法");
            long expected = number == session.getTotalParts()
                    ? session.getTotalSize() - (long) (number - 1) * session.getChunkSize() : session.getChunkSize();
            if (chunk == null || chunk.getSize() != expected) throw new BusinessException("分片大小与上传声明不一致");
            Path dir = storage.localPath(".file-uploads/" + session.getUploadId());
            java.nio.file.Files.createDirectories(dir);
            temp = java.nio.file.Files.createTempFile(dir, "part-", ".tmp");
            try (InputStream in = chunk.getInputStream(); OutputStream out = java.nio.file.Files.newOutputStream(temp)) {
                long actual = in.transferTo(out);
                if (actual != expected) throw new BusinessException("分片接收不完整");
            }
            String sha;
            try (InputStream in = java.nio.file.Files.newInputStream(temp)) { sha = DigestUtils.sha256Hex(in); }
            List<Part> existing = parts(id).stream().filter(p -> p.number == number).toList();
            if (!existing.isEmpty()) {
                if (!sha.equals(existing.get(0).sha256)) throw new BusinessException("已上传分片内容不同，请重新选择原文件");
                return vo(session);
            }
            String etag;
            if ("minio".equals(session.getStorageType())) {
                etag = storage.putPart(session.getStorageBucket(), session.getObjectKey(), session.getMultipartId(), number, temp);
            } else {
                java.nio.file.Files.move(temp, dir.resolve(String.valueOf(number)), StandardCopyOption.REPLACE_EXISTING);
                etag = sha;
            }
            jdbc.update("INSERT INTO file_upload_part(upload_id,part_number,size,etag,sha256) VALUES(?,?,?,?,?)",
                    id, number, expected, etag, sha);
            return vo(session);
        } finally {
            if (temp != null) java.nio.file.Files.deleteIfExists(temp);
            transfers.release();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionVo complete(String id, Long userId) throws IOException {
        requireUploadsEnabled();
        if (!transfers.tryAcquire()) throw new BusinessException("上传通道繁忙，请稍后重试");
        try {
        FileUploadSession session = owned(sessionMapper.lock(id), userId);
        if ("completed".equals(session.getStatus())) return vo(session);
        requirePending(session);
        List<Part> parts = parts(id);
        if (parts.size() != session.getTotalParts()
                || parts.stream().mapToLong(Part::size).sum() != session.getTotalSize()) {
            throw new BusinessException("分片未全部上传，无法完成上传");
        }
        Files file = new Files();
        file.setStorageType(session.getStorageType()); file.setStorageBucket(session.getStorageBucket());
        file.setObjectKey(session.getObjectKey());
        if ("minio".equals(session.getStorageType())) {
            file.setObjectVersion(storage.finish(session.getStorageBucket(), session.getObjectKey(), session.getMultipartId(),
                    parts.stream().map(p -> CompletedPart.builder().partNumber(p.number).eTag(p.etag).build()).toList()));
        } else {
            Path target = storage.localPath(session.getObjectKey());
            java.nio.file.Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".merging");
            try (OutputStream out = java.nio.file.Files.newOutputStream(temporary)) {
                for (Part part : parts) java.nio.file.Files.copy(partPath(session, part.number), out);
            }
            java.nio.file.Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }

        // Read the stored object back, checking each part's hash and the complete byte count.
        MessageDigest sha = DigestUtils.getSha256Digest(); MessageDigest md5 = DigestUtils.getMd5Digest();
        try (InputStream input = storage.open(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (Part part : parts) {
                MessageDigest partHash = DigestUtils.getSha256Digest(); long left = part.size;
                while (left > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(left, buffer.length));
                    if (read < 0) throw new BusinessException("对象存储中的文件不完整");
                    sha.update(buffer, 0, read); md5.update(buffer, 0, read); partHash.update(buffer, 0, read); left -= read;
                }
                if (!HexFormat.of().formatHex(partHash.digest()).equals(part.sha256)) throw new BusinessException("文件校验失败，请重试");
            }
            if (input.read() != -1) throw new BusinessException("实际文件大小超过声明大小");
        }
        String extension = FileUploadPolicy.extension(session.getFileName());
        file.setUploadUserId(userId); file.setFileName(session.getFileName());
        file.setOriginalName(session.getFileName()); file.setDisplayName(session.getFileName());
        file.setFileExtension(extension); file.setFileType(mediaType(extension));
        file.setMimeType(mimeType(extension)); file.setFileSize(session.getTotalSize());
        file.setFileMd5(HexFormat.of().formatHex(md5.digest())); file.setFileHash(HexFormat.of().formatHex(sha.digest()));
        file.setFilePath("/upload/" + session.getObjectKey()); file.setFileUrl(file.getFilePath());
        file.setStatus(1); file.setPermissionLevel(1); file.setIsDeleted(0);
        file.setDownloadCount(0); file.setPreviewCount(0); file.setAccessCount(0); file.setShareCount(0);
        file.setCreatedTime(LocalDateTime.now()); file.setUpdatedTime(LocalDateTime.now());
        file.setStorageVerifiedTime(LocalDateTime.now()); filesMapper.insert(file);
        session.setFileId(file.getFileId()); session.setStatus("completed"); sessionMapper.updateById(session);
        cleanupAfterCommit(session);
        return vo(session);
        } finally { transfers.release(); }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, Long userId) {
        FileUploadSession session = owned(sessionMapper.lock(id), userId);
        if (!"uploading".equals(session.getStatus())) return;
        if ("minio".equals(session.getStorageType())) storage.abort(session.getStorageBucket(), session.getObjectKey(), session.getMultipartId());
        session.setStatus("cancelled"); sessionMapper.updateById(session);
        cleanupAfterCommit(session);
    }

    private void cleanupAfterCommit(FileUploadSession session) {
        Runnable cleanup = () -> {
            Path directory = storage.localPath(".file-uploads/" + session.getUploadId());
            if (!java.nio.file.Files.exists(directory)) return;
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
            } catch (IOException ignored) { /* Scheduled cleanup retries temporary files. */ }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { cleanup.run(); }
                    });
        } else cleanup.run();
    }

    private Path partPath(FileUploadSession session, int part) {
        return storage.localPath(".file-uploads/" + session.getUploadId() + "/" + part);
    }
    private FileUploadSession owned(FileUploadSession session, Long userId) {
        if (session == null || !Objects.equals(session.getUserId(), userId)) throw new BusinessException("上传会话不存在或无权访问");
        return session;
    }
    private void requirePending(FileUploadSession session) {
        if (!"uploading".equals(session.getStatus()) || session.getExpiresTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已结束或过期，请重新上传");
        }
    }
    private void requireUploadsEnabled() { if (!uploadsEnabled) throw new BusinessException("文件存储维护中，请稍后上传"); }
    private List<Part> parts(String id) {
        return jdbc.query("SELECT part_number,size,etag,sha256 FROM file_upload_part WHERE upload_id=? ORDER BY part_number",
                (rs, row) -> new Part(rs.getInt(1), rs.getLong(2), rs.getString(3), rs.getString(4)), id);
    }
    private SessionVo vo(FileUploadSession session) {
        return new SessionVo(session.getUploadId(), session.getTotalSize(), session.getChunkSize(), session.getTotalParts(),
                session.getStatus(), parts(session.getUploadId()).stream().map(Part::number).toList(),
                parts(session.getUploadId()).stream().collect(java.util.stream.Collectors.toMap(Part::number, Part::sha256)),
                session.getFileId() == null ? null : FilesVo.objToVo(filesMapper.selectById(session.getFileId())));
    }
    private void requirePurposeAuthority(String purpose) {
        Set<String> allowed = switch (purpose) {
            case "bid" -> Set.of("menu:bids");
            case "import" -> Set.of("menu:all-projects", "menu:my-projects");
            case "inspection" -> Set.of("menu:my-projects", "menu:inspection-management");
            default -> Set.of("menu:my-projects", "menu:all-projects", "menu:contract-management", "menu:project-files-approval");
        };
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> allowed.contains(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
            throw new BusinessException("没有此业务的上传权限");
        }
    }
    private String mediaType(String extension) {
        if (Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif").contains(extension)) return "image";
        if (Set.of("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus").contains(extension)) return "audio";
        return "document";
    }
    private String mimeType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf"; case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg"; case "gif" -> "image/gif"; case "webp" -> "image/webp";
            case "txt", "md", "csv" -> "text/plain"; case "mp3" -> "audio/mpeg"; case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
