package com.qsy.edifice.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 用户花名册 Excel 导入 / 模板下载服务
 */
public interface UserExcelService {

    /**
     * 下载用户导入模板
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void downloadTemplate(HttpServletResponse response) throws IOException;

    /**
     * 按花名册 Excel 批量导入用户
     * @param file Excel 文件
     * @return 导入结果描述（包含成功/失败行数及错误明细）
     * @throws IOException IO异常
     */
    String importUsers(MultipartFile file) throws IOException;
}
