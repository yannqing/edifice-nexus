import { get } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  GetOperationAuditLogListParams,
  OperationAuditLogVo,
} from "@/types/audit-log";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size?: number;
  pageSize?: number;
}

export async function getOperationAuditLogList(
  params?: GetOperationAuditLogListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<OperationAuditLogVo>>> {
  const query: Record<string, string> = {};
  if (params?.operatorName) query.operatorName = params.operatorName;
  if (params?.moduleName) query.moduleName = params.moduleName;
  if (params?.operationName) query.operationName = params.operationName;
  if (params?.httpMethod) query.httpMethod = params.httpMethod;
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.startTime) query.startTime = params.startTime;
  if (params?.endTime) query.endTime = params.endTime;
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<OperationAuditLogVo>>("/audit-logs/list", {
    params: query,
    signal,
  });
}

export async function getOperationAuditLogDetail(
  id: string,
  signal?: AbortSignal
): Promise<BaseResponse<OperationAuditLogVo>> {
  return get<OperationAuditLogVo>(`/audit-logs/${id}`, { signal });
}
