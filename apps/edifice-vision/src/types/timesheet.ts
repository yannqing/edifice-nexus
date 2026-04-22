export interface TimesheetVo {
  timesheetId: string;
  userId: string;
  projectId: string;
  projectName: string;
  projectCode: string;
  projectStageId: string | null;
  stageName: string | null;
  /** 0-管理工作/1-基础工作/2-智励工作 */
  workType: number;
  workDate: string;
  hours: number;
  description: string;
  /** 0-草稿/1-已提交 */
  status: number;
  createdTime: string;
}

export interface TimesheetFormParams {
  timesheetId?: string;
  projectId: string;
  projectStageId?: string;
  workType: number;
  workDate: string;
  hours: number;
  description?: string;
  status?: number;
}

export interface GetTimesheetListParams {
  startDate?: string;
  endDate?: string;
  projectId?: string;
}

export interface WeeklyStats {
  totalHours: number;
  managementHours: number;
  basicHours: number;
  intellectualHours: number;
}

export const WORK_TYPE_MAP: Record<number, string> = {
  0: "管理工作",
  1: "基础工作",
  2: "智励工作",
};
