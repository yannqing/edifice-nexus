import type { PageResult, ProjectDetailVo, ProjectListVo } from "@/types/project";
import type { ProjectArchiveVo, ArchiveSummaryVo } from "@/types/project-archive";
import type { ProjectFileVo } from "@/types/project-file";

export interface ProjectLifecycleVo {
  project: ProjectDetailVo;
  archive: ProjectArchiveVo;
  summary: ArchiveSummaryVo;
  stages: LifecycleStageVo[];
  events: LifecycleEventVo[];
  recentFiles: ProjectFileVo[];
}

export interface LifecycleStageVo {
  projectStageId: string;
  stageName: string;
  stageStatus: number;
  stageOutput: number | null;
  benefitInclusionRatio: number | null;
  inspectionCount: number;
  latestInspectionStatus: number | null;
  outputValueCount: number;
  paidOutputAmount: number | null;
  collectionAmount: number | null;
  projectFileCount: number;
  latestActivityTime: string | null;
}

export interface LifecycleEventVo {
  eventId: string;
  eventType: string;
  eventTypeLabel: string;
  title: string;
  content: string | null;
  status: number | null;
  operatorName: string | null;
  link: string | null;
  occurredTime: string | null;
}

export interface GetLifecycleProjectListParams {
  keywords?: string;
  projectStatus?: number;
  projectType?: number;
  current?: number;
  pageSize?: number;
}

export type LifecycleProjectPageResult = PageResult<ProjectListVo>;
