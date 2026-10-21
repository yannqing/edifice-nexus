package com.qsy.edifice.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_file_folder")
public class ProjectFileFolder {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long folderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentFolderId;
    private String folderName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableLogic
    private Integer isDelete;
}
