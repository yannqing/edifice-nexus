package com.qsy.edifice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OaApplicationTypeVo {

    private String type;

    private String label;

    private String category;

    private boolean attachmentSupported;
}
