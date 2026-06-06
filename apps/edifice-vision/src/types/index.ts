import { LucideIcon } from "lucide-react";

// 统计卡片类型
export interface StatItem {
  label: string;
  value: string;
  change: string;
  icon: string;
  color: "blue" | "emerald" | "amber" | "rose";
}

// 项目类型
export type ProjectCategory = "A类" | "B类" | "C类" | "D类" | "E类";
export type ProjectStatus = "进行中" | "待验收" | "已完成" | "未开始";
export type CollectionStatus = "已回款" | "部分回款" | "未回款";

export interface Project {
  id: number;
  name: string;
  type: ProjectCategory;
  manager: string;
  amount: string;
  progress: number;
  collection: number;
  status: CollectionStatus;
}

// 待办事项类型
export type TodoType = "验工审批" | "产值确认";

export interface TodoItem {
  id: number;
  type: TodoType;
  title: string;
  from: string;
  time: string;
  urgent: boolean;
}

// 我的项目进度类型
export interface MyProject {
  id: number;
  name: string;
  category: ProjectCategory;
  phases: number;
  currentPhase: number;
  status: ProjectStatus;
}

// 项目分类统计类型
export interface CategoryStat {
  category: ProjectCategory;
  name: string;
  count: number;
  color: string;
}

// 个人产值类型
export interface PersonalStat {
  label: string;
  value: string;
  change?: string;
  note?: string;
  bgColor: string;
  textColor: string;
}

// 收入趋势类型
export interface IncomeTrend {
  time: string;
  amount: string;
  bar1Percent: number;
  bar2Percent: number;
}

// 导航项类型
export interface NavItem {
  id: string;
  label: string;
  icon: string;
  href: string;
  badge?: number;
  permissionCode?: string;
}

export interface NavSection {
  id: string;
  title?: string;
  items: NavItem[];
}

// 我的项目详情类型
export interface MyProjectDetail {
  id: number;
  name: string;
  code: string;
  category: ProjectCategory;
  categoryName: string;
  contractAmount: number;
  phases: number;
  currentPhase: number;
  currentPhaseName: string;
  status: ProjectStatus;
  startDate: string;
  endDate: string;
  members: string[];
  progress: number;
  collection: number;
}

// 验工单类型
export type InspectionStatus = "待初审" | "待终审" | "已通过" | "已驳回";

export interface ApprovalRecord {
  role: string;
  name: string;
  result: string;
  time: string;
  comment: string;
}

export interface Inspection {
  id: number;
  code: string;
  projectName: string;
  projectCode: string;
  category: ProjectCategory;
  phase: string;
  phaseName: string;
  phaseRatio: string;
  phaseAmount: number;
  submitter: string;
  submitTime: string;
  status: InspectionStatus;
  description: string;
  attachments: string[];
  urgent: boolean;
  approvalHistory?: ApprovalRecord[];
}

// 工时记录类型
export type WorkType = "管理工作" | "基础工作" | "智励工作";
export type TimesheetStatus = "已提交" | "草稿";

export interface TimesheetRecord {
  id: number;
  date: string;
  projectName: string;
  projectCode: string;
  phase: string;
  phaseName: string;
  workType: WorkType;
  hours: number;
  description: string;
  status: TimesheetStatus;
}

export interface WeekDay {
  date: string;
  day: string;
  dateNum: string;
}
