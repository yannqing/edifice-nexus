package com.qsy.edifice.enums;

// ProjectStatusEnum.java（推荐方式）
public enum ProjectStatusEnum {
    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    PENDING_ACCEPTANCE(2, "待验收"),
    ACCEPTING(3, "验收中"),
    COMPLETED(4, "已结束");

    private final int code;
    private final String desc;

    ProjectStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getTextByCode(Integer code) {
        if (code == null) return "未知";
        for (ProjectStatusEnum e : values()) {
            if (e.code == code) return e.desc;
        }
        return "无效状态";
    }
}



