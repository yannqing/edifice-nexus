export interface OutputAllocationWorkRule {
  workType: number;
  workTypeName: string;
  workWeight: number;
  projectCapRate: number | null;
}

export interface OutputAllocationStageRule {
  stageName: string;
  stageOrder: number;
  stageOutput: number | null;
  workRules: OutputAllocationWorkRule[];
}

export interface OutputAllocationWorkRate {
  workType: number;
  workTypeName: string;
  grossRate: number;
  projectRate: number;
  companyRate: number;
}

export interface OutputAllocationRule {
  ruleVersionId: string | null;
  projectTypeId: string;
  projectTypeCode: string;
  projectTypeName: string;
  versionNo: number;
  employeePoolRate: number;
  companyBaseRate: number;
  effectiveTime?: string | null;
  workRates: OutputAllocationWorkRate[];
  stages: OutputAllocationStageRule[];
}

export interface SaveOutputAllocationRuleParams {
  employeePoolRate: number;
  companyBaseRate: number;
  workRates: Array<{
    workType: number;
    grossRate: number;
    projectRate: number;
    companyRate: number;
  }>;
  stages: Array<{
    stageName: string;
    stageOrder: number;
    stageOutput: number;
    workRules: Array<{
      workType: number;
      workWeight: number;
      projectCapRate: number | null;
    }>;
  }>;
}
