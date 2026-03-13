"use client";

import { useState } from "react";
import {
  Upload,
  Download,
  Plus,
  Search,
  Eye,
  ClipboardCheck,
  Clock,
  Calendar,
  ChevronLeft,
  ChevronRight,
  FolderOpen,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { myProjectsDetailData } from "@/data/mock-data";
import type { ProjectStatus, ProjectCategory } from "@/types";

type FilterKey = "all" | "inProgress" | "pending" | "completed" | "notStarted";

const statusMap: Record<FilterKey, ProjectStatus | null> = {
  all: null,
  inProgress: "进行中",
  pending: "待验收",
  completed: "已完成",
  notStarted: "未开始",
};

const statusStyles: Record<ProjectStatus, string> = {
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
  未开始: "bg-slate-100 text-slate-500",
};

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-50 text-blue-600 border-blue-200",
  B类: "bg-emerald-50 text-emerald-600 border-emerald-200",
  C类: "bg-amber-50 text-amber-600 border-amber-200",
  D类: "bg-purple-50 text-purple-600 border-purple-200",
  E类: "bg-rose-50 text-rose-600 border-rose-200",
};

export default function MyProjectsPage() {
  const [activeFilter, setActiveFilter] = useState<FilterKey>("all");
  const [searchText, setSearchText] = useState("");

  const filterStats = {
    all: myProjectsDetailData.length,
    inProgress: myProjectsDetailData.filter((p) => p.status === "进行中").length,
    pending: myProjectsDetailData.filter((p) => p.status === "待验收").length,
    completed: myProjectsDetailData.filter((p) => p.status === "已完成").length,
    notStarted: 0,
  };

  const filteredProjects = myProjectsDetailData
    .filter((p) => {
      const statusFilter = statusMap[activeFilter];
      if (statusFilter === null) return true;
      return p.status === statusFilter;
    })
    .filter((p) => {
      if (!searchText) return true;
      return (
        p.name.includes(searchText) || p.code.includes(searchText)
      );
    });

  const filters: { key: FilterKey; label: string }[] = [
    { key: "all", label: "全部" },
    { key: "inProgress", label: "进行中" },
    { key: "pending", label: "待验收" },
    { key: "completed", label: "已完成" },
    { key: "notStarted", label: "未开始" },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            我的项目
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            管理您参与的所有项目，跟踪项目进度与验工状态。
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="flex items-center gap-2">
            <Upload className="w-4 h-4" /> 导入
          </Button>
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="w-4 h-4" /> 导出
          </Button>
          <Button className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2">
            <Plus className="w-4 h-4" /> 新建项目
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {filters.map((item) => (
            <button
              key={item.key}
              onClick={() => setActiveFilter(item.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
                activeFilter === item.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
              <span
                className={cn(
                  "text-xs px-1.5 py-0.5 rounded-full",
                  activeFilter === item.key
                    ? "bg-blue-500 text-white"
                    : "bg-slate-100 text-slate-500"
                )}
              >
                {filterStats[item.key]}
              </span>
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="搜索项目名称或编号..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-64 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Project Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
        {filteredProjects.map((project) => (
          <div
            key={project.id}
            className="glass-card rounded-2xl p-6 shadow-sm hover:shadow-md transition-all cursor-pointer group"
          >
            {/* Header */}
            <div className="flex justify-between items-start mb-4">
              <span
                className={cn(
                  "text-xs px-2 py-1 rounded-md border font-medium",
                  categoryStyles[project.category]
                )}
              >
                {project.category} · {project.categoryName}
              </span>
              <Badge
                variant="secondary"
                className={cn("text-xs", statusStyles[project.status])}
              >
                {project.status}
              </Badge>
            </div>

            {/* Name */}
            <h3 className="text-base font-semibold text-slate-800 mb-1 group-hover:text-blue-600 transition-colors">
              {project.name}
            </h3>
            <p className="text-xs text-slate-400 mb-4">{project.code}</p>

            {/* Progress */}
            <div className="mb-4">
              <div className="flex justify-between text-xs text-slate-500 mb-2">
                <span>当前阶段：{project.currentPhaseName}</span>
                <span>
                  {project.currentPhase}/{project.phases}
                </span>
              </div>
              <div className="flex gap-1">
                {[...Array(project.phases)].map((_, i) => (
                  <div
                    key={i}
                    className={cn(
                      "h-1.5 flex-1 rounded-full transition-all",
                      i < project.currentPhase ? "bg-blue-500" : "bg-slate-200"
                    )}
                  />
                ))}
              </div>
            </div>

            {/* Amount & Collection */}
            <div className="flex justify-between items-center py-3 border-t border-slate-100">
              <div>
                <p className="text-xs text-slate-400">合同金额</p>
                <p className="text-sm font-semibold text-slate-800">
                  ¥{(project.contractAmount / 10000).toFixed(1)}万
                </p>
              </div>
              <div className="text-right">
                <p className="text-xs text-slate-400">回款率</p>
                <p
                  className={cn(
                    "text-sm font-semibold",
                    project.collection >= 80
                      ? "text-emerald-600"
                      : project.collection >= 50
                      ? "text-amber-600"
                      : "text-rose-600"
                  )}
                >
                  {project.collection}%
                </p>
              </div>
            </div>

            {/* Date & Members */}
            <div className="flex justify-between items-center pt-3 border-t border-slate-100">
              <div className="flex items-center gap-1 text-xs text-slate-400">
                <Calendar className="w-3 h-3" />
                <span>
                  {project.startDate} ~ {project.endDate}
                </span>
              </div>
              <div className="flex -space-x-2">
                {project.members.slice(0, 3).map((member, idx) => (
                  <div
                    key={idx}
                    className="w-6 h-6 rounded-full bg-slate-200 border-2 border-white flex items-center justify-center text-xs text-slate-600 font-medium"
                    title={member}
                  >
                    {member[0]}
                  </div>
                ))}
                {project.members.length > 3 && (
                  <div className="w-6 h-6 rounded-full bg-slate-100 border-2 border-white flex items-center justify-center text-xs text-slate-500">
                    +{project.members.length - 3}
                  </div>
                )}
              </div>
            </div>

            {/* Actions */}
            <div className="flex gap-2 mt-4 pt-4 border-t border-slate-100">
              <button className="flex-1 py-2 text-xs text-slate-600 font-medium bg-slate-50 hover:bg-slate-100 rounded-lg transition-colors flex items-center justify-center gap-1">
                <Eye className="w-3 h-3" /> 查看详情
              </button>
              {project.status === "进行中" && (
                <button className="flex-1 py-2 text-xs text-blue-600 font-medium bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors flex items-center justify-center gap-1">
                  <ClipboardCheck className="w-3 h-3" /> 发起验工
                </button>
              )}
              {project.status === "待验收" && (
                <button className="flex-1 py-2 text-xs text-amber-600 font-medium bg-amber-50 hover:bg-amber-100 rounded-lg transition-colors flex items-center justify-center gap-1">
                  <Clock className="w-3 h-3" /> 等待审批
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Empty State */}
      {filteredProjects.length === 0 && (
        <div className="glass-card rounded-2xl p-12 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <FolderOpen className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无项目</h3>
          <p className="text-sm text-slate-500 mb-4">
            当前筛选条件下没有找到项目
          </p>
          <button
            onClick={() => {
              setActiveFilter("all");
              setSearchText("");
            }}
            className="px-4 py-2 text-sm text-blue-600 font-medium hover:bg-blue-50 rounded-lg transition-colors"
          >
            清除筛选条件
          </button>
        </div>
      )}

      {/* Pagination */}
      {filteredProjects.length > 0 && (
        <div className="flex justify-between items-center pt-4">
          <p className="text-sm text-slate-500">
            共{" "}
            <span className="font-semibold text-slate-800">
              {filteredProjects.length}
            </span>{" "}
            个项目
          </p>
          <div className="flex items-center gap-2">
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button className="px-3 py-1.5 text-sm font-medium bg-blue-600 text-white rounded-lg">
              1
            </button>
            <button className="px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg">
              2
            </button>
            <button className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
