package com.qsy.edifice.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TodoCenterDetailVo {
    private TodoCenterItemVo item;
    private List<ApprovalRecordVo> approvalRecords;
}
