import { get, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  GetProjectArchiveListParams,
  ProjectArchivePageResult,
} from "@/types/project-archive";

function toQuery(params?: GetProjectArchiveListParams): Record<string, string> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.projectType !== undefined) query.projectType = String(params.projectType);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return query;
}

export async function getArchivableProjects(
  params?: GetProjectArchiveListParams,
  signal?: AbortSignal
): Promise<BaseResponse<ProjectArchivePageResult>> {
  return get<ProjectArchivePageResult>("/project-archive/ready", {
    params: toQuery(params),
    signal,
  });
}

export async function getArchivedProjects(
  params?: GetProjectArchiveListParams,
  signal?: AbortSignal
): Promise<BaseResponse<ProjectArchivePageResult>> {
  return get<ProjectArchivePageResult>("/project-archive/archived", {
    params: toQuery(params),
    signal,
  });
}

export async function archiveProject(projectId: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/project-archive/archive/${projectId}`, { body: {} });
}

export async function archiveProjectWithRemark(
  projectId: string,
  archiveRemark?: string
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/project-archive/archive/${projectId}`, {
    body: { archiveRemark: archiveRemark?.trim() || undefined },
  });
}

export async function unarchiveProject(projectId: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/project-archive/unarchive/${projectId}`);
}
