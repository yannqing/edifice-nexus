import { get, post, del } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  TimesheetVo,
  TimesheetFormParams,
  GetTimesheetListParams,
  WeeklyStats,
} from "@/types/timesheet";

/**
 * 查询我的工时记录
 */
export async function getMyTimesheets(
  params?: GetTimesheetListParams
): Promise<BaseResponse<TimesheetVo[]>> {
  const query: Record<string, string> = {};
  if (params?.startDate) query.startDate = params.startDate;
  if (params?.endDate) query.endDate = params.endDate;
  if (params?.projectId) query.projectId = params.projectId;
  return get<TimesheetVo[]>("/timesheet/my-list", { params: query });
}

/**
 * 周工时统计
 */
export async function getWeeklyStats(
  startDate: string,
  endDate: string
): Promise<BaseResponse<WeeklyStats>> {
  return get<WeeklyStats>("/timesheet/weekly-stats", {
    params: { startDate, endDate },
  });
}

/**
 * 新增/更新工时记录
 */
export async function saveTimesheet(
  params: TimesheetFormParams
): Promise<BaseResponse<number>> {
  return post<number>("/timesheet/save", { body: params });
}

/**
 * 删除工时记录
 */
export async function deleteTimesheet(
  timesheetId: string
): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/timesheet/delete/${timesheetId}`);
}
