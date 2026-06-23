package com.qsy.edifice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目文件列表查询参数（含分页 + 筛选）。
 *
 * <p>同时服务于两个场景：
 * <ul>
 *   <li>跨项目汇总列表（projectId 为空）</li>
 *   <li>单项目内文件列表（projectId 非空）</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "项目文件列表查询参数")
public class GetProjectFileListDto {

    @Schema(description = "项目ID（可空，单项目场景传）")
    private Long projectId;

    @Schema(description = "审批状态：0-待提交/1-审批中/2-通过/3-驳回")
    private Integer approvalStatus;

    @Schema(description = "文件分类（精确匹配，如 图纸/合同/报告/其他）")
    private String fileCategory;

    @Schema(description = "搜索关键字：模糊匹配 description / fileCategory（OR）")
    private String keyword;

    @Schema(description = "当前页")
    private Integer current = 1;

    @Schema(description = "一页大小")
    private Integer pageSize = 10;
}
