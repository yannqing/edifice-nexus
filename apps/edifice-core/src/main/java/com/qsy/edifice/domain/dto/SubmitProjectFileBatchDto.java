package com.qsy.edifice.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class SubmitProjectFileBatchDto {
    private Long projectId;
    private List<Long> draftIds;
    private String title;
    private String description;
    private Long firstApproverId;
}
