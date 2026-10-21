import { del, get, post, put } from "@/lib/request";
import { getAccessToken } from "@/lib/token";
import { notifySidebarCountsUpdated } from "@/lib/sidebar-counts";
import { ResponseCode, type BaseResponse } from "@/types/api";
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

export function createProjectFilesBatch(params: CreateProjectFileParams[]) {
  return post<string[]>("/project-files/create-batch", { body: params });
}

export function getProjectFileDraftPolicy() {
  return get<{ retentionDays: number; maxBatchFiles: number }>("/project-files/draft-policy");
}

export function saveProjectFileDrafts(params: CreateProjectFileParams[]) {
  return post<string[]>("/project-files/drafts", { body: params });
}

export function getProjectFileDrafts(params: { projectId: string; current?: number; pageSize?: number; keyword?: string }, signal?: AbortSignal) {
  return get<PageResult<ProjectFileVo>>("/project-files/drafts", {
    params: Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined).map(([key, value]) => [key, String(value)])), signal,
  });
}

export function removeProjectFileDraft(id: string) {
  return del<boolean>(`/project-files/drafts/${id}`);
}

export function updateProjectFileDraft(id: string, params: CreateProjectFileParams) {
  return put<boolean>(`/project-files/drafts/${id}`, { body: params });
}

export async function submitProjectFileDrafts(params: { projectId: string; draftIds: string[]; title?: string; description?: string; firstApproverId?: string }) {
  const result = await post<string>("/project-files/submit-drafts", { body: params });
  if (result.code === ResponseCode.SUCCESS) notifySidebarCountsUpdated();
  return result;
}

export function copyProjectFileToDrafts(id: string) {
  return post<string[]>(`/project-files/${id}/copy-to-drafts`);
}

export async function approveProjectFile(
  params: ApproveProjectFileParams,
): Promise<BaseResponse<boolean>> {
  const result = await post<boolean>("/project-files/approve", { body: params });
  if (result.code === ResponseCode.SUCCESS) notifySidebarCountsUpdated();
  return result;
}

export async function cancelProjectFile(
  id: string,
): Promise<BaseResponse<boolean>> {
  const result = await post<boolean>(`/project-files/${id}/cancel`);
  if (result.code === ResponseCode.SUCCESS) notifySidebarCountsUpdated();
  return result;
}

export async function getProjectFileList(params?: {
  projectId?: string;
  approvalStatus?: number;
  fileCategory?: string;
  keyword?: string;
  mineOnly?: boolean;
  applicationsOnly?: boolean;
  current?: number;
  pageSize?: number;
}, signal?: AbortSignal): Promise<BaseResponse<PageResult<ProjectFileVo>>> {
  const q: Record<string, string> = {};
  if (params?.projectId) q.projectId = params.projectId;
  if (params?.approvalStatus !== undefined) q.approvalStatus = String(params.approvalStatus);
  if (params?.fileCategory) q.fileCategory = params.fileCategory;
  if (params?.keyword) q.keyword = params.keyword;
  if (params?.mineOnly) q.mineOnly = "true";
  if (params?.applicationsOnly) q.applicationsOnly = "true";
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

/** Use the browser's streaming download/preview path instead of allocating a multi-GB Blob. */
export async function prepareNativeFileUrl(fileId: string, preview = false): Promise<string> {
  const base = getProjectFileDownloadUrl(fileId);
  const url = preview ? `${base}?mode=preview` : base;
  const token = getAccessToken();
  const response = await fetch(url, { method: "HEAD", cache: "no-store", headers: token ? { token } : {} });
  if (!response.ok || response.headers.get("content-type")?.includes("application/json")) throw new Error("文件不存在或没有访问权限");
  if (preview && !response.headers.get("content-disposition")?.startsWith("inline")) throw new Error("暂不支持预览该格式，请下载后查看");
  return url;
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
