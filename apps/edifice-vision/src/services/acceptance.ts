import { get, post } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  AcceptanceVo,
  ApproveAcceptanceParams,
  CreateAcceptanceParams,
} from "@/types/acceptance";

export async function createAcceptance(
  params: CreateAcceptanceParams,
): Promise<BaseResponse<number>> {
  return post<number>("/acceptance/create", { body: params });
}

export async function approveAcceptance(
  params: ApproveAcceptanceParams,
): Promise<BaseResponse<boolean>> {
  return post<boolean>("/acceptance/approve", { body: params });
}

export async function getAcceptanceList(params?: {
  projectId?: string;
  acceptanceType?: number;
  status?: number;
  keyword?: string;
}): Promise<BaseResponse<AcceptanceVo[]>> {
  const q: Record<string, string> = {};
  if (params?.projectId) q.projectId = params.projectId;
  if (params?.acceptanceType !== undefined) q.acceptanceType = String(params.acceptanceType);
  if (params?.status !== undefined) q.status = String(params.status);
  if (params?.keyword) q.keyword = params.keyword;
  return get<AcceptanceVo[]>("/acceptance/list", { params: q });
}

export async function getAcceptanceDetail(
  id: string,
): Promise<BaseResponse<AcceptanceVo>> {
  return get<AcceptanceVo>(`/acceptance/${id}`);
}

export async function getMyPendingAcceptance(): Promise<BaseResponse<AcceptanceVo[]>> {
  return get<AcceptanceVo[]>("/acceptance/my-pending");
}
