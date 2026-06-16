import { get, put } from "@/lib/request";
import { getAccessToken } from "@/lib/token";
import type { BaseResponse } from "@/types/api";
import type {
  GetProjectArchiveListParams,
  ProjectArchiveDetailVo,
  ProjectArchivePageResult,
} from "@/types/project-archive";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

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

export async function getProjectArchiveDetail(
  projectId: string,
  signal?: AbortSignal
): Promise<BaseResponse<ProjectArchiveDetailVo>> {
  return get<ProjectArchiveDetailVo>(`/project-archive/detail/${projectId}`, { signal });
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

export async function downloadProjectArchivePackage(projectId: string): Promise<void> {
  await downloadFile(`${BASE_URL}/project-archive/package/${projectId}`, "项目归档资料.zip");
}

async function downloadFile(url: string, fallbackName: string): Promise<void> {
  const headers = new Headers();
  const token = getAccessToken();
  if (token) headers.set("token", token);
  const response = await fetch(url, { headers });
  if (!response.ok) throw new Error(`HTTP error: ${response.status}`);
  const blob = await response.blob();
  const fileName = resolveFileName(response.headers.get("Content-Disposition")) ?? fallbackName;
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

function resolveFileName(disposition: string | null): string | null {
  if (!disposition) return null;
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) return decodeURIComponent(encoded.replace(/^"|"$/g, ""));
  const raw = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return raw ? decodeURIComponent(raw) : null;
}
