package com.qsy.edifice.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 审批业务类型
 *
 * - {@code code}  对应旧字段 {@code approval_records.approval_record_type}（tinyint）
 * - {@code ext}   对应新字段 {@code approval_records.biz_type_ext}（varchar）
 *
 * 旧取值语义保留，新增类型在尾部追加。
 */
@Getter
@AllArgsConstructor
public enum ApprovalBizType {

    /** 项目文件上传（三级审批：项目负责人 → 专业主管 → 总工） */
    FILE(0, "file", "项目文件"),

    /** 验工单审批 */
    INSPECTION(1, "inspection", "验工单"),

    /** 产值分配审批 */
    OUTPUT(2, "output", "产值分配"),

    /** 工时审批 */
    TIMESHEET(3, "timesheet", "工时"),

    /** 投标管理审批 */
    BID(4, "bid", "投标"),

    /** 成果 / 过程 / 阶段性验收审批 */
    ACCEPTANCE(5, "acceptance", "验收"),

    /** OA 统一申请单 */
    OA_APPLICATION(6, "oa_application", "OA申请");

    private final Integer code;
    private final String ext;
    private final String label;

    public static ApprovalBizType fromExt(String ext) {
        if (ext == null) return null;
        return Arrays.stream(values())
                .filter(t -> t.ext.equalsIgnoreCase(ext))
                .findFirst()
                .orElse(null);
    }

    public static ApprovalBizType fromCode(Integer code) {
        if (code == null) return null;
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
