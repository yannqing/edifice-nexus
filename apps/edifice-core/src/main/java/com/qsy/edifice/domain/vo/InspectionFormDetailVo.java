package com.qsy.edifice.domain.vo;


import com.baomidou.mybatisplus.annotation.*;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "验工单详情vo")
public class InspectionFormDetailVo  implements Serializable {


    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 验工单id
     */
    private Long inspectionFormId;

    /**
     * 验工单编号
     */
    private String inspectionFormCode;

    /**
     * 项目id
     */
    private String projectId;

    /**
     * 项目阶段id
     */
    private Long projectStageId;

    /**
     * 验工说明
     */
    private String inspectionFormDescription;

    /**
     * 申请人id
     */
    private Long applyUserId;

    /**
     * 验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿
     */
    @TableField("inspection_form_status")
    private Integer inspectionFormStatus;

    /**
     * 附件id（json数组）
     */
    private String fileIds;

    /**
     * 审批记录id
     */
    private Long approvalRecordId;

    /**
     * 审批类型：0-项目文件上传/1-验工审批/2-产值分配审批/3-工时填写
     */
    private Integer approvalRecordType;

    /**
     * 审批人id
     */
    private Long approver;

    /**
     * 审批说明
     */
    private String approvalDescription;

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

    public static InspectionFormDetailVo objToVo(InspectionForm inspectionForm) {
        if (inspectionForm == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }

        InspectionFormDetailVo inspectionFormDetailVo= new InspectionFormDetailVo();

        BeanUtils.copyProperties(inspectionForm, inspectionFormDetailVo);

        return inspectionFormDetailVo;
    }
}
