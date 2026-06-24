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

export interface OutputValueAdjustmentDetailVo {
  adjustmentDetailId?: string | null;
  sourceOutputValueId: string;
  sourceProjectStageId: string;
  sourceStageName: string;
  sourceBaseRatio: number;
  sourceBenefitRatio: number;
  oldBaseAmountSnapshot: number | null;
  oldBenefitAmountSnapshot: number | null;
  oldStageAmount: number;
  newBaseAmountSnapshot: number;
  newBenefitAmountSnapshot: number;
  newStageAmount: number;
  alreadyAdjustedAmount: number;
  adjustmentAmount: number;
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
  /** 公司账（v0.4：60% 主体 + 降档差额 + 离职兜底） */
  companyReserve: number;
  /** v0.4 起始终为 0；保留字段防迁移破坏 */
  leaderExtra: number;
  /** 离职兜底独立记账（实际钱进 companyReserve） */
  otherAmount: number;
  /** 公司补贴（只记录不计入产值） */
  subsidyAmount: number;

  // ========== v0.4 阶段累计快照 ==========
  /** 当前阶段应得（含基本+效益） */
  stageCumulativeAmount: number | null;
  /** 历史字段：旧累计差额模型下的上一次累计；新单固定为 0 */
  previousCumulativeAmount: number | null;
  /** 本期基本部分 */
  baseAmountPart: number | null;
  /** 本期效益部分 */
  benefitAmountPart: number | null;
  /** 快照：本单创建时合同的预计效益值 */
  benefitSnapshot: number | null;
  /** 当前阶段纯产值，不含历史补差 */
  currentStageAmount: number | null;
  /** 历史阶段补差合计，可正可负 */
  adjustmentAmount: number | null;
  /** 创建时阶段的完成比例（%，0-100） */
  stageCompletionRatio?: number | null;
  /** 快照：本单创建时合同的基本金额 */
  baseAmountSnapshot: number | null;
  /** 快照：本单创建时合同的效益金额 */
  benefitAmountSnapshot: number | null;
  /** 计算版本 */
  calculationVersion: string | null;

  /** 0-待确认/1-待审核/2-已审批/3-已发放 */
  status: number;
  submitUserName: string;
  confirmUserId: string | null;
  confirmUserName: string | null;
  approveUserId: string | null;
  approveUserName: string | null;
  payUserId: string | null;
  payUserName: string | null;
  currentHandlerId: string | null;
  currentHandlerName: string | null;
  submitTime: string;
  approvedTime: string | null;
  paidTime: string | null;
  createdTime: string;
  distributions: DistributionItemVo[];
  adjustmentDetails: OutputValueAdjustmentDetailVo[];
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
  /** 确认人 */
  confirmUserId: string;
  /** v0.4 已废弃：系统自动算 */
  totalAmount?: number;
  /** 公司补贴（元，只记录不计入产值） */
  subsidyAmount?: number;
  /** 已废弃：旧累计差额模型下允许负产值的开关 */
  allowNegative?: boolean;
  distributions: CreateDistributionItem[];
}

/** 创建产值分配单前预览：当前阶段产值 + 历史补差 */
export interface OutputValuePreview {
  baseAmount: number;
  benefitAmount: number;
  baseRatio: number;
  benefitRatio: number;
  basePart: number;
  benefitPart: number;
  currentStageAmount: number;
  adjustmentAmount: number;
  thisPeriodTotal: number;
  adjustmentDetails: OutputValueAdjustmentDetailVo[];
}

export interface OutputValueStats {
  totalCount?: number;
  pendingCount: number;
  confirmCount?: number;
  reviewCount?: number;
  approvedCount: number;
  paidCount?: number;
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
