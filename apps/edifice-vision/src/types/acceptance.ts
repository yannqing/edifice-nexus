import type { ApprovalRecordVo } from "@/types/approval";

export interface AcceptanceVo {
  acceptanceId: string;
  projectId: string;
  projectName: string | null;
  projectCode: string | null;
  projectStageId: string | null;
  stageName: string | null;
  /** 0-过程 / 1-成果 / 2-阶段性 */
  acceptanceType: number;
  acceptanceTypeLabel: string;
  title: string;
  content: string | null;
  fileIds: string | null;
  applyUserId: string | null;
  applyUserName: string | null;
  /** 0-待审批 / 1-审批中 / 2-通过 / 3-驳回 */
  status: number;
  currentRecordId: string | null;
  currentApproverId: string | null;
  currentApproverName: string | null;
  createdTime: string;
  updatedTime: string | null;
  approvalChain?: ApprovalRecordVo[] | null;
}

export interface CreateAcceptanceParams {
  projectId: string;
  projectStageId?: string;
  acceptanceType: number;
  title: string;
  content?: string;
  fileIds?: string;
  firstApproverId?: string;
}

export interface ApproveAcceptanceParams {
  recordId: string;
  pass: boolean;
  nextApproverId?: string;
  comment?: string;
}

export const ACCEPTANCE_TYPE_OPTIONS = [
  { value: 0, label: "过程验收" },
  { value: 1, label: "成果验收" },
  { value: 2, label: "阶段性验收" },
] as const;

export const ACCEPTANCE_STATUS_MAP: Record<number, string> = {
  0: "待审批",
  1: "审批中",
  2: "已通过",
  3: "已驳回",
};
