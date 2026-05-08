import { get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type { PageResult } from "@/types/project";
import type {
  InspectionFormListVo,
  InspectionFormDetailVo,
  InspectionOverviewVo,
  GetInspectionListParams,
  ApplyInspectionParams,
  ApprovalInspectionParams,
} from "@/types/inspection";

/**
 * 构建验工单查询参数
 */
function buildInspectionQuery(params?: GetInspectionListParams): Record<string, string | string[]> {
  const query: Record<string, string | string[]> = {};
  if (params?.inspectionFormCode) query.inspectionFormCode = params.inspectionFormCode;
  if (params?.projectId) query.projectId = params.projectId;
  if (params?.inspectionFormStatuses && params.inspectionFormStatuses.length > 0) {
    query.inspectionFormStatuses = params.inspectionFormStatuses.map(String);
  } else if (params?.inspectionFormStatus !== undefined) {
    query.inspectionFormStatus = String(params.inspectionFormStatus);
  }
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return query;
}

/**
 * 查询我的验工单列表（当前用户提交的）
 */
export async function getMyInspectionList(
  params?: GetInspectionListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<InspectionFormListVo>>> {
  return get<PageResult<InspectionFormListVo>>("/inspections/my-list", {
    params: buildInspectionQuery(params), signal,
  });
}

/**
 * 查询我的待审批验工单列表（当前用户为当前审批人）
 */
export async function getMyPendingInspectionList(
  params?: GetInspectionListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<InspectionFormListVo>>> {
  return get<PageResult<InspectionFormListVo>>("/inspections/my-pending", {
    params: buildInspectionQuery(params), signal,
  });
}

/**
 * 查询全部验工单列表
 */
export async function getAllInspectionList(
  params?: GetInspectionListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<InspectionFormListVo>>> {
  return get<PageResult<InspectionFormListVo>>("/inspections/all", {
    params: buildInspectionQuery(params), signal,
  });
}

/**
 * @deprecated 使用 getMyInspectionList 或 getAllInspectionList
 */
export const getInspectionList = getMyInspectionList;

/**
 * 查询验工单详情
 */
export async function getInspectionDetail(
  id: string
): Promise<BaseResponse<InspectionFormDetailVo>> {
  return get<InspectionFormDetailVo>(`/inspections/${id}`);
}

/**
 * 验工单统计总览
 */
export async function getInspectionOverview(): Promise<
  BaseResponse<InspectionOverviewVo>
> {
  return get<InspectionOverviewVo>("/inspections/my-list/statistic");
}

/**
 * 我的待审批验工单统计
 */
export async function getMyPendingInspectionOverview(): Promise<
  BaseResponse<InspectionOverviewVo>
> {
  return get<InspectionOverviewVo>("/inspections/my-pending/statistic");
}

/**
 * 提交验工单
 */
export async function applyInspection(
  params: ApplyInspectionParams
): Promise<BaseResponse<number>> {
  return post<number>("/inspections/apply", { body: params });
}

/**
 * 审批验工单
 */
export async function approvalInspection(
  params: ApprovalInspectionParams
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/inspections/approval", { body: params });
}

// ==================== 导出 Excel ====================

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

function buildExportQueryString(params?: Pick<GetInspectionListParams, "inspectionFormCode" | "inspectionFormStatus">): string {
  const query = new URLSearchParams();
  if (params?.inspectionFormCode) query.append("inspectionFormCode", params.inspectionFormCode);
  if (params?.inspectionFormStatus !== undefined) {
    query.append("inspectionFormStatus", String(params.inspectionFormStatus));
  }
  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

/**
 * 导出「我的验工单」Excel URL（用于 window.open 直接下载）
 */
export function getMyInspectionExportUrl(
  params?: Pick<GetInspectionListParams, "inspectionFormCode" | "inspectionFormStatus">
): string {
  return `${BASE_URL}/inspections/my-list/export${buildExportQueryString(params)}`;
}

/**
 * 导出「全部验工单」Excel URL（用于 window.open 直接下载）
 */
export function getAllInspectionExportUrl(
  params?: Pick<GetInspectionListParams, "inspectionFormCode" | "inspectionFormStatus">
): string {
  return `${BASE_URL}/inspections/export${buildExportQueryString(params)}`;
}
