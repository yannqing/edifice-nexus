package com.qsy.edifice.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qsy.edifice.common.BaseTypeInterface;
import lombok.Getter;

/**
 * 关键词类型枚举
 * @author yanqing
 */
@Getter
public enum KeywordTypeEnum implements BaseTypeInterface<Integer> {
    /**
     * 核心词
     */
    CORE(1, "核心词"),

    /**
     * 扩展词
     */
    EXTENDED(2, "扩展词"),

    /**
     * 竞品词
     */
    COMPETITOR(3, "竞品词");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String desc;

    KeywordTypeEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据值获取枚举
     * @param value 枚举值
     * @return 枚举对象
     */
    public static KeywordTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (KeywordTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public Integer getCode() {
        return this.value;
    }

    @Override
    public String getMessage() {
        return this.desc;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }
}
