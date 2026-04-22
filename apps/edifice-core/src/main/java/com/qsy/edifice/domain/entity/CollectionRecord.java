package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("collection_record")
public class CollectionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "collection_record_id", type = IdType.ASSIGN_ID)
    private Long collectionRecordId;

    /** 项目id */
    @TableField("project_id")
    private Long projectId;

    /** 项目阶段id（可选：预付款等情况可不关联阶段） */
    @TableField("project_stage_id")
    private Long projectStageId;

    /** 回款金额（元） */
    @TableField("amount")
    private Integer amount;

    /** 实际回款日期 */
    @TableField("collect_date")
    private LocalDate collectDate;

    /** 凭证文件id */
    @TableField("voucher_file_id")
    private Long voucherFileId;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 录入人id */
    @TableField("record_user_id")
    private Long recordUserId;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableField("is_delete")
    @TableLogic
    private Integer isDelete;
}
