package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "工时记录VO")
public class TimesheetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long timesheetId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String projectName;

    private String projectCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectStageId;

    private String stageName;

    /** 0-管理工作/1-基础工作/2-智励工作 */
    private Integer workType;

    private LocalDate workDate;

    private BigDecimal hours;

    private String description;

    /** 0-草稿/1-已提交 */
    private Integer status;

    private LocalDateTime createdTime;
}
