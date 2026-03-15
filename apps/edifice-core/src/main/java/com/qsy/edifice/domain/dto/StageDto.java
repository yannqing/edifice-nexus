package com.qsy.edifice.domain.dto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StageDto {


    /**
     * 项目id
     */

    private Long projectId;

    /**
     * 阶段名称
     */

    private String stageName;

    /**
     * 阶段状态：0-未开始/1-进行中/2-待验收/3-已验收/4-已驳回/5-待分配/6-已完成
     */

    private Integer stageStatus;

    /**
     * 阶段产值比例
     */

    private BigDecimal stageOutput;

    /**
     * 创建时间
     */

    private LocalDateTime createdTime;

    /**
     * 更新时间
     */

    private LocalDateTime updatedTime;
}
