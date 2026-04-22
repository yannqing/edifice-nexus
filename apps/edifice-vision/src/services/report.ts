import { get } from "@/lib/request";
import type { BaseResponse } from "@/types/api";

// ==================== 仪表盘 ====================

export interface DashboardData {
  stats: {
    projectCount: number;
    totalOutputValue: number;
    paidOutputValue: number;
    pendingInspections: number;
  };
  topProjects: {
    projectId: string;
    projectName: string;
    type: string;
    contractAmount: number;
    projectStatus: number;
    completedValue: number;
    progress: number;
  }[];
  todos: {
    id: string;
    type: string;
    title: string;
    from: string;
    time: string;
  }[];
  myProjects: {
    projectId: string;
    projectName: string;
    projectStatus: number;
    category: string;
    phases: number;
    currentPhase: number;
  }[];
  categoryDistribution: {
    category: string;
    name: string;
    count: number;
  }[];
}

export async function getDashboard(): Promise<BaseResponse<DashboardData>> {
  return get<DashboardData>("/report/dashboard");
}

// ==================== 统计报表 ====================

export async function getReportOverview(): Promise<BaseResponse<{
  totalProjects: number;
  totalContractAmount: number;
  completedOutputValue: number;
  totalOutputValue: number;
}>> {
  return get("/report/overview");
}

export async function getProjectStats(): Promise<BaseResponse<{
  projectId: string;
  projectName: string;
  category: string;
  contractAmount: number;
  completedAmount: number;
  outputValue: number;
  pendingValue: number;
}[]>> {
  return get("/report/project-stats");
}

export async function getCategoryStats(): Promise<BaseResponse<{
  category: string;
  name: string;
  count: number;
  contractTotal: number;
  completedTotal: number;
  percentage: number;
}[]>> {
  return get("/report/category-stats");
}

export async function getPersonnelRanking(): Promise<BaseResponse<{
  rank: number;
  userId: string;
  name: string;
  outputValue: number;
}[]>> {
  return get("/report/personnel-ranking");
}

// ==================== 个人绩效 ====================

export async function getMyPerformance(): Promise<BaseResponse<{
  projectCount: number;
  totalHours: number;
  managementHours: number;
  basicHours: number;
  intellectualHours: number;
  paidOutputValue: number;
  totalOutputValue: number;
}>> {
  return get("/report/my-performance");
}

export async function getMyProjectDetails(): Promise<BaseResponse<{
  projectId: string;
  projectName: string;
  projectCode: string;
  projectStatus: number;
  category: string;
  role: string;
  totalHours: number;
  outputValue: number;
}[]>> {
  return get("/report/my-project-details");
}

export async function getMyPayments(): Promise<BaseResponse<{
  distributionId: string;
  projectName: string;
  stageName: string;
  amount: number;
  status: number;
  paidTime: string | null;
}[]>> {
  return get("/report/my-payments");
}
