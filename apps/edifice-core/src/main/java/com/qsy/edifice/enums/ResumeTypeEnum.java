package com.qsy.edifice.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简历类型枚举
 * 定义系统支持的三种简历类型
 */
@Getter
@AllArgsConstructor
public enum ResumeTypeEnum {

    /**
     * 平衡型简历
     */
    BALANCED("balanced", "平衡型", "综合平衡，适合大多数场景"),

    /**
     * 保守型简历
     */
    CONSERVATIVE("conservative", "保守型", "稳重保守，突出稳定性和可靠性"),

    /**
     * 激进型简历
     */
    AGGRESSIVE("aggressive", "激进型", "积极进取，突出创新能力和进取心");

    /**
     * 类型代码
     */
    private final String code;

    /**
     * 类型名称
     */
    private final String name;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 根据代码获取枚举
     * @param code 类型代码
     * @return 对应的枚举，如果不存在返回null
     */
    public static ResumeTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ResumeTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断代码是否有效
     * @param code 类型代码
     * @return 如果是有效的类型代码返回true，否则返回false
     */
    public static boolean isValidCode(String code) {
        return fromCode(code) != null;
    }
}
