package com.qsy.edifice.domain.vo;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目类型id
     */
    private Long projectTypeId;

    /**
     * 项目类型名称
     */
    private String projectTypeName;

    /**
     * 项目类型唯一编码（项目编号）
     */
    private String projectCode;

    /**
     * 项目类型状态：0-禁用/1-启用
     */
    private Integer projectTypeStatus;
}
