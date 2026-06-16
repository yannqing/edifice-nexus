import { get, put } from "@/lib/request";
import { getAccessToken } from "@/lib/token";
import type { BaseResponse } from "@/types/api";
import type {
  ContractListVo,
  ContractChangeLogVo,
  ContractPageResult,
  GetContractListParams,
  UpdateContractParams,
} from "@/types/contract-management";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

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

export async function getContractChangeLogs(
  contractId: string,
  signal?: AbortSignal
): Promise<BaseResponse<ContractChangeLogVo[]>> {
  return get<ContractChangeLogVo[]>(`/contracts/${contractId}/change-logs`, { signal });
}

export async function exportContractExcel(params?: GetContractListParams): Promise<void> {
  const query = new URLSearchParams();
  if (params?.keywords) query.set("keywords", params.keywords);
  if (params?.contractType !== undefined) query.set("contractType", String(params.contractType));
  if (params?.projectId) query.set("projectId", params.projectId);

  const url = `${BASE_URL}/contracts/export${query.toString() ? `?${query.toString()}` : ""}`;
  await downloadFile(url, "合同数据.xlsx");
}

async function downloadFile(url: string, fallbackName: string): Promise<void> {
  const headers = new Headers();
  const token = getAccessToken();
  if (token) headers.set("token", token);
  const response = await fetch(url, { headers });
  if (!response.ok) throw new Error(`HTTP error: ${response.status}`);
  const blob = await response.blob();
  const fileName = resolveFileName(response.headers.get("Content-Disposition")) ?? fallbackName;
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

function resolveFileName(disposition: string | null): string | null {
  if (!disposition) return null;
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) return decodeURIComponent(encoded.replace(/^"|"$/g, ""));
  const raw = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return raw ? decodeURIComponent(raw) : null;
}
