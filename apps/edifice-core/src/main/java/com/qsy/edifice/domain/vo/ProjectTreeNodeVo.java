package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ProjectTreeNodeVo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    private long childCount;
    private boolean selfMatches;
    // Null for a grouping parent the user cannot read. Never expose its business fields.
    private ProjectListVo project;
}
