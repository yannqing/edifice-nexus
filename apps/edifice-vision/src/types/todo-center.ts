import type { ApprovalRecordVo } from "@/types/approval";

export type TodoCenterTab = "pending" | "initiated" | "processed" | "cc";

export interface TodoCenterItem {
  todoId: string;
  bizType?: string | null;
  bizTypeLabel: string;
  bizId: string;
  bizName: string;
  title: string;
  status: number;
  statusLabel: string;
  applyUserId?: string | null;
  applyUserName?: string | null;
  currentApproverId?: string | null;
  currentApproverName?: string | null;
  approvalLevel?: number | null;
  createdTime?: string | null;
  updatedTime?: string | null;
  link: string;
}

export interface TodoCenterStats {
  pendingCount: number;
  initiatedCount: number;
  processedCount: number;
  ccCount: number;
  todayPendingCount: number;
}

export interface TodoCenterDetail {
  item: TodoCenterItem;
  approvalRecords: ApprovalRecordVo[];
}

export interface GetTodoCenterListParams {
  bizType?: string;
  keyword?: string;
  status?: number;
  current?: number;
  pageSize?: number;
}
