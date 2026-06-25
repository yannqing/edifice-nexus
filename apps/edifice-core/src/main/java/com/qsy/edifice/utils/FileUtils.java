package com.qsy.edifice.utils;

import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import static com.qsy.edifice.common.Constant.*;

@Slf4j
@Component
public class FileUtils {

    @Value("${file.upload-common-url}")
    private String uploadCommonPath;

    @Value("${file.upload-prefix-url}")
    private String uploadPrefixPath;

    @Value("${file.storage-type:local}")
    private String storageType;

    /**
     * 上传文件通用工具类 TODO 缺少大文件处理（分片处理）
     * @param file
     * @param subPath
     * @param type
     * @param fileName
     * @param newFileName
     * @param fileExtension
     * @return
     * @throws IOException
     */
    public String uploadFile(MultipartFile file, String subPath, String type, String fileName, String newFileName, String fileExtension) throws IOException {

        // 验证文件的一级类型
        switch (type) {
            case IMAGE_FILE_TYPE -> {
                if (!ALLOWED_IMAGE_EXTENSIONS.contains(fileExtension)) {
                    throw new BusinessException("文件类型不支持，仅支持以下格式：" + String.join(", ", ALLOWED_IMAGE_EXTENSIONS));
                }
                // 验证图片尺寸
//                validateImageDimensions(file);
            }
            case AUDIO_FILE_TYPE -> {
                if (!ALLOWED_AUDIO_EXTENSIONS.contains(fileExtension)) {
                    throw new BusinessException("文件类型不支持，仅支持以下格式：" + String.join(", ", ALLOWED_AUDIO_EXTENSIONS));
                }
            }
        }
            // 使用本地文件存储
            log.info("使用本地存储上传文件: {}", newFileName);
            return uploadFileToLocal(file, subPath, newFileName);
    }

    /**
     * 上传文件到本地服务器
     */
    private String uploadFileToLocal(MultipartFile file, String subPath, String newFileName) throws IOException {
        // 生成基于日期的目录结构
        LocalDate now = LocalDate.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // 构建完整的目录路径
        String fullDirectoryPath = uploadCommonPath + File.separator + subPath + File.separator + datePath;

        // 创建目录（如果不存在）
        File uploadDir = new File(fullDirectoryPath);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (created) {
                log.info("文件夹{}创建成功", fullDirectoryPath);
            } else {
                log.error("文件夹{}创建失败", fullDirectoryPath);
                throw new BusinessException("文件夹创建失败");
            }
        }

        // 保存文件（流式写入，不占堆内存）
        Path filePath = Paths.get(fullDirectoryPath + File.separator + newFileName);
        try (InputStream is = file.getInputStream()) {
            java.nio.file.Files.copy(is, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // 构建访问URL（URL 必须使用正斜杠 /，不能使用 File.separator）
        String accessUrl = uploadPrefixPath + "/" + subPath + "/" + datePath + "/" + newFileName;

        return accessUrl;
    }

    /**
     * 为图片生成缩略图（JPEG 压缩，最大宽度 1200px）
     * @param sourceFile 原始图片文件
     * @param subPath 子路径
     * @param newFileName 新文件名
     * @return 缩略图的访问路径，失败返回 null
     */
    public String generateThumbnail(File sourceFile, String subPath, String newFileName) {
        try {
            BufferedImage original = ImageIO.read(sourceFile);
            if (original == null) return null;

            int maxThumbWidth = 1200;
            int origWidth = original.getWidth();

            if (origWidth <= maxThumbWidth) return null;

            int newHeight = (int) ((long) original.getHeight() * maxThumbWidth / origWidth);
            BufferedImage thumb = new BufferedImage(maxThumbWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, maxThumbWidth, newHeight, null);
            g.dispose();

            String thumbName = newFileName.replaceFirst("\\.[^.]+$", "") + "_thumb.jpg";
            LocalDate now = LocalDate.now();
            String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String fullDir = uploadCommonPath + File.separator + uploadPrefixPath + File.separator + subPath + File.separator + datePath;
            File thumbFile = new File(fullDir, thumbName);

            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f);
            javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(thumbFile);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(thumb, null, null), param);
            writer.dispose();
            ios.close();

            log.info("缩略图生成: {}, {}KB", thumbName, thumbFile.length() / 1024);
            return uploadPrefixPath + "/" + subPath + "/" + datePath + "/" + thumbName;
        } catch (Exception e) {
            log.warn("缩略图生成失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 下载文件（支持 Range 请求 / 断点续传）
     */
    public ResponseEntity<FileSystemResource> downloadFile(Files files) {
        File imageFile = new File(uploadCommonPath + files.getFilePath().replace(uploadPrefixPath, ""));

        if (!imageFile.exists()) return ResponseEntity.notFound().build();

        String contentType = files.getMimeType();
        long fileLength = imageFile.length();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("inline", files.getFileName());
        headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic().getHeaderValue());
        headers.setExpires(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7));
        headers.set("Accept-Ranges", "bytes");
        headers.setContentLength(fileLength);

        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(imageFile));
    }

    /**
     * 验证图片尺寸
     * @param file 图片文件
     * @throws BusinessException 当图片尺寸不符合要求时抛出异常
     */
    private void validateImageDimensions(MultipartFile file) throws BusinessException {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                int maxDimension = Math.max(width, height);
                int minDimension = Math.min(width, height);
                log.info("图片最长边：{}", maxDimension);
                log.info("图片最短边：{}", minDimension);

                if (maxDimension > 8000) {
                    throw new BusinessException("图片最长边不能超过8000px，当前为：" + maxDimension + "px");
                }

                if (minDimension < 20) {
                    throw new BusinessException("图片最短边不能小于20px，当前为：" + minDimension + "px");
                }
            }
        } catch (IOException e) {
            log.error("读取图片尺寸失败: {}", e.getMessage());
            throw new BusinessException("图片文件损坏或格式不正确");
        }
    }
}
