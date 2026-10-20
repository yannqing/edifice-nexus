package com.qsy.edifice.service;

import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.exception.BusinessException;
import com.qsy.edifice.mapper.FilesMapper;
import com.qsy.edifice.service.support.FileDownloadResponse;
import com.qsy.edifice.service.support.FileObjectStorage;
import com.qsy.edifice.service.support.FileUploadPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageTests {
    @TempDir Path root;

    @Test void policyAcceptsExactLimitForAllPurposesAndRejectsOverflow() {
        for (String purpose : new String[]{"project", "contract", "inspection", "bid", "import"})
            assertDoesNotThrow(() -> FileUploadPolicy.validate("test.xlsx", FileUploadPolicy.MAX_SIZE, purpose));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.validate("test.pdf", FileUploadPolicy.MAX_SIZE + 1, "project"));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.validate("test.pdf", 0, "project"));
    }

    @Test void rejectsUnsafeNamesTypesAndPurpose() {
        for (String name : new String[]{"../file.pdf", "a\\file.pdf", "a\n.pdf", "script.php", "file", "a.exe"})
            assertThrows(BusinessException.class, () -> FileUploadPolicy.validate(name, 1, "project"));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.validate("file.pdf", 1, "import"));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.validate("file.pdf", 1, null));
        assertDoesNotThrow(() -> FileUploadPolicy.validate("drawing.DWG", 1, "project"));
    }

    @Test void legacyResolutionPreservesActualRootLayoutAndRejectsEscape() throws Exception {
        FileObjectStorage storage = new FileObjectStorage();
        ReflectionTestUtils.setField(storage, "localRoot", root.toString());
        Path source = root.resolve("documents/a.pdf");
        java.nio.file.Files.createDirectories(source.getParent());
        java.nio.file.Files.writeString(source, "original");
        Files file = new Files(); file.setFilePath("/upload/documents/a.pdf");
        assertEquals(source.toRealPath(), storage.resolveLegacy(file));
        assertThrows(BusinessException.class, () -> storage.localPath("../../outside"));
        file.setFilePath("/upload/../../outside.pdf");
        assertThrows(BusinessException.class, () -> storage.resolveLegacy(file));
    }

    @Test void rangesAreStreamedExactlyAndInvalidRangesAreRejected() throws Exception {
        FileObjectStorage storage = mock(FileObjectStorage.class);
        FileDownloadResponse downloads = new FileDownloadResponse();
        ReflectionTestUtils.setField(downloads, "storage", storage);
        ReflectionTestUtils.setField(downloads, "filesMapper", mock(FilesMapper.class));
        Files file = new Files(); file.setFileId(1L); file.setFileSize(6L);
        file.setDisplayName("test.pdf"); file.setFileExtension("pdf"); file.setMimeType("application/pdf");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/file/download/1");
        request.addHeader("Range", "bytes=2-4"); request.setParameter("mode", "preview");
        when(storage.open(file, 2L, 3L)).thenReturn(new ByteArrayInputStream("cdef".getBytes()));
        var response = downloads.response(file, request);
        assertEquals(206, response.getStatusCode().value());
        assertEquals("bytes 2-4/6", response.getHeaders().getFirst("Content-Range"));
        assertEquals("private, no-store", response.getHeaders().getCacheControl());
        assertTrue(response.getHeaders().getContentDisposition().isInline());
        ByteArrayOutputStream out = new ByteArrayOutputStream(); response.getBody().writeTo(out);
        assertEquals("cde", out.toString());
        request.removeHeader("Range"); request.addHeader("Range", "bytes=99-");
        assertEquals(416, downloads.response(file, request).getStatusCode().value());
    }

    @Test void headDoesNotReadObjectAndSvgCannotExecuteInline() throws Exception {
        FileObjectStorage storage = mock(FileObjectStorage.class);
        FileDownloadResponse downloads = new FileDownloadResponse();
        ReflectionTestUtils.setField(downloads, "storage", storage);
        Files file = new Files(); file.setFileSize(10L); file.setFileExtension("svg");
        file.setDisplayName("unsafe.svg"); file.setMimeType("image/svg+xml");
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/file/download/1");
        request.setParameter("mode", "preview");
        var response = downloads.response(file, request);
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
        response.getBody().writeTo(new ByteArrayOutputStream());
        verifyNoInteractions(storage);
    }
}
