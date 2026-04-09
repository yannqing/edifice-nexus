package com.qsy.edifice.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.qsy.edifice.common.BaseTypeInterface;
import lombok.Getter;

/**
 * 问题类型枚举
 * @author yanqing
 */
@Getter
public enum QuestionTypeEnum implements BaseTypeInterface<Integer> {
    /**
     * 推荐类
     */
    RECOMMENDATION(1, "推荐类"),

    /**
     * 对比类
     */
    COMPARISON(2, "对比类"),

    /**
     * 功能类
     */
    FUNCTIONAL(3, "功能类"),

    /**
     * 价格类
     */
    PRICING(4, "价格类");

    @EnumValue
    private final Integer value;

    @JsonValue
    private final String desc;

    QuestionTypeEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据值获取枚举
     * @param value 枚举值
     * @return 枚举对象
     */
    public static QuestionTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (QuestionTypeEnum type : values()) {
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
