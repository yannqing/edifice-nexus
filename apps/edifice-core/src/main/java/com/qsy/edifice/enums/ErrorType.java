package com.qsy.edifice.enums;

import com.qsy.edifice.common.BaseTypeInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorType implements BaseTypeInterface<Integer> {
    // TODO 类型统一摆放
    // common 一般异常
    SYSTEM_ERROR(50000, "系统错误！"),
    ARGS_NOT_NULL(50001, "参数不能为空，请重试！"),
    SYSTEM_USER_ERROR(50002, "系统用户无法修改，请重试！"),
    OPERATION_FAILED(50005, "操作失败，请重试！"),

    // 权限异常
    NO_AUTH_ERROR(50050, "您没有权限，请重试！"),

    //业务异常
    USER_CANNOT_NULL(50101, "用户不存在，请重试！"),
    USER_ALREADY_EXISTS(50101, "用户已存在，请重试！"),
    PROJECT_NOT_EXISTS(60000,"需要导入项目"),
    CONTRACT_FAILED(60001,"合同信息补充完整"),
    ;
    // 异常码
    private final Integer code;
    // 错误信息
    private final String message;
}
