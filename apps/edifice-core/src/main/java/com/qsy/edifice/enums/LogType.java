package com.qsy.edifice.enums;

import com.qsy.edifice.common.BaseTypeInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LogType implements BaseTypeInterface<Integer> {

    AUTH_LOG(1, "认证操作")
    ;

    private final Integer code;
    private final String message;
}
