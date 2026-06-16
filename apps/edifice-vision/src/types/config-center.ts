export type ConfigBizType = "inspection" | "file" | "output" | "timesheet" | "bid" | "acceptance" | "oa_application";

export interface ApprovalFlowNodeVo {
  flowNodeId?: string;
  flowConfigId?: string;
  nodeOrder: number;
  nodeName: string;
  approverSourceType: string;
  approverSourceId?: string | null;
  allowTerminate: number;
  requiredNode: number;
  createdTime?: string | null;
  updatedTime?: string | null;
}

export interface ApprovalFlowConfigVo {
  flowConfigId: string;
  bizType: ConfigBizType;
  bizTypeLabel: string;
  flowName: string;
  enabled: number;
  allowWithdraw: number;
  allowUrge: number;
  allowCc: number;
  allowStarterSelectNext: number;
  version: number;
  status: number;
  remark?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  createdTime?: string | null;
  updatedTime?: string | null;
  nodes: ApprovalFlowNodeVo[];
}

export interface SaveApprovalFlowConfigParams {
  flowConfigId?: string;
  bizType: ConfigBizType;
  flowName: string;
  enabled: number;
  allowWithdraw: number;
  allowUrge: number;
  allowCc: number;
  allowStarterSelectNext: number;
  version: number;
  status: number;
  remark?: string;
  nodes: ApprovalFlowNodeVo[];
}

export interface GetConfigListParams {
  bizType?: string;
  keyword?: string;
  enabled?: number;
  current?: number;
  pageSize?: number;
}

export interface BusinessRuleConfigVo {
  ruleConfigId: string;
  bizType: ConfigBizType;
  bizTypeLabel: string;
  ruleKey: string;
  ruleName: string;
  ruleValue: string;
  valueType: string;
  enabled: number;
  description?: string | null;
  updatedBy?: string | null;
  createdTime?: string | null;
  updatedTime?: string | null;
}

export interface SaveBusinessRuleConfigParams {
  ruleConfigId?: string;
  bizType: ConfigBizType;
  ruleKey: string;
  ruleName: string;
  ruleValue: string;
  valueType: string;
  enabled: number;
  description?: string;
}
