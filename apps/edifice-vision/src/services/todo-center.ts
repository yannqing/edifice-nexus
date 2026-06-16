import { get, post } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  CreateApprovalCcParams,
  GetTodoCenterListParams,
  TodoCenterDetail,
  TodoCenterItem,
  TodoCenterStats,
  TodoCenterTab,
  UrgeApprovalParams,
  WithdrawApprovalParams,
} from "@/types/todo-center";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size?: number;
  pageSize?: number;
}

function toQuery(params?: GetTodoCenterListParams) {
  const query: Record<string, string> = {};
  if (params?.bizType) query.bizType = params.bizType;
  if (params?.keyword) query.keyword = params.keyword;
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return query;
}

export function getTodoCenterList(
  tab: TodoCenterTab,
  params?: GetTodoCenterListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<TodoCenterItem>>> {
  return get<PageResult<TodoCenterItem>>(`/todo-center/${tab}`, {
    params: toQuery(params),
    signal,
  });
}

export function getTodoCenterStats(signal?: AbortSignal): Promise<BaseResponse<TodoCenterStats>> {
  return get<TodoCenterStats>("/todo-center/statistics", { signal });
}

export function getTodoCenterDetail(
  recordId: string,
  signal?: AbortSignal
): Promise<BaseResponse<TodoCenterDetail>> {
  return get<TodoCenterDetail>(`/todo-center/${recordId}`, { signal });
}

export function createApprovalCc(params: CreateApprovalCcParams): Promise<BaseResponse<boolean>> {
  return post<boolean>("/todo-center/cc", { body: params });
}

export function urgeApproval(params: UrgeApprovalParams): Promise<BaseResponse<boolean>> {
  return post<boolean>("/todo-center/urge", { body: params });
}

export function withdrawApproval(params: WithdrawApprovalParams): Promise<BaseResponse<boolean>> {
  return post<boolean>("/todo-center/withdraw", { body: params });
}
