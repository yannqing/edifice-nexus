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

/**
 * 个人绩效总览数据（/report/my-performance）
 */
export interface PerformanceData {
  projectCount: number;
  totalHours: number;
  managementHours: number;
  basicHours: number;
  intellectualHours: number;
  paidOutputValue: number;
  totalOutputValue: number;
}

/**
 * 个人绩效 - 参与项目明细（/report/my-project-details）
 */
export interface ProjectDetail {
  projectId: string;
  projectName: string;
  projectCode: string;
  projectStatus: number;
  category: string;
  role: string;
  totalHours: number;
  outputValue: number;
}

/**
 * 个人绩效 - 产值发放记录（/report/my-payments）
 */
export interface PaymentRecord {
  distributionId: string;
  projectName: string;
  stageName: string;
  amount: number;
  status: number;
  paidTime: string | null;
}
