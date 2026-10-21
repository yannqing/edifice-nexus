package com.qsy.edifice.service;

import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileUploadCompletionTests {
    FileUploadCompletionService service;
    UnifiedFileUploadService uploads;
    CountDownLatch release;
    UnifiedFileUploadService.SessionVo state(String status, List<Integer> parts, FilesVo file) {
        return new UnifiedFileUploadService.SessionVo("id", 5L * 1024 * 1024 * 1024, 5 * 1024 * 1024,
                2, status, parts, Map.of(), file);
    }
    @BeforeEach void setup() {
        service = new FileUploadCompletionService(); uploads = mock(UnifiedFileUploadService.class);
        ReflectionTestUtils.setField(service, "uploads", uploads);
        release = new CountDownLatch(1);
        when(uploads.status("id", 7L)).thenReturn(state("uploading", List.of(1, 2), null));
        when(uploads.status("id", 8L)).thenThrow(new BusinessException("没有权限"));
    }
    @AfterEach void close() { release.countDown(); service.close(); }

    @Test void verificationIsNonblockingIdempotentAndOwnerScoped() throws Exception {
        FilesVo file = new FilesVo();
        when(uploads.complete("id", 7L)).thenAnswer(call -> {
            assertTrue(release.await(2, TimeUnit.SECONDS));
            return state("completed", List.of(1, 2), file);
        });
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> assertEquals("verifying", service.start("id", 7L).status()));
        assertEquals("verifying", service.start("id", 7L).status());
        assertThrows(BusinessException.class, () -> service.start("id", 8L));
        assertThrows(BusinessException.class, () -> service.status("id", 8L));
        assertThrows(BusinessException.class, () -> service.cancel("id", 7L));
        release.countDown();
        var result = service.status("id", 7L);
        for (int i = 0; i < 100 && !"completed".equals(result.status()); i++) { Thread.sleep(10); result = service.status("id", 7L); }
        assertSame(file, result.file());
        verify(uploads, times(1)).complete("id", 7L);
    }

    @Test void missingPartsDoNotQueueAndFailureIsRetryable() throws Exception {
        when(uploads.status("id", 7L)).thenReturn(state("uploading", List.of(1), null));
        assertThrows(BusinessException.class, () -> service.start("id", 7L));
        verify(uploads, never()).complete(any(), any());
        when(uploads.status("id", 7L)).thenReturn(state("uploading", List.of(1, 2), null));
        when(uploads.complete("id", 7L)).thenThrow(new IOException("test storage failure"));
        try { service.start("id", 7L); } catch (BusinessException ignored) { return; }
        for (int i = 0; i < 100; i++) {
            try { service.status("id", 7L); Thread.sleep(10); }
            catch (BusinessException expected) { return; }
        }
        fail("Failed verification was not reported");
    }
}
