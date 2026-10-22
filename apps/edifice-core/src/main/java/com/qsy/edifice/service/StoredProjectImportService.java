package com.qsy.edifice.service;

import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.domain.vo.ProjectImportResultVo;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.service.support.FileObjectStorage;
import com.qsy.edifice.utils.PathMultipartFile;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Service
public class StoredProjectImportService {
    @Resource private FileService files;
    @Resource private FileObjectStorage storage;
    @Resource private ProjectExcelService excel;
    private final Semaphore imports = new Semaphore(1);
    public ProjectImportResultVo importFile(Long fileId, List<Long> contractFileIds, Long userId, HttpServletRequest request) throws Exception {
        Files file = files.authorizedFile(fileId, request);
        if (!Objects.equals(file.getUploadUserId(), userId)
                || !Set.of("xls", "xlsx").contains(file.getFileExtension())) {
            throw new BusinessException("请选择本人上传的Excel文件");
        }
        if (!imports.tryAcquire()) throw new BusinessException("已有项目导入正在处理，请稍后重试");
        Path temporary = null;
        try {
            temporary = java.nio.file.Files.createTempFile("edifice-import-", "." + file.getFileExtension());
            try (InputStream input = storage.open(file)) {
                java.nio.file.Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return excel.importProjects(new PathMultipartFile(temporary, file.getOriginalName(), file.getMimeType()),
                    contractFileIds, userId, request);
        } finally {
            if (temporary != null) java.nio.file.Files.deleteIfExists(temporary);
            imports.release();
        }
    }
}
