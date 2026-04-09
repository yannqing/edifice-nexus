package com.qsy.edifice.domain.vo;

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

    @Schema(description = "验工单id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long inspectionFormId;

    @Schema(description = "验工单编号")
    private String inspectionFormCode;

    @Schema(description = "项目id")
    private String projectId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "项目编码")
    private String projectCode;

    @Schema(description = "项目类型名称")
    private String projectTypeName;

    @Schema(description = "项目阶段id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    @Schema(description = "阶段名称")
    private String stageName;

    @Schema(description = "阶段产值比例")
    private java.math.BigDecimal stageOutput;

    @Schema(description = "合同金额")
    private Integer contractAmount;

    @Schema(description = "申请人id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyUserId;

    @Schema(description = "申请人姓名")
    private String applyUserName;

    @Schema(description = "验工单状态：0-待审核/1-审核中/2-已驳回/3-已通过/4-草稿")
    private Integer inspectionFormStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    public static InspectionFormListVo objToVo(InspectionForm inspectionForm) {
        if (inspectionForm == null) {
            throw new BusinessException(ErrorType.SYSTEM_ERROR);
        }
        InspectionFormListVo vo = new InspectionFormListVo();
        BeanUtils.copyProperties(inspectionForm, vo);
        return vo;
    }
}
