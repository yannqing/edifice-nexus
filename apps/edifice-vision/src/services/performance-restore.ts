import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  PerformanceRestoreVo,
  GenerateRestoreResult,
  MarkQuarterResult,
} from "@/types/performance";

export async function getPerformanceRestoreList(params?: {
  quarter?: string;
  status?: number;
  userId?: string;
}): Promise<BaseResponse<PerformanceRestoreVo[]>> {
  const q: Record<string, string> = {};
  if (params?.quarter) q.quarter = params.quarter;
  if (params?.status !== undefined) q.status = String(params.status);
  if (params?.userId) q.userId = params.userId;
  return get<PerformanceRestoreVo[]>("/performance/restore/list", { params: q });
}

export async function generatePerformanceRestore(
  quarter: string,
): Promise<BaseResponse<GenerateRestoreResult>> {
  return post<GenerateRestoreResult>("/performance/restore/generate", {
    params: { quarter },
  });
}

export async function markPerformanceRestored(
  id: string,
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/performance/restore/mark/${id}`);
}

export async function markQuarterRestored(
  quarter: string,
): Promise<BaseResponse<MarkQuarterResult>> {
  return put<MarkQuarterResult>("/performance/restore/mark-quarter", {
    params: { quarter },
  });
}

export async function deletePerformanceRestore(
  id: string,
): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/performance/restore/${id}`);
}
