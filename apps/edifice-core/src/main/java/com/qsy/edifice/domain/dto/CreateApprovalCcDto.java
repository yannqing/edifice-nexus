package com.qsy.edifice.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateApprovalCcDto {
    private Long recordId;
    private List<Long> ccUserIds;
    private String comment;
}
