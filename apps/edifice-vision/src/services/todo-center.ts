import { get } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  GetTodoCenterListParams,
  TodoCenterDetail,
  TodoCenterItem,
  TodoCenterStats,
  TodoCenterTab,
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
  tab: Exclude<TodoCenterTab, "cc">,
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
