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

export interface OutputAllocationRule {
  ruleVersionId: string | null;
  projectTypeId: string;
  projectTypeCode: string;
  projectTypeName: string;
  versionNo: number;
  employeePoolRate: number;
  companyBaseRate: number;
  effectiveTime?: string | null;
  stages: OutputAllocationStageRule[];
}

export interface SaveOutputAllocationRuleParams {
  employeePoolRate: number;
  companyBaseRate: number;
  stages: Array<{
    stageName: string;
    stageOrder: number;
    workRules: Array<{
      workType: number;
      workWeight: number;
      projectCapRate: number | null;
    }>;
  }>;
}
