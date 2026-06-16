import type { PageResult } from "@/types/project";
import type { FilesVo } from "@/types/project";

export interface ContractListVo {
  contractId: string;
  projectId: string | null;
  projectName: string | null;
  projectCode: string | null;
  projectStatus: number | null;
  contractName: string;
  contractCode: string;
  contractType: number;
  contractAmount: number;
  contractFile: string | null;
  contractOtherFiles: string | null;
  contractFileDetail: FilesVo | null;
  contractAttachmentFiles: FilesVo[];
  baseAmount: number | null;
  benefitRules: string | null;
  benefitAmount: number | null;
  benefitStatus: number | null;
  signingDate: string | null;
  preStartDate: string | null;
  preEndDate: string | null;
  createdTime: string | null;
  updatedTime: string | null;
}

export interface GetContractListParams {
  keywords?: string;
  contractType?: number;
  projectId?: string;
  current?: number;
  pageSize?: number;
}

export interface UpdateContractParams {
  contractId: string;
  contractName?: string;
  contractCode?: string;
  contractType?: number;
  contractAmount?: number;
  baseAmount?: number;
  benefitRules?: string;
  benefitAmount?: number;
  signingDate?: string;
  preStartDate?: string;
  preEndDate?: string;
}

export type ContractPageResult = PageResult<ContractListVo>;

export interface ContractChangeLogVo {
  changeLogId: string;
  contractId: string;
  projectId: string | null;
  fieldName: string;
  fieldLabel: string;
  oldValue: string | null;
  newValue: string | null;
  operatorId: string | null;
  operatorName: string | null;
  createdTime: string | null;
}
