"use client";

import { useState } from "react";
import {
  Upload,
  Download,
  Plus,
  Search,
  Eye,
  Pencil,
  MoreHorizontal,
  Layers,
  PlayCircle,
  Clock,
  CheckCircle,
  Banknote,
  ChevronLeft,
  ChevronRight,
  Briefcase,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { allProjectsData } from "@/data/mock-data";
import type { ProjectStatus, ProjectCategory } from "@/types";

type StatusFilter = "all" | ProjectStatus;
type CategoryFilter = "all" | ProjectCategory;

const statusStyles: Record<ProjectStatus, string> = {
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
  未开始: "bg-slate-100 text-slate-500",
};

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-50 text-blue-600",
  B类: "bg-emerald-50 text-emerald-600",
  C类: "bg-amber-50 text-amber-600",
  D类: "bg-purple-50 text-purple-600",
  E类: "bg-rose-50 text-rose-600",
};

export default function AllProjectsPage() {
  const [selectedCategory, setSelectedCategory] = useState<CategoryFilter>("all");
  const [selectedStatus, setSelectedStatus] = useState<StatusFilter>("all");
  const [selectedManager, setSelectedManager] = useState<string>("all");
  const [searchText, setSearchText] = useState("");
  const [selectedRows, setSelectedRows] = useState<number[]>([]);

  const managers = [...new Set(allProjectsData.map((p) => p.manager))];

  const stats = {
    total: allProjectsData.length,
    inProgress: allProjectsData.filter((p) => p.status === "进行中").length,
    pending: allProjectsData.filter((p) => p.status === "待验收").length,
    completed: allProjectsData.filter((p) => p.status === "已完成").length,
    totalAmount: allProjectsData.reduce((sum, p) => sum + p.contractAmount, 0),
  };

  const filteredProjects = allProjectsData.filter((p) => {
    if (selectedCategory !== "all" && p.category !== selectedCategory) return false;
    if (selectedStatus !== "all" && p.status !== selectedStatus) return false;
    if (selectedManager !== "all" && p.manager !== selectedManager) return false;
    if (searchText && !p.name.includes(searchText) && !p.code.includes(searchText)) return false;
    return true;
  });

  const toggleRow = (id: number) => {
    setSelectedRows((prev) =>
      prev.includes(id) ? prev.filter((r) => r !== id) : [...prev, id]
    );
  };

  const toggleAll = () => {
    if (selectedRows.length === filteredProjects.length) {
      setSelectedRows([]);
    } else {
      setSelectedRows(filteredProjects.map((p) => p.id));
    }
  };

  const resetFilters = () => {
    setSelectedCategory("all");
    setSelectedStatus("all");
    setSelectedManager("all");
    setSearchText("");
  };

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            全部项目
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            管理系统内所有项目，查看项目状态与进度。
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

      {/* Stats Cards */}
      <div className="grid grid-cols-5 gap-4">
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-slate-100 text-slate-600 rounded-lg">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">项目总数</p>
              <p className="text-xl font-bold text-slate-800">{stats.total}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
              <PlayCircle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">进行中</p>
              <p className="text-xl font-bold text-slate-800">{stats.inProgress}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-amber-100 text-amber-600 rounded-lg">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">待验收</p>
              <p className="text-xl font-bold text-slate-800">{stats.pending}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-emerald-100 text-emerald-600 rounded-lg">
              <CheckCircle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">已完成</p>
              <p className="text-xl font-bold text-slate-800">{stats.completed}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-purple-100 text-purple-600 rounded-lg">
              <Banknote className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">合同总额</p>
              <p className="text-xl font-bold text-slate-800">
                ¥{(stats.totalAmount / 10000).toFixed(0)}万
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="glass-card rounded-2xl p-4 shadow-sm">
        <div className="flex items-center gap-4">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="搜索项目名称或编号..."
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>

          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value as CategoryFilter)}
            className="px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">全部分类</option>
            <option value="A类">A类 · 全程结算</option>
            <option value="B类">B类 · 全过程</option>
            <option value="C类">C类 · 单项</option>
            <option value="D类">D类 · 技术咨询</option>
            <option value="E类">E类 · 零星</option>
          </select>

          <select
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value as StatusFilter)}
            className="px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">全部状态</option>
            <option value="未开始">未开始</option>
            <option value="进行中">进行中</option>
            <option value="待验收">待验收</option>
            <option value="已完成">已完成</option>
          </select>

          <select
            value={selectedManager}
            onChange={(e) => setSelectedManager(e.target.value)}
            className="px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">全部负责人</option>
            {managers.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>

          <button
            onClick={resetFilters}
            className="px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium"
          >
            重置
          </button>
        </div>

        {selectedRows.length > 0 && (
          <div className="mt-4 pt-4 border-t border-slate-100 flex items-center gap-4">
            <span className="text-sm text-slate-500">
              已选择 <span className="font-semibold text-slate-800">{selectedRows.length}</span> 个项目
            </span>
            <button className="px-3 py-1.5 text-sm text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors">
              批量导出
            </button>
            <button className="px-3 py-1.5 text-sm text-amber-600 bg-amber-50 hover:bg-amber-100 rounded-lg transition-colors">
              批量暂停
            </button>
            <button
              onClick={() => setSelectedRows([])}
              className="text-sm text-slate-400 hover:text-slate-600"
            >
              取消选择
            </button>
          </div>
        )}
      </div>

      {/* Projects Table */}
      <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead className="bg-slate-50/50">
            <tr className="text-slate-500 text-xs uppercase tracking-wider">
              <th className="py-4 px-4 text-left">
                <input
                  type="checkbox"
                  checked={selectedRows.length === filteredProjects.length && filteredProjects.length > 0}
                  onChange={toggleAll}
                  className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                />
              </th>
              <th className="py-4 px-4 text-left font-semibold">项目信息</th>
              <th className="py-4 px-4 text-left font-semibold">分类</th>
              <th className="py-4 px-4 text-left font-semibold">合同金额</th>
              <th className="py-4 px-4 text-left font-semibold">项目经理</th>
              <th className="py-4 px-4 text-left font-semibold">阶段进度</th>
              <th className="py-4 px-4 text-center font-semibold">回款率</th>
              <th className="py-4 px-4 text-center font-semibold">状态</th>
              <th className="py-4 px-6 text-right font-semibold">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filteredProjects.map((project) => (
              <tr key={project.id} className="hover:bg-slate-50/50 transition-colors">
                <td className="py-4 px-4">
                  <input
                    type="checkbox"
                    checked={selectedRows.includes(project.id)}
                    onChange={() => toggleRow(project.id)}
                    className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                </td>
                <td className="py-4 px-4">
                  <p className="text-sm font-semibold text-slate-800">{project.name}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{project.code}</p>
                </td>
                <td className="py-4 px-4">
                  <span className={cn("text-xs px-2 py-1 rounded-md font-medium", categoryStyles[project.category])}>
                    {project.category}
                  </span>
                  <p className="text-xs text-slate-400 mt-1">{project.categoryName}</p>
                </td>
                <td className="py-4 px-4">
                  <p className="text-sm font-semibold text-slate-800">
                    ¥{(project.contractAmount / 10000).toFixed(1)}万
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">{project.contractType}</p>
                </td>
                <td className="py-4 px-4">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                      {project.manager[0]}
                    </div>
                    <span className="text-sm text-slate-600">{project.manager}</span>
                  </div>
                </td>
                <td className="py-4 px-4">
                  <div className="flex items-center gap-2 mb-1">
                    <div className="flex-1 flex gap-0.5 max-w-24">
                      {[...Array(project.phases)].map((_, i) => (
                        <div
                          key={i}
                          className={cn(
                            "h-1.5 flex-1 rounded-full",
                            i < project.currentPhase ? "bg-blue-500" : "bg-slate-200"
                          )}
                        />
                      ))}
                    </div>
                    <span className="text-xs text-slate-500">
                      {project.currentPhase}/{project.phases}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400">{project.currentPhaseName}</p>
                </td>
                <td className="py-4 px-4 text-center">
                  <span
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
                  </span>
                </td>
                <td className="py-4 px-4 text-center">
                  <Badge variant="secondary" className={cn("text-xs", statusStyles[project.status])}>
                    {project.status}
                  </Badge>
                </td>
                <td className="py-4 px-6 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <button className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                      <Eye className="w-4 h-4" />
                    </button>
                    <button className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
                      <Pencil className="w-4 h-4" />
                    </button>
                    <button className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
                      <MoreHorizontal className="w-4 h-4" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {filteredProjects.length === 0 && (
          <div className="py-16 text-center">
            <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Briefcase className="w-8 h-8 text-slate-400" />
            </div>
            <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无项目</h3>
            <p className="text-sm text-slate-500 mb-4">当前筛选条件下没有找到项目</p>
            <button
              onClick={resetFilters}
              className="px-4 py-2 text-sm text-blue-600 font-medium hover:bg-blue-50 rounded-lg transition-colors"
            >
              清除筛选条件
            </button>
          </div>
        )}
      </div>

      {/* Pagination */}
      {filteredProjects.length > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{filteredProjects.length}</span> 个项目
          </p>
          <div className="flex items-center gap-2">
            <select className="px-3 py-1.5 text-sm border border-slate-200 rounded-lg bg-white">
              <option>10 条/页</option>
              <option>20 条/页</option>
              <option>50 条/页</option>
            </select>
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button className="px-3 py-1.5 text-sm font-medium bg-blue-600 text-white rounded-lg">
              1
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
