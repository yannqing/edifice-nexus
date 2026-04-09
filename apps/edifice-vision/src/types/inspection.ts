// ==================== 验工单列表 VO ====================

export interface InspectionFormListVo {
  inspectionFormId: string;
  inspectionFormCode: string;
  projectId: string;
  projectName: string;
  projectCode: string;
  projectTypeName: string;
  projectStageId: string;
  stageName: string;
  stageOutput: number;
  contractAmount: number;
  applyUserId: string;
  applyUserName: string;
  inspectionFormStatus: number;
  createdTime: string;
}

// ==================== 审批记录 VO ====================

export interface ApprovalRecordVo {
  approvalRecordId: string;
  approver: string;
  approverName: string;
  approvalDescription: string;
  /** 0-待审核/1-已通过/2-已拒绝 */
  inspectionFormStatus: number;
  createdTime: string;
}

// ==================== 验工单详情 VO ====================

export interface InspectionFormDetailVo extends InspectionFormListVo {
  inspectionFormDescription: string;
  fileIds: string;
  updatedTime: string;
  approvalRecords: ApprovalRecordVo[] | null;
}

// ==================== 统计总览 VO ====================

export interface InspectionOverviewVo {
  pendingApproval: number;
  pendingFirstReview: number;
  approved: number;
  rejected: number;
}

// ==================== 查询参数 ====================

export interface GetInspectionListParams {
  inspectionFormCode?: string;
  projectId?: string;
  inspectionFormStatus?: number;
  current?: number;
  pageSize?: number;
}

// ==================== 提交验工单参数 ====================

export interface ApplyInspectionParams {
  projectId: number;
  projectStageId: number;
  inspectionFormDescription?: string;
  fileIds?: string;
}

// ==================== 审批参数 ====================

export interface ApprovalInspectionParams {
  inspectionFormId: string;
  /** 1-通过/2-驳回 */
  result: number;
  approvalDescription?: string;
}

// ==================== 状态映射 ====================

export const INSPECTION_STATUS_MAP: Record<number, string> = {
  0: "待审核",
  1: "审核中",
  2: "已驳回",
  3: "已通过",
  4: "草稿",
};

export const APPROVAL_RESULT_MAP: Record<number, string> = {
  0: "待审核",
  1: "通过",
  2: "拒绝",
};
