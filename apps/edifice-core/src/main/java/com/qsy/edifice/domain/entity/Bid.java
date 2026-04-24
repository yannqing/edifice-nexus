package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投标实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid")
public class Bid implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "bid_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bidId;

    @TableField("bid_name")
    private String bidName;

    @TableField("bid_code")
    private String bidCode;

    @TableField("owner_user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;

    @TableField("tender_amount")
    private BigDecimal tenderAmount;

    /** 业务状态：0-筹备 / 1-已投递 / 2-中标 / 3-未中标 / 4-终止 */
    @TableField("bid_status")
    private Integer bidStatus;

    @TableField("bid_date")
    private LocalDate bidDate;

    @TableField("result_date")
    private LocalDate resultDate;

    @TableField("client_name")
    private String clientName;

    @TableField("description")
    private String description;

    /** 审批状态：0-草稿 / 1-审核中 / 2-通过 / 3-驳回 */
    @TableField("approval_status")
    private Integer approvalStatus;

    @TableField("current_record_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentRecordId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
