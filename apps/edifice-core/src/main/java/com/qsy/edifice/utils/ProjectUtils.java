package com.qsy.edifice.utils;

public class ProjectUtils {
    public static String getIsShowText(Integer isShow) {
        if (isShow == null) return "未知";
        return isShow == 1 ? "公开" : "不公开";
    }
}
