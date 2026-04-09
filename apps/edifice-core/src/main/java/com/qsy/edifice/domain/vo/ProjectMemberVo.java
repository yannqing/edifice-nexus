package com.qsy.edifice.domain.vo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目成员VO，用于列表展示
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMemberVo implements Serializable {
    //
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目成员id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectMemberId;

    /**
     * 项目id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /**
     * 用户id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 项目内角色id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectRoleId;

    /**
     * 用户真实姓名
     */
    private String realName;

}
