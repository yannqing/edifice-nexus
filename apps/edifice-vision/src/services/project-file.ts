import { get, post } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  ApproveProjectFileParams,
  CreateProjectFileParams,
  ProjectFileVo,
} from "@/types/project-file";

export async function createProjectFile(
  params: CreateProjectFileParams,
): Promise<BaseResponse<number>> {
  return post<number>("/project-files/create", { body: params });
}

export async function approveProjectFile(
  params: ApproveProjectFileParams,
): Promise<BaseResponse<boolean>> {
  return post<boolean>("/project-files/approve", { body: params });
}

export async function getProjectFileList(params?: {
  projectId?: string;
  approvalStatus?: number;
  keyword?: string;
}): Promise<BaseResponse<ProjectFileVo[]>> {
  const q: Record<string, string> = {};
  if (params?.projectId) q.projectId = params.projectId;
  if (params?.approvalStatus !== undefined) q.approvalStatus = String(params.approvalStatus);
  if (params?.keyword) q.keyword = params.keyword;
  return get<ProjectFileVo[]>("/project-files/list", { params: q });
}

export async function getProjectFileDetail(
  id: string,
): Promise<BaseResponse<ProjectFileVo>> {
  return get<ProjectFileVo>(`/project-files/${id}`);
}

export async function getMyPendingProjectFiles(): Promise<BaseResponse<ProjectFileVo[]>> {
  return get<ProjectFileVo[]>("/project-files/my-pending");
}
