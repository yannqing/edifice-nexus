"use client";

import { Calendar, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  StatsGrid,
  ProjectsTable,
  TodoList,
  ProjectProgress,
  CategoryDistribution,
  PersonalStats,
  IncomeTrendCard,
} from "@/components/dashboard";
import {
  statsData,
  projectsData,
  todosData,
  myProjectsData,
  categoryStatsData,
  personalStatsData,
  incomeTrendData,
} from "@/data/mock-data";

export default function DashboardPage() {
  const totalProjectCount = categoryStatsData.reduce(
    (acc, cat) => acc + cat.count,
    0
  );

  return (
    <div className="p-8 space-y-8">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            全局数据仪表盘
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            欢迎回来，这是截至目前的产值分配与核算概览信息。
          </p>
        </div>
        <div className="flex gap-3">
          <Button
            variant="outline"
            className="px-4 py-2 rounded-xl text-sm font-medium flex items-center gap-2"
          >
            <Calendar className="w-4 h-4" /> 2024年第一季度
          </Button>
          <Button className="px-4 py-2 bg-blue-600 text-white rounded-xl text-sm font-semibold hover:bg-blue-700 shadow-lg shadow-blue-200 flex items-center gap-2">
            <Plus className="w-4 h-4" /> 创建新项目
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <StatsGrid stats={statsData} />

      {/* Projects Table + Todo List */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <ProjectsTable projects={projectsData} />
        <TodoList todos={todosData} />
      </div>

      {/* Project Progress + Category Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <ProjectProgress projects={myProjectsData} />
        <CategoryDistribution
          categories={categoryStatsData}
          totalCount={totalProjectCount}
        />
      </div>

      {/* Personal Stats + Income Trend */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <PersonalStats stats={personalStatsData} />
        <IncomeTrendCard trends={incomeTrendData} />
      </div>
    </div>
  );
}
