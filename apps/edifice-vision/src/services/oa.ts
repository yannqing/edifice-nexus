import { get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  CreateOaApplicationParams,
  ApproveOaApplicationParams,
  OaApplication,
  OaApplicationListParams,
  OaApplicationType,
  OaSsoTokenData,
  SubmitOaApplicationParams,
} from "@/types/oa";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize: number;
}

export async function getOaSsoToken(): Promise<BaseResponse<OaSsoTokenData>> {
  return get<OaSsoTokenData>("/auth/oa-sso-token");
}

export async function getOaApplicationTypes(): Promise<BaseResponse<OaApplicationType[]>> {
  return get<OaApplicationType[]>("/oa/application/types");
}

export async function getOaApplications(
  params?: OaApplicationListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<OaApplication>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.applicationType) query.applicationType = params.applicationType;
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.mine !== undefined) query.mine = String(params.mine);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<OaApplication>>("/oa/application/list", {
    params: query,
    signal,
  });
}

export async function getPendingOaApplications(
  params?: OaApplicationListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<OaApplication>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.applicationType) query.applicationType = params.applicationType;
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<OaApplication>>("/oa/application/pending", {
    params: query,
    signal,
  });
}

export async function createOaApplication(
  params: CreateOaApplicationParams
): Promise<BaseResponse<string>> {
  return post<string>("/oa/application/create", { body: params });
}

export async function submitOaApplication(
  id: string,
  params: SubmitOaApplicationParams
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/oa/application/submit/${id}`, { body: params });
}

export async function withdrawOaApplication(id: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/oa/application/withdraw/${id}`);
}

export async function approveOaApplication(
  params: ApproveOaApplicationParams
): Promise<BaseResponse<unknown>> {
  return post<unknown>("/oa/application/approve", { body: params });
}
