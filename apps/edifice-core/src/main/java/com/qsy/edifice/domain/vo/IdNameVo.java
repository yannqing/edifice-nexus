package com.qsy.edifice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 简单ID名称VO，用于项目类型、项目阶段等
 * id 到 name 的映射
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdNameVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * 名称
     */
    private String name;
}
