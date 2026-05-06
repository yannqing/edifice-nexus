package com.qsy.edifice.domain.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户列表查询参数")
public class GetUserListDto {

    /** 统一搜索关键字：任一命中 username / realName / employeeNo / phone 即可（OR 逻辑） */
    @Schema(description = "搜索关键字：同时模糊匹配用户名/姓名/工号/手机号（OR）")
    private String keywords;

    @Schema(description = "登录用户名（模糊匹配）")
    private String username;

    @Schema(description = "真实姓名（模糊匹配）")
    private String realName;

    @Schema(description = "员工编号（模糊匹配）")
    private String employeeNo;

    @Schema(description = "邮箱（模糊匹配）")
    private String email;

    @Schema(description = "手机号（模糊匹配）")
    private String phone;

    @Schema(description = "职务（模糊匹配）")
    private String position;

    @Schema(description = "部门ID")
    private Long departmentId;

    @Schema(description = "是否包含子部门")
    private Boolean includeChildren = true;

    /** 0-离职/1-在职 */
    @Schema(description = "在职状态：0-离职/1-在职")
    private Integer employmentStatus;

    /** 0-禁用/1-启用 */
    @Schema(description = "账号状态：0-禁用/1-启用")
    private Integer status;

    @Schema(description = "当前页")
    private Integer current = 1;

    @Schema(description = "一页大小")
    private Integer pageSize = 10;
}
