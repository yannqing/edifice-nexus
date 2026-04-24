export interface DistributionItemVo {
  distributionId: string;
  userId: string;
  userName: string;
  userRole: string;
  /** 0-管理工作/1-基础工作/2-智励工作 */
  workType: number;
  /** 旧口径比例，保留展示以兼容历史数据 */
  ratio: number;
  /** 分配比例（%，60% 个人池内） */
  allocRatio: number;
  /** 完成比例（%） */
  completionRatio: number;
  /** 0-员工正常/1-员工降档/2-领导兜底/3-公司留存/4-其他金额 */
  distType: number;
  /** 下单时成员是否在职：0-离职/1-在职 */
  isActive: number;
  /** 实得金额 */
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
  /** 所属季度 YYYY-Qn */
  quarter: string | null;
  totalAmount: number;
  /** 公司留存金额（40%） */
  companyReserve: number;
  /** 领导兜底（降档差额累计） */
  leaderExtra: number;
  /** 其他金额（离职成员未发金额累计） */
  otherAmount: number;
  /** 公司补贴（只记录不计入产值） */
  subsidyAmount: number;
  /** 0-待确认/1-待审核/2-已审批/3-已发放 */
  status: number;
  submitUserName: string;
  submitTime: string;
  approvedTime: string | null;
  paidTime: string | null;
  createdTime: string;
  distributions: DistributionItemVo[];
}

export interface CreateDistributionItem {
  userId: string;
  workType: number;
  /** 分配比例（%，60% 池内，合计应为 100） */
  allocRatio: number;
  /** 完成比例（%，0-100） */
  completionRatio: number;
  /** 可空：在职快照，0-离职/1-在职 */
  isActive?: number;
}

export interface CreateOutputValueParams {
  projectId: string;
  projectStageId: string;
  /** 所属季度 YYYY-Qn */
  quarter: string;
  totalAmount: number;
  /** 公司补贴（元，只记录不计入产值） */
  subsidyAmount?: number;
  distributions: CreateDistributionItem[];
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

export const DIST_TYPE_LABELS: Record<number, string> = {
  0: "正常分配",
  1: "降档分配",
  2: "领导兜底",
  3: "公司留存",
  4: "其他金额",
};

/**
 * 生成最近若干个季度选项，如 ["2026-Q2", "2026-Q1", "2025-Q4", ...]
 */
export function generateQuarterOptions(count = 8, fromDate: Date = new Date()): string[] {
  const options: string[] = [];
  let year = fromDate.getFullYear();
  let quarter = Math.floor(fromDate.getMonth() / 3) + 1;
  for (let i = 0; i < count; i++) {
    options.push(`${year}-Q${quarter}`);
    quarter -= 1;
    if (quarter === 0) {
      quarter = 4;
      year -= 1;
    }
  }
  return options;
}

/**
 * 当前季度
 */
export function currentQuarter(): string {
  const now = new Date();
  return `${now.getFullYear()}-Q${Math.floor(now.getMonth() / 3) + 1}`;
}
