package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户角色列表vo")
public class SysRoleListVo implements Serializable {


    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色id
     */
    @Schema(description = "角色id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色唯一编码
     */
    @Schema(description = "角色唯一编码")
    private String roleCode;

    /**
     * 角色描述
     */
    @Schema(description = "角色描述")
    private String roleDesc;

    /**
     * 状态：0禁用 1启用
     */
    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

}
