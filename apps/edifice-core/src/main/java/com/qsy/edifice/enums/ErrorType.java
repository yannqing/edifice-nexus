package com.qsy.edifice.enums;

import com.qsy.edifice.common.BaseTypeInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorType implements BaseTypeInterface<Integer> {
    // ==================== 通用异常 ====================
    SYSTEM_ERROR(50000, "系统错误！"),
    ARGS_NOT_NULL(50001, "参数不能为空，请重试！"),
    SYSTEM_USER_ERROR(50002, "系统用户无法修改，请重试！"),
    OPERATION_FAILED(50005, "操作失败，请重试！"),
    ARGS_INVALID(50007, "参数非法，请重试！"),

    // ==================== 权限异常 ====================
    NO_AUTH_ERROR(50050, "您没有权限，请重试！"),

    // ==================== 用户 ====================
    USER_CANNOT_NULL(50101, "用户不存在，请重试！"),
    USER_ALREADY_EXISTS(50102, "用户已存在，请重试！"),

    // ==================== 项目 ====================
    PROJECT_CANNOT_NULL(50006, "项目不存在，请重试！"),
    PROJECT_TYPE_NOT_FOUND(50110, "项目类型不存在！"),
    CONTRACT_NOT_FOUND(50111, "项目合同不存在！"),

    // ==================== 阶段 ====================
    STAGE_NOT_FOUND(50201, "项目阶段不存在！"),
    STAGE_STATUS_INVALID(50202, "阶段状态不允许此操作！"),
    STAGE_HAS_PENDING_INSPECTION(50203, "该阶段存在未完成的验工单，请先处理！"),

    // ==================== 验工单 ====================
    INSPECTION_FORM_NOT_FOUND(50301, "验工单不存在！"),
    INSPECTION_FORM_STATUS_INVALID(50302, "验工单当前状态不允许此操作！"),

    // ==================== 产值分配 ====================
    OUTPUT_VALUE_NOT_FOUND(50401, "产值分配单不存在！"),
    OUTPUT_VALUE_STATUS_INVALID(50402, "产值分配单当前状态不允许此操作！"),

    // ==================== 工时 ====================
    TIMESHEET_NOT_FOUND(50501, "工时记录不存在！"),

    // ==================== 回款 ====================
    COLLECTION_RECORD_NOT_FOUND(50601, "回款记录不存在！"),

    // ==================== 文件 ====================
    FILE_NOT_FOUND(50701, "文件不存在！"),
    FILE_TYPE_UNSUPPORTED(50702, "文件类型不支持！"),

    // ==================== 公告 ====================
    ANNOUNCEMENT_NOT_FOUND(50801, "公告不存在！"),
    ANNOUNCEMENT_STATUS_INVALID(50802, "公告当前状态不允许此操作！"),
    ;
    // 异常码
    private final Integer code;
    // 错误信息
    private final String message;
}
