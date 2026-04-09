package com.qsy.edifice.domain.dto;


import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetUserListDto {

    /**
     * 登录用户名
     */
    @Schema(description = "登录用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 当前页
     */
    private Integer current = 1;

    /**
     * 一页大小
     */
    private Integer pageSize = 10;
}
