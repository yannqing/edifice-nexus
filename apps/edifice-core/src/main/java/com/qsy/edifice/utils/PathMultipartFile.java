package com.qsy.edifice.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;

/** Disk-backed adapter for the existing Excel and legacy upload interfaces. */
public record PathMultipartFile(Path path, String originalFilename, String contentType) implements MultipartFile {
    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return getSize() == 0; }
    @Override public long getSize() {
        try { return Files.size(path); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public byte[] getBytes() throws IOException {
        if (getSize() > 10 * 1024 * 1024) throw new IOException("大文件请使用流式读取");
        return Files.readAllBytes(path);
    }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
    @Override public void transferTo(File dest) throws IOException { Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING); }
}
