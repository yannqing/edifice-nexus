package com.qsy.edifice.domain.dto;

import lombok.Data;

@Data
public class GetMessageCenterListDto {
    private String category;
    private Boolean unreadOnly;
    private Integer current = 1;
    private Integer pageSize = 10;
}
