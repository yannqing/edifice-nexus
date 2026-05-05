/** 合同效益修正记录（v0.4） */
export interface ContractBenefitRevisionVo {
  revisionId: string;
  contractId: string;
  oldAmount: number | null;
  newAmount: number;
  deltaAmount: number | null;
  revisionReason: string | null;
  /** 0-非最终 / 1-最终确认（结算锁定） */
  isFinal: number;
  operatorId: string | null;
  operatorName: string | null;
  createdTime: string;
}

export interface ReviseBenefitParams {
  newAmount: number;
  revisionReason?: string;
  isFinal?: boolean;
}
