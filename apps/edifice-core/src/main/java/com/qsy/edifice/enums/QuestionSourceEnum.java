package com.qsy.edifice.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qsy.edifice.common.BaseTypeInterface;
import lombok.Getter;

/**
 * 问题来源枚举
 * @author yanqing
 */
@Getter
public enum QuestionSourceEnum implements BaseTypeInterface<Integer> {
    /**
     * 人工录入
     */
    MANUAL(1, "人工录入"),

    /**
     * AI生成
     */
    AI_GENERATED(2, "AI生成"),

    /**
     * 导入
     */
    IMPORTED(3, "导入");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String desc;

    QuestionSourceEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据值获取枚举
     * @param value 枚举值
     * @return 枚举对象
     */
    public static QuestionSourceEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (QuestionSourceEnum source : values()) {
            if (source.getValue().equals(value)) {
                return source;
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
