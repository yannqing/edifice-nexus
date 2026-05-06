package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oa_application")
public class OaApplication {

    @TableId(value = "application_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicationId;

    @TableField("application_no")
    private String applicationNo;

    @TableField("application_type")
    private String applicationType;

    @TableField("title")
    private String title;

    @TableField("applicant_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    @TableField("status")
    private Integer status;

    @TableField("priority")
    private Integer priority;

    @TableField("form_data")
    private String formData;

    @TableField("attachment_ids")
    private String attachmentIds;

    @TableField("current_record_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentRecordId;

    @TableField("submitted_time")
    private LocalDateTime submittedTime;

    @TableField("approved_time")
    private LocalDateTime approvedTime;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
