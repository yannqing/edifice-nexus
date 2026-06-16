package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TodoCenterItemVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long todoId;

    private String bizType;
    private String bizTypeLabel;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    private String bizName;
    private String title;
    private Integer status;
    private String statusLabel;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyUserId;

    private String applyUserName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentApproverId;

    private String currentApproverName;
    private Integer approvalLevel;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String link;
}
