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
    USER_ALREADY_EXISTS(50102, "用户已存在，请重试！"),
    PROJECT_CANNOT_NULL(50006, "项目不存在，请重试！"),

    // 阶段状态异常
    STAGE_NOT_FOUND(50201, "项目阶段不存在！"),
    STAGE_STATUS_INVALID(50202, "阶段状态不允许此操作！"),
    STAGE_HAS_PENDING_INSPECTION(50203, "该阶段存在未完成的验工单，请先处理！")
    ;
    // 异常码
    private final Integer code;
    // 错误信息
    private final String message;
}
