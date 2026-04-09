"use client";

import { useState } from "react";
import {
  Download,
  Plus,
  Search,
  Eye,
  RefreshCw,
  Undo2,
  MoreHorizontal,
  FileText,
  Clock,
  CheckCircle,
  XCircle,
  Banknote,
  ChevronLeft,
  ChevronRight,
  X,
  Send,
  UploadCloud,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { inspectionManagementData, allProjectsData } from "@/data/mock-data";
import type { ProjectCategory, InspectionStatus } from "@/types";

type TabKey = "all" | "pending" | "passed" | "rejected";

const statusStyles: Record<InspectionStatus, string> = {
  待初审: "bg-amber-100 text-amber-600",
  待终审: "bg-blue-100 text-blue-600",
  已通过: "bg-emerald-100 text-emerald-600",
  已驳回: "bg-rose-100 text-rose-600",
};

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "text-blue-600",
  B类: "text-emerald-600",
  C类: "text-amber-600",
  D类: "text-purple-600",
  E类: "text-rose-600",
};

export default function InspectionManagementPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string>("all");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedProject, setSelectedProject] = useState("");

  const stats = {
    total: inspectionManagementData.length,
    pending: inspectionManagementData.filter(
      (i) => i.status === "待初审" || i.status === "待终审"
    ).length,
    passed: inspectionManagementData.filter((i) => i.status === "已通过").length,
    rejected: inspectionManagementData.filter((i) => i.status === "已驳回").length,
    totalAmount: inspectionManagementData
      .filter((i) => i.status === "已通过")
      .reduce((sum, i) => sum + i.phaseAmount, 0),
  };

  const filteredInspections = inspectionManagementData.filter((i) => {
    if (activeTab === "pending" && i.status !== "待初审" && i.status !== "待终审") return false;
    if (activeTab === "passed" && i.status !== "已通过") return false;
    if (activeTab === "rejected" && i.status !== "已驳回") return false;
    if (selectedCategory !== "all" && i.category !== selectedCategory) return false;
    if (searchText && !i.projectName.includes(searchText) && !i.code.includes(searchText))
      return false;
    return true;
  });

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: stats.total },
    { key: "pending", label: "审批中", count: stats.pending },
    { key: "passed", label: "已通过", count: stats.passed },
    { key: "rejected", label: "已驳回", count: stats.rejected },
  ];

  const resetFilters = () => {
    setActiveTab("all");
    setSelectedCategory("all");
    setSearchText("");
  };

  const projectOptions = allProjectsData.filter((p) => p.status === "进行中").slice(0, 3);

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">验工单管理</h1>
          <p className="text-slate-500 text-sm mt-1">管理所有验工单，发起新的阶段验收申请。</p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="w-4 h-4" /> 导出
          </Button>
          <Button
            onClick={() => setShowCreateModal(true)}
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
          >
            <Plus className="w-4 h-4" /> 发起验工
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-5 gap-4">
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-slate-100 text-slate-600 rounded-lg">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">验工单总数</p>
              <p className="text-xl font-bold text-slate-800">{stats.total}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-amber-100 text-amber-600 rounded-lg">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">审批中</p>
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
              <p className="text-xs text-slate-500">已通过</p>
              <p className="text-xl font-bold text-slate-800">{stats.passed}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-rose-100 text-rose-600 rounded-lg">
              <XCircle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">已驳回</p>
              <p className="text-xl font-bold text-slate-800">{stats.rejected}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
              <Banknote className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">已确认产值</p>
              <p className="text-xl font-bold text-slate-800">
                ¥{(stats.totalAmount / 10000).toFixed(1)}万
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {tabs.map((item) => (
            <button
              key={item.key}
              onClick={() => setActiveTab(item.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
                activeTab === item.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
              <span
                className={cn(
                  "text-xs px-1.5 py-0.5 rounded-full",
                  activeTab === item.key ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-500"
                )}
              >
                {item.count}
              </span>
            </button>
          ))}
        </div>

        <select
          value={selectedCategory}
          onChange={(e) => setSelectedCategory(e.target.value)}
          className="px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="all">全部分类</option>
          <option value="A类">A类 · 全程结算</option>
          <option value="B类">B类 · 全过程</option>
          <option value="C类">C类 · 单项</option>
          <option value="D类">D类 · 技术咨询</option>
          <option value="E类">E类 · 零星</option>
        </select>

        <div className="flex-1" />

        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="搜索验工单号或项目名称..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Inspection Table */}
      <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead className="bg-slate-50/50">
            <tr className="text-slate-500 text-xs uppercase tracking-wider">
              <th className="text-left py-4 px-6 font-semibold">验工单信息</th>
              <th className="text-left py-4 px-4 font-semibold">项目阶段</th>
              <th className="text-left py-4 px-4 font-semibold">阶段产值</th>
              <th className="text-left py-4 px-4 font-semibold">发起人</th>
              <th className="text-left py-4 px-4 font-semibold">发起时间</th>
              <th className="text-left py-4 px-4 font-semibold">当前审批人</th>
              <th className="text-center py-4 px-4 font-semibold">状态</th>
              <th className="text-right py-4 px-6 font-semibold">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filteredInspections.map((item) => (
              <tr key={item.id} className="hover:bg-slate-50/50 transition-colors">
                <td className="py-4 px-6">
                  <p className="text-sm font-semibold text-slate-800">{item.projectName}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{item.code}</p>
                </td>
                <td className="py-4 px-4">
                  <span className="text-sm text-slate-600">
                    {item.phase} · {item.phaseName}
                  </span>
                  <p className="text-xs text-slate-400 mt-0.5">
                    <span className={categoryStyles[item.category]}>{item.category}</span>
                    <span className="mx-1">·</span>
                    产值比例 {item.phaseRatio}
                  </p>
                </td>
                <td className="py-4 px-4">
                  <span className="text-sm font-semibold text-slate-800">
                    ¥{(item.phaseAmount / 10000).toFixed(2)}万
                  </span>
                </td>
                <td className="py-4 px-4">
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                      {item.submitter[0]}
                    </div>
                    <span className="text-sm text-slate-600">{item.submitter}</span>
                  </div>
                </td>
                <td className="py-4 px-4">
                  <span className="text-sm text-slate-500">{item.submitTime}</span>
                </td>
                <td className="py-4 px-4">
                  {item.currentApprover !== "-" ? (
                    <span className="text-sm text-slate-600">{item.currentApprover}</span>
                  ) : (
                    <span className="text-sm text-slate-400">-</span>
                  )}
                </td>
                <td className="py-4 px-4 text-center">
                  <Badge variant="secondary" className={cn("text-xs", statusStyles[item.status])}>
                    {item.status}
                  </Badge>
                </td>
                <td className="py-4 px-6 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <button className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                      <Eye className="w-4 h-4" />
                    </button>
                    {item.status === "已驳回" && (
                      <button className="p-1.5 text-slate-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-colors">
                        <RefreshCw className="w-4 h-4" />
                      </button>
                    )}
                    {(item.status === "待初审" || item.status === "待终审") && (
                      <button className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors">
                        <Undo2 className="w-4 h-4" />
                      </button>
                    )}
                    <button className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
                      <MoreHorizontal className="w-4 h-4" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {filteredInspections.length === 0 && (
          <div className="py-16 text-center">
            <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <FileText className="w-8 h-8 text-slate-400" />
            </div>
            <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无验工单</h3>
            <p className="text-sm text-slate-500 mb-4">当前筛选条件下没有找到验工单</p>
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
      {filteredInspections.length > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{filteredInspections.length}</span>{" "}
            条记录
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
            <button className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-8">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl overflow-hidden">
            <div className="p-6 border-b border-slate-100 flex justify-between items-center">
              <h2 className="text-xl font-bold text-slate-900">发起验工申请</h2>
              <button
                onClick={() => setShowCreateModal(false)}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-5">
              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  选择项目 <span className="text-rose-500">*</span>
                </label>
                <select
                  value={selectedProject}
                  onChange={(e) => setSelectedProject(e.target.value)}
                  className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white"
                >
                  <option value="">请选择项目</option>
                  {projectOptions.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.code}) - {p.category}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  验工阶段 <span className="text-rose-500">*</span>
                </label>
                <select
                  className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white"
                  disabled={!selectedProject}
                >
                  <option value="">请先选择项目</option>
                  <option value="1">阶段4 · 核对 (产值比例 20%)</option>
                </select>
                <p className="text-xs text-slate-400 mt-1">只能选择当前可验工的阶段（按阶段顺序）</p>
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  验工说明 <span className="text-rose-500">*</span>
                </label>
                <textarea
                  rows={4}
                  placeholder="请描述本阶段完成的主要工作内容..."
                  className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                />
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  验收材料 <span className="text-rose-500">*</span>
                </label>
                <div className="border-2 border-dashed border-slate-200 rounded-xl p-8 text-center hover:border-blue-400 transition-colors cursor-pointer">
                  <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-3">
                    <UploadCloud className="w-6 h-6 text-slate-400" />
                  </div>
                  <p className="text-sm text-slate-600 mb-1">点击或拖拽文件到此处上传</p>
                  <p className="text-xs text-slate-400">支持 PDF、Word、Excel 等格式，单个文件不超过 20MB</p>
                </div>
                <p className="text-xs text-slate-400 mt-2">请上传：台账、计算底稿、成果文件等验收材料</p>
              </div>
            </div>

            <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
              <button
                onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
              >
                取消
              </button>
              <button className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors">
                保存草稿
              </button>
              <button className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-xl transition-colors flex items-center gap-2">
                <Send className="w-4 h-4" /> 提交验工
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
