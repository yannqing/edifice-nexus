import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";

export interface ProjectTypeVo {
  projectTypeId: string;
  projectTypeName: string;
  projectTypeCode: string;
  projectTypeStatus: number;
  createdTime?: string;
}

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize: number;
}

export async function getProjectTypeList(
  params?: {
    keyword?: string;
    status?: number;
    current?: number;
    pageSize?: number;
  },
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<ProjectTypeVo>>> {
  const query: Record<string, string> = {};
  if (params?.keyword) query.keyword = params.keyword;
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.current) query.current = String(params.current);
  if (params?.pageSize) query.pageSize = String(params.pageSize);
  return get("/project-type/list", { params: query, signal });
}

export async function getAllProjectTypes(): Promise<BaseResponse<ProjectTypeVo[]>> {
  return get("/project-type/all");
}

export async function createProjectType(params: {
  projectTypeName: string;
  projectTypeCode: string;
  projectTypeStatus: number;
}): Promise<BaseResponse<boolean>> {
  return post("/project-type/create", { body: params });
}

export async function updateProjectType(params: {
  projectTypeId: string;
  projectTypeName: string;
  projectTypeCode: string;
  projectTypeStatus: number;
}): Promise<BaseResponse<boolean>> {
  return put("/project-type/update", { body: params });
}

export async function deleteProjectType(id: string): Promise<BaseResponse<boolean>> {
  return del(`/project-type/${id}`);
}
