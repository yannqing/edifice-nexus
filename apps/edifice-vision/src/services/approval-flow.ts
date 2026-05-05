import { get } from "@/lib/request";
import type { BaseResponse } from "@/types/api";

/**
 * 我的待审批计数（按业务类型分桶）。
 * key 为 ext：file / inspection / bid / acceptance / output / timesheet
 */
export async function getMyPendingCounts(
  signal?: AbortSignal,
): Promise<BaseResponse<Record<string, number>>> {
  return get<Record<string, number>>("/approval-flow/my-pending-counts", { signal });
}
