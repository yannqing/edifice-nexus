package com.qsy.edifice.domain.dto;

import lombok.Data;

@Data
public class CreateProjectFileFolderDto {
    private Long projectId;
    private Long parentFolderId;
    private String folderName;
}
