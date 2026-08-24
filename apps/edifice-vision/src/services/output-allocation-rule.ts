import { get, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  OutputAllocationRule,
  SaveOutputAllocationRuleParams,
} from "@/types/output-allocation-rule";

export async function getOutputAllocationRule(
  projectTypeId: string,
  signal?: AbortSignal,
): Promise<BaseResponse<OutputAllocationRule>> {
  return get<OutputAllocationRule>(`/output-allocation-rule/${projectTypeId}`, { signal });
}

export async function saveOutputAllocationRule(
  projectTypeId: string,
  params: SaveOutputAllocationRuleParams,
): Promise<BaseResponse<OutputAllocationRule>> {
  return put<OutputAllocationRule>(`/output-allocation-rule/${projectTypeId}`, { body: params });
}
