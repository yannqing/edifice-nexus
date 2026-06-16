import type { ContractVo, PageResult, ProjectTypeVo } from "@/types/project";
import type { ProjectDetailVo } from "@/types/project";
import type { ProjectFileVo } from "@/types/project-file";

export interface ProjectArchiveVo {
  projectId: string;
  projectName: string;
  projectCode: string;
  projectType: ProjectTypeVo | null;
  projectStatus: number;
  contract: ContractVo | null;
  contractAmount: number;
  fileCount: number;
  completedStageCount: number;
  totalStageCount: number;
  archiveReady: boolean;
  archiveWarning: string | null;
  archiveStatus: number;
  archiveTime: string | null;
  archiveUserId: string | null;
  archiveUserName: string | null;
  archiveRemark: string | null;
  projectStartTime: string | null;
  projectEndTime: string | null;
  updatedTime: string | null;
}

export interface GetProjectArchiveListParams {
  keywords?: string;
  projectType?: number;
  current?: number;
  pageSize?: number;
}

export interface ArchiveProjectParams {
  archiveRemark?: string;
}

export type ProjectArchivePageResult = PageResult<ProjectArchiveVo>;

export interface ProjectArchiveDetailVo {
  project: ProjectDetailVo;
  archive: ProjectArchiveVo;
  summary: ArchiveSummaryVo;
  checklist: ArchiveChecklistItemVo[];
  inspections: ArchiveInspectionVo[];
  outputValues: ArchiveOutputValueVo[];
  collections: ArchiveCollectionVo[];
  projectFiles: ProjectFileVo[];
}

export interface ArchiveSummaryVo {
  contractAmount: number;
  totalOutputAmount: number;
  paidOutputAmount: number;
  totalCollectionAmount: number;
  stageCount: number;
  completedStageCount: number;
  inspectionCount: number;
  outputValueCount: number;
  collectionCount: number;
  projectFileCount: number;
}

export interface ArchiveChecklistItemVo {
  itemKey: string;
  itemName: string;
  status: "pass" | "warning" | "fail" | string;
  description: string;
}

export interface ArchiveInspectionVo {
  inspectionFormId: string;
  inspectionFormCode: string;
  projectStageId: string | null;
  stageName: string | null;
  inspectionFormStatus: number;
  applyUserName: string | null;
  createdTime: string | null;
}

export interface ArchiveOutputValueVo {
  outputValueId: string;
  projectStageId: string | null;
  stageName: string | null;
  quarter: string | null;
  totalAmount: number | null;
  status: number;
  submitTime: string | null;
  paidTime: string | null;
}

export interface ArchiveCollectionVo {
  collectionRecordId: string;
  projectStageId: string | null;
  stageName: string | null;
  amount: number | null;
  collectDate: string | null;
  recordUserName: string | null;
  remark: string | null;
}
