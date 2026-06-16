import type { ContractVo, PageResult, ProjectTypeVo } from "@/types/project";

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

export type ProjectArchivePageResult = PageResult<ProjectArchiveVo>;
