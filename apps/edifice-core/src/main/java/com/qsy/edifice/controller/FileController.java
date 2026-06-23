package com.qsy.edifice.controller;

import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.vo.FilesVo;
import com.qsy.edifice.service.FileService;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private FileService fileService;

    /** 图片/音频类小文件上限 */
    private static final long MAX_MEDIA_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    /** 文档类（含项目文件）上限 */
    private static final long MAX_DOCUMENT_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB

    /**
     * 上传图片接口
     *
     * @param image 图片文件
     * @return 返回文件vo
     * @throws IOException IO 异常
     */
    @Operation(summary = "上传图片文件", description = "支持jpg, jpeg, png, gif, bmp, webp, svg, ico, tiff, tif格式")
    @PostMapping("/upload/images")
    public BaseResponse<FilesVo> uploadImage(@RequestParam("image") MultipartFile image, HttpServletRequest request) throws IOException {
        if (image != null && image.getSize() > MAX_MEDIA_FILE_SIZE_BYTES) {
            return ResultUtils.failure("图片大小不能超过5MB");
        }
        FilesVo filesVo = fileService.uploadImageAndReturnVo(image, request);

        return ResultUtils.success(Code.SUCCESS, filesVo, "上传成功！");
    }

    /**
     * 上传音频接口
     *
     * @param audio 音频文件
     * @return 返回文件vo
     * @throws IOException IO 异常
     */
    @Operation(summary = "上传音频文件", description = "支持mp3、wav、flac、aac、ogg、wma、m4a、opus格式")
    @PostMapping("/upload/audio")
    public BaseResponse<FilesVo> uploadAudio(@RequestParam("audio") MultipartFile audio,
                                             HttpServletRequest request) throws IOException {
        FilesVo filesVo = fileService.uploadAudioAndReturnVo(audio, request);

        return ResultUtils.success(Code.SUCCESS, filesVo, "上传成功！");
    }

    /**
     * 上传文档接口
     *
     * @param document 文档文件
     * @return 返回文件vo
     * @throws IOException IO 异常
     */
    @Operation(summary = "上传文档文件", description = "支持pdf、doc、docx、xls、xlsx、ppt、pptx、txt、md、csv、rtf、odt、ods、odp格式")
    @PostMapping("/upload/document")
    public BaseResponse<FilesVo> uploadDocument(@RequestParam("document") MultipartFile document,
                                                HttpServletRequest request) throws IOException {
        if (document != null && document.getSize() > MAX_DOCUMENT_FILE_SIZE_BYTES) {
            return ResultUtils.failure("文件大小不能超过20MB");
        }
        FilesVo filesVo = fileService.uploadDocumentAndReturnVo(document, request);

        return ResultUtils.success(Code.SUCCESS, filesVo, "上传成功！");
    }

    /**
     * 下载文件
     *
     * @param fileId 要下载的文件id
     * @return 返回文件
     */
    @Operation(summary = "下载文件")
    @GetMapping("/download/{fileId}")
    public ResponseEntity<FileSystemResource> downloadImage(@PathVariable("fileId") Long fileId, HttpServletRequest request) {

        return fileService.downloadFile(fileId, request);
    }
}
