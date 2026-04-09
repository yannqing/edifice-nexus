"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Upload,
  Download,
  Plus,
  Search,
  Eye,
  Pencil,
  Layers,
  PlayCircle,
  Clock,
  CheckCircle,
  Banknote,
  ChevronLeft,
  ChevronRight,
  Briefcase,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { getAllProjects, getProjectStatistics, getProjectTypes, getExportUrl } from "@/services/project";
import { ProjectDetailDialog } from "@/components/project/project-detail-dialog";
import { ImportProjectDialog } from "@/components/project/import-project-dialog";
import { EditProjectDialog } from "@/components/project/edit-project-dialog";
import { deleteProject } from "@/services/project";
import { getAccessToken } from "@/lib/token";
import { ResponseCode } from "@/types/api";
import type { ProjectListVo, ProjectStatisticsVo } from "@/types/project";
import {
  PROJECT_STATUS_MAP,
  STAGE_COMPLETED_STATUSES,
} from "@/types/project";
import type { ProjectStatus, ProjectCategory } from "@/types";

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

/** 后端状态数字 → 前端中文 */
function getStatusLabel(status: number): ProjectStatus {
  return (PROJECT_STATUS_MAP[status] ?? "未开始") as ProjectStatus;
}

/** 项目类型编码 → 分类标签 */
function getCategoryLabel(project: ProjectListVo): ProjectCategory {
  const code = project.projectType?.projectTypeCode ?? "";
  if (code.startsWith("A")) return "A类";
  if (code.startsWith("B")) return "B类";
  if (code.startsWith("C")) return "C类";
  if (code.startsWith("D")) return "D类";
  if (code.startsWith("E")) return "E类";
  return "A类";
}

/** 已完成阶段数 */
function getCompletedStageCount(project: ProjectListVo): number {
  if (!project.projectStages) return 0;
  return project.projectStages.filter((s) =>
    STAGE_COMPLETED_STATUSES.includes(s.stageStatus)
  ).length;
}

/** 获取成员名列表 */
function getMemberNames(project: ProjectListVo): string[] {
  if (!project.projectMemberList) return [];
  return project.projectMemberList.map((m) => m.realName).filter(Boolean);
}

/** 状态数字 → 筛选值 */
const STATUS_FILTER_OPTIONS: { value: string; label: string; status?: number }[] = [
  { value: "all", label: "全部状态" },
  { value: "0", label: "未开始", status: 0 },
  { value: "1", label: "进行中", status: 1 },
  { value: "2", label: "待验收", status: 2 },
  { value: "4", label: "已完成", status: 4 },
];

interface ProjectTypeOption {
  projectTypeId: number;
  projectTypeName: string;
  projectTypeCode: string;
}

const PAGE_SIZE = 10;

export default function AllProjectsPage() {
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [selectedStatus, setSelectedStatus] = useState("all");
  const [selectedType, setSelectedType] = useState("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [selectedRows, setSelectedRows] = useState<string[]>([]);
  const [statistics, setStatistics] = useState<ProjectStatisticsVo | null>(null);
  const [projectTypes, setProjectTypes] = useState<ProjectTypeOption[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editProjectId, setEditProjectId] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  // 筛选变化时重置页码
  useEffect(() => {
    setCurrentPage(1);
  }, [selectedStatus, selectedType, debouncedSearch]);

  // 加载项目列表
  const fetchProjects = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getAllProjects({
        keywords: debouncedSearch || undefined,
        projectStatus:
          selectedStatus !== "all" ? Number(selectedStatus) : undefined,
        projectType:
          selectedType !== "all" ? Number(selectedType) : undefined,
        current: currentPage,
        pageSize: PAGE_SIZE,
      });

      if (response.code === ResponseCode.SUCCESS && response.data) {
        setProjects(response.data.records ?? []);
        setTotal(response.data.total ?? 0);
      }
    } catch {
      setProjects([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [debouncedSearch, selectedStatus, selectedType, currentPage]);

  // 加载统计数据
  const fetchStatistics = useCallback(async () => {
    try {
      const res = await getProjectStatistics();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setStatistics(res.data);
      }
    } catch {
      // 静默
    }
  }, []);

  // 加载项目类型（用于下拉筛选）
  const fetchProjectTypes = useCallback(async () => {
    try {
      const res = await getProjectTypes();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setProjectTypes(res.data);
      }
    } catch {
      // 静默
    }
  }, []);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  useEffect(() => {
    fetchStatistics();
    fetchProjectTypes();
  }, [fetchStatistics, fetchProjectTypes]);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  const toggleRow = (id: string) => {
    setSelectedRows((prev) =>
      prev.includes(id) ? prev.filter((r) => r !== id) : [...prev, id]
    );
  };

  const toggleAll = () => {
    if (selectedRows.length === projects.length && projects.length > 0) {
      setSelectedRows([]);
    } else {
      setSelectedRows(projects.map((p) => p.projectId));
    }
  };

  const resetFilters = () => {
    setSelectedStatus("all");
    setSelectedType("all");
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
          <Button
            variant="outline"
            className="flex items-center gap-2"
            onClick={() => setImportOpen(true)}
          >
            <Upload className="w-4 h-4" /> 导入
          </Button>
          <Button
            variant="outline"
            className="flex items-center gap-2"
            onClick={() => {
              const url = getExportUrl();
              const token = getAccessToken();
              const separator = url.includes("?") ? "&" : "?";
              window.open(token ? `${url}${separator}token=${token}` : url, "_blank");
            }}
          >
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
              <p className="text-xl font-bold text-slate-800">
                {statistics?.totalCount ?? 0}
              </p>
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
              <p className="text-xl font-bold text-slate-800">
                {statistics?.processingCount ?? 0}
              </p>
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
              <p className="text-xl font-bold text-slate-800">
                {statistics?.pendingAcceptanceCount ?? 0}
              </p>
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
              <p className="text-xl font-bold text-slate-800">
                {statistics?.completedCount ?? 0}
              </p>
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
                {statistics?.totalContractAmount
                  ? `¥${(statistics.totalContractAmount / 10000).toFixed(0)}万`
                  : "¥0"}
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
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value)}
            className="px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">全部分类</option>
            {projectTypes.map((t) => (
              <option key={t.projectTypeId} value={t.projectTypeId}>
                {t.projectTypeCode}类 · {t.projectTypeName}
              </option>
            ))}
          </select>

          <select
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value)}
            className="px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {STATUS_FILTER_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
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
              已选择{" "}
              <span className="font-semibold text-slate-800">
                {selectedRows.length}
              </span>{" "}
              个项目
            </span>
            <button
              className="px-3 py-1.5 text-sm text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
              onClick={() => {
                const url = getExportUrl(selectedRows);
                const token = getAccessToken();
                window.open(token ? `${url}&token=${token}` : url, "_blank");
              }}
            >
              批量导出
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

      {/* Loading */}
      {loading && (
        <div className="flex justify-center py-16">
          <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
        </div>
      )}

      {/* Projects Table */}
      {!loading && projects.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-slate-50/50">
              <tr className="text-slate-500 text-xs uppercase tracking-wider">
                <th className="py-4 px-4 text-left">
                  <input
                    type="checkbox"
                    checked={
                      selectedRows.length === projects.length &&
                      projects.length > 0
                    }
                    onChange={toggleAll}
                    className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                </th>
                <th className="py-4 px-4 text-left font-semibold">项目信息</th>
                <th className="py-4 px-4 text-left font-semibold">分类</th>
                <th className="py-4 px-4 text-left font-semibold">合同金额</th>
                <th className="py-4 px-4 text-left font-semibold">项目成员</th>
                <th className="py-4 px-4 text-left font-semibold">阶段进度</th>
                <th className="py-4 px-4 text-center font-semibold">状态</th>
                <th className="py-4 px-6 text-right font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {projects.map((project) => {
                const statusLabel = getStatusLabel(project.projectStatus);
                const category = getCategoryLabel(project);
                const typeName = project.projectType?.projectTypeName ?? "";
                const stages = project.projectStages ?? [];
                const completedStages = getCompletedStageCount(project);
                const totalStages = stages.length;
                const currentStageName =
                  project.projectStage?.stageName ?? "-";
                const members = getMemberNames(project);
                const contractAmt =
                  project.contractAmount?.contractAmount ?? 0;
                const contractTypeLabel =
                  project.contractAmount?.contractType === 0
                    ? "基本收费"
                    : project.contractAmount?.contractType === 1
                    ? "基本+效益"
                    : "";

                return (
                  <tr
                    key={project.projectId}
                    className="hover:bg-slate-50/50 transition-colors"
                  >
                    <td className="py-4 px-4">
                      <input
                        type="checkbox"
                        checked={selectedRows.includes(project.projectId)}
                        onChange={() => toggleRow(project.projectId)}
                        className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                      />
                    </td>
                    <td className="py-4 px-4">
                      <p className="text-sm font-semibold text-slate-800">
                        {project.projectName}
                      </p>
                      <p className="text-xs text-slate-400 mt-0.5">
                        {project.projectCode}
                      </p>
                    </td>
                    <td className="py-4 px-4">
                      <span
                        className={cn(
                          "text-xs px-2 py-1 rounded-md font-medium",
                          categoryStyles[category]
                        )}
                      >
                        {category}
                      </span>
                      {typeName && (
                        <p className="text-xs text-slate-400 mt-1">
                          {typeName}
                        </p>
                      )}
                    </td>
                    <td className="py-4 px-4">
                      <p className="text-sm font-semibold text-slate-800">
                        {contractAmt > 0
                          ? `¥${(contractAmt / 10000).toFixed(1)}万`
                          : "-"}
                      </p>
                      {contractTypeLabel && (
                        <p className="text-xs text-slate-400 mt-0.5">
                          {contractTypeLabel}
                        </p>
                      )}
                    </td>
                    <td className="py-4 px-4">
                      <div className="flex -space-x-1.5">
                        {members.slice(0, 3).map((name, idx) => (
                          <div
                            key={idx}
                            className="w-7 h-7 rounded-full bg-slate-200 border-2 border-white flex items-center justify-center text-xs font-medium text-slate-600"
                            title={name}
                          >
                            {name[0]}
                          </div>
                        ))}
                        {members.length > 3 && (
                          <div className="w-7 h-7 rounded-full bg-slate-100 border-2 border-white flex items-center justify-center text-xs text-slate-500">
                            +{members.length - 3}
                          </div>
                        )}
                        {members.length === 0 && (
                          <span className="text-xs text-slate-400">-</span>
                        )}
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      {totalStages > 0 ? (
                        <>
                          <div className="flex items-center gap-2 mb-1">
                            <div className="flex-1 flex gap-0.5 max-w-24">
                              {stages.map((_, i) => (
                                <div
                                  key={i}
                                  className={cn(
                                    "h-1.5 flex-1 rounded-full",
                                    i < completedStages
                                      ? "bg-blue-500"
                                      : "bg-slate-200"
                                  )}
                                />
                              ))}
                            </div>
                            <span className="text-xs text-slate-500">
                              {completedStages}/{totalStages}
                            </span>
                          </div>
                          <p className="text-xs text-slate-400">
                            {currentStageName}
                          </p>
                        </>
                      ) : (
                        <span className="text-xs text-slate-400">-</span>
                      )}
                    </td>
                    <td className="py-4 px-4 text-center">
                      <Badge
                        variant="secondary"
                        className={cn("text-xs", statusStyles[statusLabel])}
                      >
                        {statusLabel}
                      </Badge>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => {
                            setSelectedProjectId(project.projectId);
                            setDetailOpen(true);
                          }}
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => {
                            setEditProjectId(project.projectId);
                            setEditOpen(true);
                          }}
                          className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Empty State */}
      {!loading && projects.length === 0 && (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Briefcase className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">
            暂无项目
          </h3>
          <p className="text-sm text-slate-500 mb-4">
            当前筛选条件下没有找到项目
          </p>
          <button
            onClick={resetFilters}
            className="px-4 py-2 text-sm text-blue-600 font-medium hover:bg-blue-50 rounded-lg transition-colors"
          >
            清除筛选条件
          </button>
        </div>
      )}

      {/* Pagination */}
      {!loading && total > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共{" "}
            <span className="font-semibold text-slate-800">{total}</span>{" "}
            个项目
          </p>
          <div className="flex items-center gap-2">
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => p - 1)}
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map(
              (page) => (
                <button
                  key={page}
                  onClick={() => setCurrentPage(page)}
                  className={cn(
                    "px-3 py-1.5 text-sm font-medium rounded-lg",
                    page === currentPage
                      ? "bg-blue-600 text-white"
                      : "text-slate-600 hover:bg-slate-100"
                  )}
                >
                  {page}
                </button>
              )
            )}
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled={currentPage >= totalPages}
              onClick={() => setCurrentPage((p) => p + 1)}
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* 导入项目弹窗 */}
      <ImportProjectDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => {
          fetchProjects();
          fetchStatistics();
        }}
      />

      {/* 编辑项目弹窗 */}
      <EditProjectDialog
        projectId={editProjectId}
        open={editOpen}
        onOpenChange={setEditOpen}
        onSuccess={() => {
          fetchProjects();
          fetchStatistics();
        }}
      />

      {/* 项目详情弹窗 */}
      <ProjectDetailDialog
        projectId={selectedProjectId}
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />
    </div>
  );
}
