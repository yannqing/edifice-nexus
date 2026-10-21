package com.qsy.edifice.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResolveProjectFileFoldersDto {
    private Long projectId;
    private Long parentFolderId;
    private List<String> paths;
}
