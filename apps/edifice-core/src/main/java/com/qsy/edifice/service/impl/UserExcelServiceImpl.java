package com.qsy.edifice.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.qsy.edifice.domain.excel.UserExcelData;
import com.qsy.edifice.service.UserExcelService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用户花名册 Excel 服务。
 *
 * 员工、部门、岗位以 OA 为主数据源；edifice 只保留模板下载和镜像读取，
 * 不再直接导入写入 sys_user。
 */
@Service
public class UserExcelServiceImpl implements UserExcelService {

    @Override
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        List<UserExcelData> template = List.of(
                UserExcelData.builder()
                        .employeeNo("1")
                        .realName("张三")
                        .gender("男")
                        .ethnicity("汉")
                        .birthDate("1990.05.01")
                        .education("本科")
                        .school("示例大学")
                        .major("工程管理")
                        .position("造价员")
                        .professionalTitle("中级")
                        .certificates("一造（土建）")
                        .entryDate("2020.07.01")
                        .idCard("11010519900501001X")
                        .phone("13800138000")
                        .domicile("北京市")
                        .address("北京市朝阳区示例路1号")
                        .email("zhangsan@example.com")
                        .contractEndDate("2026.12.31")
                        .socialInsuranceDate("2020.07.01")
                        .employmentStatus("在职")
                        .resignDate("")
                        .remark("")
                        .build()
        );

        setExcelResponseHeader(response, "用户导入模板");
        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(0, "员工花名册")
                    .head(UserExcelData.class).build();
            writer.write(template, sheet);
        }
    }

    @Override
    public String importUsers(MultipartFile file) {
        return "员工主数据请在 OA 系统维护，edifice 会自动同步；当前不再支持直接导入 edifice 用户表。";
    }

    private void setExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }
}
