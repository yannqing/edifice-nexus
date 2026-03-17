package com.qsy.edifice.domain.vo;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
@Schema(description = "验工单列表返回VO")
public class InspectionFormListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 验工单id
     */
    @Schema(description = "验工单id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long inspectionFormId;

    /**
     * 验工单编号
     */
    @Schema(description = "验工单编号")
    private String inspectionFormCode;

    /**
     * 项目id
     */
    @Schema(description = "项目id")
    private String projectId;

    /**
     * 项目阶段id
     */
    @Schema(description = "项目阶段id")
    private Long projectStageId;

    /**
     * 申请人id
     */
    @Schema(description = "申请人id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyUserId;

    /**
     * 验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿
     */
    @Schema(description = "验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿")
    private Integer inspectionFormStatus;

    public static InspectionFormListVo objToVo(InspectionForm inspectionForm) {
        if (inspectionForm == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }

        InspectionFormListVo inspectionFormListVo = new InspectionFormListVo();
        BeanUtils.copyProperties(inspectionForm, inspectionFormListVo);

        return inspectionFormListVo;
    }



}
