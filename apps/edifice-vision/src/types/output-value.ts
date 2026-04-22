export interface DistributionItemVo {
  distributionId: string;
  userId: string;
  userName: string;
  userRole: string;
  /** 0-管理工作/1-基础工作/2-智励工作 */
  workType: number;
  ratio: number;
  amount: number;
}

export interface OutputValueVo {
  outputValueId: string;
  projectId: string;
  projectName: string;
  projectCode: string;
  projectTypeName: string;
  projectStageId: string;
  stageName: string;
  stageOutput: number;
  totalAmount: number;
  /** 0-待确认/1-待审核/2-已审批/3-已发放 */
  status: number;
  submitUserName: string;
  submitTime: string;
  approvedTime: string | null;
  paidTime: string | null;
  createdTime: string;
  distributions: DistributionItemVo[];
}

export interface CreateOutputValueParams {
  projectId: string;
  projectStageId: string;
  totalAmount: number;
  distributions: {
    userId: string;
    workType: number;
    ratio: number;
    amount: number;
  }[];
}

export interface OutputValueStats {
  pendingCount: number;
  approvedCount: number;
  paidAmount: number;
  totalAmount: number;
}

export const OUTPUT_VALUE_STATUS_MAP: Record<number, string> = {
  0: "待确认",
  1: "待审核",
  2: "已审批",
  3: "已发放",
};

export const WORK_TYPE_LABELS: Record<number, string> = {
  0: "管理工作",
  1: "基础工作",
  2: "智励工作",
};
