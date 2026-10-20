package com.qsy.edifice.service;

import com.qsy.edifice.domain.entity.FileUploadSession;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FileUploadSessionMapper;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.service.support.FileObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedFileUploadServiceTests {
    UnifiedFileUploadService service;
    FileUploadSessionMapper sessions;
    FilesMapper files;
    FileObjectStorage storage;
    FileUploadSession session;

    @BeforeEach void setup() {
        service = new UnifiedFileUploadService();
        sessions = mock(FileUploadSessionMapper.class); files = mock(FilesMapper.class);
        storage = mock(FileObjectStorage.class);
        ReflectionTestUtils.setField(service, "sessionMapper", sessions);
        ReflectionTestUtils.setField(service, "filesMapper", files);
        ReflectionTestUtils.setField(service, "storage", storage);
        ReflectionTestUtils.setField(service, "jdbc", mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(service, "uploadsEnabled", true);
        session = new FileUploadSession(); session.setUploadId("upload-1"); session.setUserId(7L);
        session.setStatus("uploading"); session.setExpiresTime(LocalDateTime.now().plusDays(1));
        session.setTotalParts(1); session.setTotalSize(3L); session.setChunkSize(5 * 1024 * 1024);
        when(sessions.lock("upload-1")).thenReturn(session);
        when(sessions.selectById("upload-1")).thenReturn(session);
    }

    @Test void preventsCrossUserStatusUploadCompleteAndCancel() {
        assertThrows(BusinessException.class, () -> service.status("upload-1", 8L));
        assertThrows(BusinessException.class, () -> service.uploadPart("upload-1", 1,
                new MockMultipartFile("chunk", new byte[3]), 8L));
        assertThrows(BusinessException.class, () -> service.complete("upload-1", 8L));
        assertThrows(BusinessException.class, () -> service.cancel("upload-1", 8L));
        verifyNoInteractions(storage, files);
    }

    @Test void rejectsWrongPartSizeNumberExpiredAndCancelledSession() {
        assertThrows(BusinessException.class, () -> service.uploadPart("upload-1", 0,
                new MockMultipartFile("chunk", new byte[3]), 7L));
        assertThrows(BusinessException.class, () -> service.uploadPart("upload-1", 1,
                new MockMultipartFile("chunk", new byte[4]), 7L));
        session.setExpiresTime(LocalDateTime.now().minusSeconds(1));
        assertThrows(BusinessException.class, () -> service.uploadPart("upload-1", 1,
                new MockMultipartFile("chunk", new byte[3]), 7L));
        session.setStatus("cancelled");
        assertThrows(BusinessException.class, () -> service.complete("upload-1", 7L));
        verifyNoInteractions(storage, files);
    }

    @Test void cannotFinishMissingPartsAndRespectsMaintenanceFlag() {
        assertThrows(BusinessException.class, () -> service.complete("upload-1", 7L));
        ReflectionTestUtils.setField(service, "uploadsEnabled", false);
        assertThrows(BusinessException.class, () -> service.uploadPart("upload-1", 1,
                new MockMultipartFile("chunk", new byte[3]), 7L));
        verifyNoInteractions(storage, files);
    }
}
