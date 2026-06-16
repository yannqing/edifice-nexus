import type { ApprovalRecordVo } from "@/types/approval";

export interface ProjectFileVo {
  projectFileId: string;
  projectId: string;
  projectName: string | null;
  projectCode: string | null;
  projectStageId: string;
  stageName: string | null;
  fileId: string;
  fileName: string | null;
  fileUrl: string | null;
  fileExtension: string | null;
  fileSize: string | null;
  fileCategory: string | null;
  description: string | null;
  uploadUserId: string | null;
  uploadUserName: string | null;
  /** 0-待提交/1-审批中/2-通过/3-驳回 */
  approvalStatus: number;
  currentRecordId: string | null;
  currentApproverId: string | null;
  currentApproverName: string | null;
  createdTime: string;
  updatedTime: string | null;
  approvalChain?: ApprovalRecordVo[] | null;
}

export interface CreateProjectFileParams {
  projectId: string;
  /** 选填：哪个阶段的文件 */
  projectStageId?: string;
  fileId: string;
  /** 用户填写的文件名称（展示名） */
  fileName?: string;
  fileCategory?: string;
  description?: string;
  /** 一级审批人（项目负责人）；缺省后端按 ProjectMember ROLE_MANAGER 自动选取 */
  firstApproverId?: string;
}

export interface ApproveProjectFileParams {
  recordId: string;
  pass: boolean;
  nextApproverId?: string;
  terminate?: boolean;
  comment?: string;
}

export const PROJECT_FILE_STATUS_MAP: Record<number, string> = {
  0: "待提交",
  1: "审批中",
  2: "已通过",
  3: "已驳回",
};

export const FILE_CATEGORY_OPTIONS = ["图纸", "合同", "报告", "其他"];
