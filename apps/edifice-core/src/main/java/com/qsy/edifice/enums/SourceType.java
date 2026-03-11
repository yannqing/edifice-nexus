package com.qsy.edifice.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 消息源
 */
@Getter
@AllArgsConstructor
public enum SourceType implements IEnum<String> {
    USER("user"),
    SYSTEM("system"),
    GROUP("group");
    private final String value;
    public static SourceType valueOfAll(String value) {
        for (SourceType sourceType : SourceType.values()) {
            if(Objects.equals(value,sourceType.getValue())){
                return sourceType;
            }

        }
        return null;
    }

    public static SourceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SourceType sourceType : SourceType.values()) {
            if (sourceType.value.equalsIgnoreCase(value)) {
                return sourceType;
            }
        }
        return null;
    }
}
