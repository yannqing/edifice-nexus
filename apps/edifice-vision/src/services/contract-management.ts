import { get, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  ContractListVo,
  ContractPageResult,
  GetContractListParams,
  UpdateContractParams,
} from "@/types/contract-management";

export async function getContractList(
  params?: GetContractListParams,
  signal?: AbortSignal
): Promise<BaseResponse<ContractPageResult>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.contractType !== undefined) query.contractType = String(params.contractType);
  if (params?.projectId) query.projectId = params.projectId;
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return get<ContractPageResult>("/contracts/list", { params: query, signal });
}

export async function getContractDetail(
  contractId: string
): Promise<BaseResponse<ContractListVo>> {
  return get<ContractListVo>(`/contracts/${contractId}`);
}

export async function updateContractInfo(
  params: UpdateContractParams
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/contracts/update", { body: params });
}
