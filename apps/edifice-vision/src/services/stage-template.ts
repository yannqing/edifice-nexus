import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";

export interface StageTemplateVo {
  stageId: string;
  stageName: string;
  projectTypeId: string;
  stageOutput: number;
  stageStatus: number;
  createdTime?: string;
}

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize: number;
}

export async function getStageTemplateList(
  params?: {
    projectTypeId?: string;
    status?: number;
    current?: number;
    pageSize?: number;
  },
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<StageTemplateVo>>> {
  const query: Record<string, string> = {};
  if (params?.projectTypeId) query.projectTypeId = params.projectTypeId;
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.current) query.current = String(params.current);
  if (params?.pageSize) query.pageSize = String(params.pageSize);
  return get("/stage-template/list", { params: query, signal });
}

export async function getAllStageTemplates(projectTypeId?: string): Promise<BaseResponse<StageTemplateVo[]>> {
  const query: Record<string, string> = {};
  if (projectTypeId) query.projectTypeId = projectTypeId;
  return get("/stage-template/all", { params: query });
}

export async function createStageTemplate(params: {
  stageName: string;
  projectTypeId: string;
  stageOutput: number;
  stageStatus: number;
}): Promise<BaseResponse<boolean>> {
  return post("/stage-template/create", { body: params });
}

export async function updateStageTemplate(params: {
  stageId: string;
  stageName: string;
  projectTypeId: string;
  stageOutput: number;
  stageStatus: number;
}): Promise<BaseResponse<boolean>> {
  return put("/stage-template/update", { body: params });
}

export async function deleteStageTemplate(id: string): Promise<BaseResponse<boolean>> {
  return del(`/stage-template/${id}`);
}
