package com.qsy.edifice.domain.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SettlementProjectPageVo {
    private Page<ProjectListVo> projects;
    private SettlementProjectSummaryVo summary;
    private boolean canCreate;
}
