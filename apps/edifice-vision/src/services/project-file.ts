import { get, post } from "@/lib/request";
import { getAccessToken } from "@/lib/token";
import type { BaseResponse } from "@/types/api";
import type { PageResult } from "@/types/project";
import type {
  ApproveProjectFileParams,
  CreateProjectFileParams,
  ProjectFileVo,
} from "@/types/project-file";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

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

export async function cancelProjectFile(
  id: string,
): Promise<BaseResponse<boolean>> {
  return post<boolean>(`/project-files/${id}/cancel`);
}

export async function getProjectFileList(params?: {
  projectId?: string;
  approvalStatus?: number;
  fileCategory?: string;
  keyword?: string;
  current?: number;
  pageSize?: number;
}, signal?: AbortSignal): Promise<BaseResponse<PageResult<ProjectFileVo>>> {
  const q: Record<string, string> = {};
  if (params?.projectId) q.projectId = params.projectId;
  if (params?.approvalStatus !== undefined) q.approvalStatus = String(params.approvalStatus);
  if (params?.fileCategory) q.fileCategory = params.fileCategory;
  if (params?.keyword) q.keyword = params.keyword;
  if (params?.current !== undefined) q.current = String(params.current);
  if (params?.pageSize !== undefined) q.pageSize = String(params.pageSize);
  return get<PageResult<ProjectFileVo>>("/project-files/list", { params: q, signal });
}

export async function getProjectFileDetail(
  id: string,
): Promise<BaseResponse<ProjectFileVo>> {
  return get<ProjectFileVo>(`/project-files/${id}`);
}

export async function getMyPendingProjectFiles(): Promise<BaseResponse<ProjectFileVo[]>> {
  return get<ProjectFileVo[]>("/project-files/my-pending");
}

export async function getProjectFileStatistics(): Promise<
  BaseResponse<{ total: number; pending: number; approved: number; rejected: number }>
> {
  return get("/project-files/statistics");
}

export function getProjectFileDownloadUrl(fileId: string, token?: string | null): string {
  const query = token ? `?token=${encodeURIComponent(token)}` : "";
  return `${BASE_URL}/file/download/${fileId}${query}`;
}

export async function fetchProjectFileBlob(fileId: string): Promise<Blob> {
  const token = getAccessToken();
  const response = await fetch(getProjectFileDownloadUrl(fileId), {
    headers: token ? { token } : {},
  });

  if (!response.ok) {
    throw new Error(response.status >= 500 ? "服务器异常，请稍后重试" : "文件获取失败");
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const data = (await response.json().catch(() => null)) as BaseResponse<unknown> | null;
    throw new Error(data?.msg || "文件获取失败");
  }

  return response.blob();
}

function resolveFileName(disposition: string | null): string | null {
  if (!disposition) return null;

  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) return decodeURIComponent(encoded.replace(/^"|"$/g, ""));

  const raw = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return raw ? decodeURIComponent(raw) : null;
}

export async function fetchFileBlobWithMeta(
  fileId: string,
): Promise<{ blob: Blob; fileName: string | null; contentType: string }> {
  const token = getAccessToken();
  const response = await fetch(getProjectFileDownloadUrl(fileId), {
    headers: token ? { token } : {},
  });

  if (!response.ok) {
    throw new Error(response.status >= 500 ? "服务器异常，请稍后重试" : "文件获取失败");
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const data = (await response.json().catch(() => null)) as BaseResponse<unknown> | null;
    throw new Error(data?.msg || "文件获取失败");
  }

  return {
    blob: await response.blob(),
    fileName: resolveFileName(response.headers.get("Content-Disposition")),
    contentType,
  };
}
