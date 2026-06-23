import { get, post, put } from "@/lib/request";
import { getAccessToken } from "@/lib/token";
import type { BaseResponse } from "@/types/api";
import type {
  OutputValueVo,
  CreateOutputValueParams,
  OutputValuePreview,
  OutputValueStats,
} from "@/types/output-value";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

export async function getOutputValueList(
  status?: number
): Promise<BaseResponse<OutputValueVo[]>> {
  const params: Record<string, string> = {};
  if (status !== undefined) params.status = String(status);
  return get<OutputValueVo[]>("/output-value/list", { params });
}

export async function getOutputValueStats(): Promise<BaseResponse<OutputValueStats>> {
  return get<OutputValueStats>("/output-value/statistics");
}

export async function getOutputValuePreview(
  projectId: string,
  projectStageId: string,
): Promise<BaseResponse<OutputValuePreview>> {
  return get<OutputValuePreview>("/output-value/preview", {
    params: { projectId, projectStageId },
  });
}

export async function createOutputValue(
  params: CreateOutputValueParams
): Promise<BaseResponse<number>> {
  return post<number>("/output-value/create", { body: params });
}

export async function confirmOutputValue(
  id: string,
  nextUserId: string,
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/confirm/${id}`, { body: { nextUserId } });
}

export async function approveOutputValue(
  id: string,
  nextUserId: string,
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/approve/${id}`, { body: { nextUserId } });
}

export async function payOutputValue(id: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/pay/${id}`);
}

/** 终审产值分配单：当前处理人（确认人/审批人）直接终审，跳过后续环节，状态置为已发放 */
export async function terminateOutputValue(id: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/terminate/${id}`);
}

export async function exportOutputValueExcel(status?: number, keyword?: string): Promise<void> {
  const params = new URLSearchParams();
  if (status !== undefined) params.set("status", String(status));
  if (keyword?.trim()) params.set("keyword", keyword.trim());

  const url = `${BASE_URL}/output-value/export${params.toString() ? `?${params.toString()}` : ""}`;
  const headers = new Headers();
  const token = getAccessToken();
  if (token) headers.set("token", token);

  const response = await fetch(url, { headers });
  if (!response.ok) {
    throw new Error(`HTTP error: ${response.status}`);
  }

  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition");
  const matched = disposition?.match(/filename=([^;]+)/i);
  const fileName = matched?.[1]
    ? decodeURIComponent(matched[1].replace(/^"|"$/g, ""))
    : "产值分配数据.xlsx";

  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}
