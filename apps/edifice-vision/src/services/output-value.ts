import { get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  OutputValueVo,
  CreateOutputValueParams,
  OutputValueStats,
} from "@/types/output-value";

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

export async function createOutputValue(
  params: CreateOutputValueParams
): Promise<BaseResponse<number>> {
  return post<number>("/output-value/create", { body: params });
}

export async function confirmOutputValue(id: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/confirm/${id}`);
}

export async function approveOutputValue(id: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/approve/${id}`);
}

export async function payOutputValue(id: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/output-value/pay/${id}`);
}
