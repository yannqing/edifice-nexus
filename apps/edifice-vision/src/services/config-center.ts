import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  ApprovalFlowConfigVo,
  BusinessRuleConfigVo,
  ConfigOptionBundleVo,
  GetConfigListParams,
  SaveApprovalFlowConfigParams,
  SaveBusinessRuleConfigParams,
} from "@/types/config-center";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize?: number;
  size?: number;
}

function toQuery(params?: GetConfigListParams) {
  const query: Record<string, string> = {};
  if (params?.bizType) query.bizType = params.bizType;
  if (params?.keyword) query.keyword = params.keyword;
  if (params?.enabled !== undefined) query.enabled = String(params.enabled);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return query;
}

export function getFlowConfigList(
  params?: GetConfigListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<ApprovalFlowConfigVo>>> {
  return get<PageResult<ApprovalFlowConfigVo>>("/flow-config/list", { params: toQuery(params), signal });
}

export function getEnabledFlowConfig(
  bizType: string,
  signal?: AbortSignal
): Promise<BaseResponse<ApprovalFlowConfigVo | null>> {
  return get<ApprovalFlowConfigVo | null>(`/flow-config/enabled/${bizType}`, { signal });
}

export function saveFlowConfig(params: SaveApprovalFlowConfigParams): Promise<BaseResponse<string>> {
  return post<string>("/flow-config/save", { body: params });
}

export function toggleFlowConfig(id: string, enabled: number): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/flow-config/toggle/${id}`, { body: { enabled } });
}

export function deleteFlowConfig(id: string): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/flow-config/${id}`);
}

export function getBusinessRuleConfigList(
  params?: GetConfigListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<BusinessRuleConfigVo>>> {
  return get<PageResult<BusinessRuleConfigVo>>("/business-rule-config/list", { params: toQuery(params), signal });
}

export function saveBusinessRuleConfig(params: SaveBusinessRuleConfigParams): Promise<BaseResponse<string>> {
  return post<string>("/business-rule-config/save", { body: params });
}

export function toggleBusinessRuleConfig(id: string, enabled: number): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/business-rule-config/toggle/${id}`, { body: { enabled } });
}

export function deleteBusinessRuleConfig(id: string): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/business-rule-config/${id}`);
}

export function getConfigOptions(signal?: AbortSignal): Promise<BaseResponse<ConfigOptionBundleVo>> {
  return get<ConfigOptionBundleVo>("/config-options", { signal });
}
