/**
 * 绩效还原记录
 */
export interface PerformanceRestoreVo {
  restoreId: string;
  quarter: string;
  userId: string;
  realName: string;
  projectId: string | null;
  projectName: string | null;
  restoreAmount: number;
  /** 0-待还原/1-已还原 */
  status: number;
  restoredTime: string | null;
  operatorId: string | null;
  operatorName: string | null;
  remark: string | null;
  createdTime: string;
}

export interface GenerateRestoreResult {
  generated: number;
  skipped: number;
  totalAmount: number;
}

export interface MarkQuarterResult {
  restored: number;
}

export const RESTORE_STATUS_MAP: Record<number, string> = {
  0: "待还原",
  1: "已还原",
};
