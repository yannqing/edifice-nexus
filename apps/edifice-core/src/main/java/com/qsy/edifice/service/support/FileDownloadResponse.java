package com.qsy.edifice.service.support;

import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.exception.BusinessException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Component
public class FileDownloadResponse {
    @Resource private FileObjectStorage storage;
    @Resource private FilesMapper filesMapper;

    public ResponseEntity<StreamingResponseBody> response(Files file, HttpServletRequest request) {
        long size = file.getFileSize();
        long offset = 0; long count = size;
        boolean ranged = false;
        String range = request.getHeader(HttpHeaders.RANGE);
        if (range != null) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(range);
                if (ranges.size() != 1 || size == 0) throw new IllegalArgumentException();
                offset = ranges.get(0).getRangeStart(size);
                count = ranges.get(0).getRangeEnd(size) - offset + 1;
                if (offset >= size || count <= 0) throw new IllegalArgumentException();
                ranged = true;
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(416).header(HttpHeaders.CONTENT_RANGE, "bytes */" + size).build();
            }
        }
        try { storage.checkAvailable(file); }
        catch (IOException | RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(FileDownloadResponse.class).warn("File unavailable: {}", file.getFileId(), error);
            throw new BusinessException("文件不存在或存储暂不可用，请稍后重试或联系管理员");
        }
        long start = offset; long length = count;
        boolean inline = "preview".equals(request.getParameter("mode"))
                && Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp", "txt", "md", "csv", "mp3", "wav")
                .contains(String.valueOf(file.getFileExtension()).toLowerCase(java.util.Locale.ROOT));
        String name = file.getDisplayName() == null ? file.getFileName() : file.getDisplayName();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(count);
        try { headers.setContentType(MediaType.parseMediaType(file.getMimeType())); }
        catch (Exception e) { headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); }
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8).build());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "sandbox");
        if (ranged) headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + offset + "-" + (offset + count - 1) + "/" + size);
        StreamingResponseBody body = out -> {
            if ("HEAD".equalsIgnoreCase(request.getMethod()) || length == 0) return;
            try (InputStream input = storage.open(file, start, length)) {
                byte[] buffer = new byte[64 * 1024]; long left = length;
                while (left > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, left));
                    if (read < 0) throw new EOFException("文件读取不完整");
                    out.write(buffer, 0, read); left -= read;
                }
            }
            filesMapper.increaseDownloadCount(file.getFileId());
        };
        return new ResponseEntity<>(body, headers, ranged ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }
}
