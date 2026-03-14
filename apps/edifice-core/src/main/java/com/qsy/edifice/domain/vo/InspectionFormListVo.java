package com.qsy.edifice.domain.vo;


import com.baomidou.mybatisplus.annotation.*;
import com.qsy.edifice.domain.entity.InspectionForm;
import com.qsy.edifice.enums.ErrorType;
import com.qsy.edifice.exception.BusinessException;
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
public class InspectionFormListVo implements Serializable {

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
     * 申请人id
     */
    private Long applyUserId;

    /**
     * 验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿
     */
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
