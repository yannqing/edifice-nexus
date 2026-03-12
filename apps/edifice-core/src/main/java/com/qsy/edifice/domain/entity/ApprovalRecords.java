package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("approval_records")
public class ApprovalRecords implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审批记录id
     */
    @TableId(value = "approval_record_id", type = IdType.ASSIGN_ID)
    private Long approvalRecordId;

    /**
     * 审批类型：0-项目文件上传/1-验工审批/2-产值分配审批/3-工时填写
     */
    @TableField("approval_record_type")
    private Integer approvalRecordType;

    /**
     * 对应业务id
     */
    @TableField("inspection_form_id")
    private Long inspectionFormId;

    /**
     * 审批人id
     */
    @TableField("approver")
    private Long approver;

    /**
     * 审批说明
     */
    @TableField("approval_description")
    private String approvalDescription;

    /**
     * 审批状态：0-待审核/1-已通过/2-已拒绝
     */
    @TableField("inspection_form_status")
    private Integer inspectionFormStatus;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除
     */
    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
