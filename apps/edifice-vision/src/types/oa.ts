export interface OaApplicationType {
  type: string;
  label: string;
  category: string;
  attachmentSupported: boolean;
}

export interface OaApplication {
  applicationId: string;
  applicationNo: string;
  applicationType: string;
  applicationTypeLabel: string;
  title: string;
  applicantId: string;
  applicantName?: string;
  status: number;
  priority: number;
  formData: Record<string, unknown>;
  attachmentIds: number[];
  currentRecordId?: string | null;
  currentApproverId?: string | null;
  currentApproverName?: string | null;
  submittedTime?: string | null;
  approvedTime?: string | null;
  createdTime: string;
  updatedTime?: string | null;
}

export interface OaApplicationListParams {
  keywords?: string;
  applicationType?: string;
  status?: number;
  mine?: boolean;
  current?: number;
  pageSize?: number;
}

export interface CreateOaApplicationParams {
  applicationType: string;
  title: string;
  status?: number;
  firstApproverId?: string;
  submitDescription?: string;
  priority?: number;
  formData?: Record<string, unknown>;
  attachmentIds?: number[];
}

export interface SubmitOaApplicationParams {
  firstApproverId: string;
  description?: string;
}

export interface ApproveOaApplicationParams {
  recordId: string;
  pass: boolean;
  nextApproverId?: string;
  comment?: string;
}

export interface OaSsoTokenData {
  token: string;
  oaUrl: string;
}

export const OA_STATUS_MAP: Record<number, string> = {
  0: "草稿",
  1: "审批中",
  2: "已通过",
  3: "已驳回",
  4: "已撤回",
};

export const OA_PRIORITY_MAP: Record<number, string> = {
  0: "普通",
  1: "重要",
  2: "紧急",
};
