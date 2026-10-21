package com.qsy.edifice.service.support;

import com.qsy.edifice.exception.BusinessException;
import java.util.Locale;
import java.util.Set;

public final class FileUploadPolicy {
    public static final long MAX_SIZE = 5L * 1024 * 1024 * 1024;
    public static final String MAX_SIZE_LABEL = "5GB";
    public static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private static final Set<String> PURPOSES = Set.of("contract", "project", "inspection", "bid", "import");
    private static final Set<String> DOCUMENTS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf",
            "odt", "ods", "odp", "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif",
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "zip", "rar", "7z", "dwg", "dxf");

    private FileUploadPolicy() {}

    public static void validate(String name, long size, String purpose) {
        if (!PURPOSES.contains(purpose == null ? "" : purpose)) throw new BusinessException("无效的上传用途");
        if (size <= 0 || size > MAX_SIZE) throw new BusinessException("单个文件必须大于0且不能超过" + MAX_SIZE_LABEL);
        if (name == null || name.isBlank() || name.length() > 240 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(c -> c < 32)) throw new BusinessException("文件名称不合法");
        String extension = extension(name);
        if (!DOCUMENTS.contains(extension)) throw new BusinessException("不支持该文件格式");
        if ("import".equals(purpose) && !Set.of("xls", "xlsx").contains(extension)) {
            throw new BusinessException("导入仅支持xls和xlsx文件");
        }
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
