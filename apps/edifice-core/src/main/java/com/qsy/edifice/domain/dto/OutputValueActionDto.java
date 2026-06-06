package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "产值分配单流转请求")
public class OutputValueActionDto {

    @Schema(description = "下一节点办理人id")
    private Long nextUserId;
}
