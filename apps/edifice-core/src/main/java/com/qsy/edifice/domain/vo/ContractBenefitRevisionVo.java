package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "合同效益修正记录 VO")
public class ContractBenefitRevisionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long revisionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    private BigDecimal oldAmount;
    private BigDecimal newAmount;
    private BigDecimal deltaAmount;

    private String revisionReason;

    /** 0-非最终 / 1-最终确认 */
    private Integer isFinal;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdTime;
}
