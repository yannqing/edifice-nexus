package com.qsy.edifice.service;

import com.qsy.edifice.domain.vo.ProjectImportResultVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 项目 Excel 导入导出服务接口
 */
public interface ProjectExcelService {

    /**
     * 导出全部项目为 Excel
     * @param projectIds 指定项目id列表（为空则导出全部）
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void exportProjects(List<Long> projectIds, HttpServletResponse response) throws IOException;

    /**
     * 下载导入模板
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void downloadTemplate(HttpServletResponse response) throws IOException;

    /**
     * 导入项目
     * @param file Excel文件
     * @param contractFileIds 通过统一上传获取的合同主文件ID
     * @param userId 当前登录用户id
     * @param request 当前请求，用于文件访问权限校验
     * @return 导入计数、错误及描述
     * @throws IOException IO异常
     */
    ProjectImportResultVo importProjects(MultipartFile file, List<Long> contractFileIds, Long userId,
                                        HttpServletRequest request) throws IOException;
}
