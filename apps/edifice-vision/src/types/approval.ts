export interface ApprovalRecordVo {
  approvalRecordId: string;
  approver: string | null;
  approverName: string | null;
  approvalDescription: string | null;
  /** 0-待审核/1-已通过/2-已拒绝 */
  inspectionFormStatus: number;
  createdTime: string;

  // 通用审批链扩展字段
  bizType: string | null;
  bizTypeCode: number | null;
  bizId: string | null;
  approvalLevel: number | null;
  nextApproverId: string | null;
  nextApproverName: string | null;
  parentRecordId: string | null;
  updatedTime: string | null;
}

export interface SubmitApprovalParams {
  bizType: string;
  bizId: string;
  firstApproverId: string;
  description?: string;
}

export interface ApproveParams {
  recordId: string;
  pass: boolean;
  nextApproverId?: string;
  terminate?: boolean;
  comment?: string;
}

export interface ApprovalResult {
  recordId: string;
  bizType: string;
  bizId: string;
  rejected: boolean;
  isFinal: boolean;
  nextRecordId: string | null;
}

export const APPROVAL_STATUS_MAP: Record<number, string> = {
  0: "待审核",
  1: "已通过",
  2: "已拒绝",
};

export const APPROVAL_BIZ_TYPES = [
  { value: "file", label: "项目文件" },
  { value: "inspection", label: "验工单" },
  { value: "output", label: "产值分配" },
  { value: "timesheet", label: "工时" },
  { value: "bid", label: "投标" },
  { value: "acceptance", label: "验收" },
] as const;
