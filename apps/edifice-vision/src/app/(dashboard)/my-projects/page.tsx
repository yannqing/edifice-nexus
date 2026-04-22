"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Upload,
  Download,
  Plus,
  Search,
  Eye,
  Pencil,
  ClipboardCheck,
  Clock,
  Calendar,
  ChevronLeft,
  ChevronRight,
  FolderOpen,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { CardPageSkeleton } from "@/components/ui/skeleton";
import { getMyProjects, getMyProjectStatistics, getExportUrl } from "@/services/project";
import { ProjectDetailDialog } from "@/components/project/project-detail-dialog";
import { CreateProjectDialog } from "@/components/project/create-project-dialog";
import { EditProjectDialog } from "@/components/project/edit-project-dialog";
import { ImportProjectDialog } from "@/components/project/import-project-dialog";
import { useAuth } from "@/store/auth-context";
import { getAccessToken } from "@/lib/token";
import { ResponseCode } from "@/types/api";
import type { ProjectListVo } from "@/types/project";
import {
  PROJECT_STATUS_MAP,
  STAGE_COMPLETED_STATUSES,
} from "@/types/project";
import type { ProjectStatus, ProjectCategory } from "@/types";

/** 项目经理角色ID（对应 sys_role.role_id = 101） */
const ROLE_PROJECT_MANAGER = 101;

type FilterKey = "all" | "inProgress" | "pending" | "completed" | "notStarted";

const statusFilterMap: Record<FilterKey, number | null> = {
  all: null,
  inProgress: 1,
  pending: 2,
  completed: 4,
  notStarted: 0,
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

/** 获取项目状态中文 */
function getStatusLabel(status: number): ProjectStatus {
  return (PROJECT_STATUS_MAP[status] ?? "未开始") as ProjectStatus;
}

/** 获取项目分类标签（从 projectType.projectCode 映射） */
function getCategoryLabel(project: ProjectListVo): ProjectCategory {
  const code = project.projectType?.projectTypeCode ?? "";
  if (code.startsWith("A")) return "A类";
  if (code.startsWith("B")) return "B类";
  if (code.startsWith("C")) return "C类";
  if (code.startsWith("D")) return "D类";
  if (code.startsWith("E")) return "E类";
  return "A类";
}

/** 计算当前已完成阶段数 */
function getCompletedStageCount(project: ProjectListVo): number {
  if (!project.projectStages) return 0;
  return project.projectStages.filter((s) =>
    STAGE_COMPLETED_STATUSES.includes(s.stageStatus)
  ).length;
}

/** 获取当前阶段名称 */
function getCurrentStageName(project: ProjectListVo): string {
  return project.projectStage?.stageName ?? "-";
}

/** 获取成员名列表 */
function getMemberNames(project: ProjectListVo): string[] {
  if (!project.projectMemberList) return [];
  return project.projectMemberList
    .map((m) => m.realName)
    .filter(Boolean);
}

/** 格式化日期 */
function formatDate(dateStr: string | null): string {
  if (!dateStr) return "-";
  return dateStr.slice(0, 10);
}

const PAGE_SIZE = 9;

export default function MyProjectsPage() {
  const { user } = useAuth();
  const [activeFilter, setActiveFilter] = useState<FilterKey>("all");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editProjectId, setEditProjectId] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  /** 判断当前用户是否为该项目的项目经理 */
  const isManager = (project: ProjectListVo): boolean => {
    if (!user || !project.projectMemberList) return false;
    return project.projectMemberList.some(
      (m) => String(m.userId) === String(user.userId) && Number(m.projectRoleId) === ROLE_PROJECT_MANAGER
    );
  };
  const [createOpen, setCreateOpen] = useState(false);
  const [filterCounts, setFilterCounts] = useState({
    all: 0,
    inProgress: 0,
    pending: 0,
    completed: 0,
    notStarted: 0,
  });

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  // 搜索或筛选变化时重置页码
  useEffect(() => {
    setCurrentPage(1);
  }, [activeFilter, debouncedSearch]);

  // 加载项目数据（带请求取消，防止切换 tab 时旧数据闪现）
  // 注意：abort 时不能关闭 loading——否则 StrictMode 双调用或快速切 tab 时，
  // 被 abort 的请求会把 loading 置 false，导致骨架提前消失、闪出空态。
  const fetchProjects = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const statusValue = statusFilterMap[activeFilter];
      const response = await getMyProjects({
        keywords: debouncedSearch || undefined,
        projectStatus: statusValue ?? undefined,
        current: currentPage,
        pageSize: PAGE_SIZE,
      }, signal);

      if (response.code === ResponseCode.SUCCESS && response.data) {
        setProjects(response.data.records ?? []);
        setTotal(response.data.total ?? 0);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return; // 被取消的请求，保持 loading 让新请求接管
      setProjects([]);
      setTotal(0);
      setLoading(false);
    }
  }, [activeFilter, debouncedSearch, currentPage]);

  // 加载各状态的数量
  const fetchFilterCounts = useCallback(async () => {
    try {
      const res = await getMyProjectStatistics();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setFilterCounts({
          all: res.data.totalCount ?? 0,
          inProgress: res.data.processingCount ?? 0,
          pending: res.data.pendingAcceptanceCount ?? 0,
          completed: res.data.completedCount ?? 0,
          notStarted: res.data.notStartedCount ?? 0,
        });
      }
    } catch {
      // 静默失败
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchProjects(controller.signal);
    return () => controller.abort();
  }, [fetchProjects]);

  useEffect(() => {
    fetchFilterCounts();
  }, [fetchFilterCounts]);

  const totalPages = Math.ceil(total / PAGE_SIZE);

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
          <Button
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
            onClick={() => setCreateOpen(true)}
          >
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
                {filterCounts[item.key]}
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

      {/* Loading */}
      {loading && <CardPageSkeleton cards={6} />}

      {/* Project Cards */}
      {!loading && projects.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
          {projects.map((project) => {
            const statusLabel = getStatusLabel(project.projectStatus);
            const category = getCategoryLabel(project);
            const typeName = project.projectType?.projectTypeName ?? "";
            const stages = project.projectStages ?? [];
            const completedStages = getCompletedStageCount(project);
            const totalStages = stages.length;
            const currentStageName = getCurrentStageName(project);
            const members = getMemberNames(project);
            const contractAmt = project.contractAmount?.contractAmount ?? 0;
            const startDate = project.contractAmount?.preStartDate ?? project.preStartTime;
            const endDate = project.contractAmount?.preEndDate ?? project.preEndTime;

            return (
              <div
                key={project.projectId}
                className="glass-card rounded-2xl p-6 shadow-sm hover:shadow-md transition-all cursor-pointer group"
              >
                {/* Header */}
                <div className="flex justify-between items-start mb-4">
                  <span
                    className={cn(
                      "text-xs px-2 py-1 rounded-md border font-medium",
                      categoryStyles[category] ?? "bg-slate-50 text-slate-600 border-slate-200"
                    )}
                  >
                    {category}
                    {typeName ? ` · ${typeName}` : ""}
                  </span>
                  <Badge
                    variant="secondary"
                    className={cn("text-xs", statusStyles[statusLabel] ?? "")}
                  >
                    {statusLabel}
                  </Badge>
                </div>

                {/* Name */}
                <h3 className="text-base font-semibold text-slate-800 mb-1 group-hover:text-blue-600 transition-colors">
                  {project.projectName}
                </h3>
                <p className="text-xs text-slate-400 mb-4">
                  {project.projectCode}
                </p>

                {/* Progress */}
                {totalStages > 0 && (
                  <div className="mb-4">
                    <div className="flex justify-between text-xs text-slate-500 mb-2">
                      <span>当前阶段：{currentStageName}</span>
                      <span>
                        {completedStages}/{totalStages}
                      </span>
                    </div>
                    <div className="flex gap-1">
                      {stages.map((s, i) => (
                        <div
                          key={i}
                          className={cn(
                            "h-1.5 flex-1 rounded-full transition-all",
                            s.stageStatus === 6 || s.stageStatus === 3
                              ? "bg-emerald-500"
                              : s.stageStatus === 1
                              ? "bg-blue-500"
                              : s.stageStatus === 2
                              ? "bg-amber-500"
                              : s.stageStatus === 4
                              ? "bg-rose-500"
                              : "bg-slate-200"
                          )}
                        />
                      ))}
                    </div>
                  </div>
                )}

                {/* Amount */}
                <div className="flex justify-between items-center py-3 border-t border-slate-100">
                  <div>
                    <p className="text-xs text-slate-400">合同金额</p>
                    <p className="text-sm font-semibold text-slate-800">
                      {contractAmt > 0
                        ? `¥${(contractAmt / 10000).toFixed(1)}万`
                        : "-"}
                    </p>
                  </div>
                </div>

                {/* Date & Members */}
                <div className="flex justify-between items-center pt-3 border-t border-slate-100">
                  <div className="flex items-center gap-1 text-xs text-slate-400">
                    <Calendar className="w-3 h-3" />
                    <span>
                      {formatDate(startDate)} ~ {formatDate(endDate)}
                    </span>
                  </div>
                  <div className="flex -space-x-2">
                    {members.slice(0, 3).map((member, idx) => (
                      <div
                        key={idx}
                        className="w-6 h-6 rounded-full bg-slate-200 border-2 border-white flex items-center justify-center text-xs text-slate-600 font-medium"
                        title={member}
                      >
                        {member[0]}
                      </div>
                    ))}
                    {members.length > 3 && (
                      <div className="w-6 h-6 rounded-full bg-slate-100 border-2 border-white flex items-center justify-center text-xs text-slate-500">
                        +{members.length - 3}
                      </div>
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex gap-2 mt-4 pt-4 border-t border-slate-100">
                  <button
                    onClick={() => {
                      setSelectedProjectId(project.projectId);
                      setDetailOpen(true);
                    }}
                    className="flex-1 py-2 text-xs text-slate-600 font-medium bg-slate-50 hover:bg-slate-100 rounded-lg transition-colors flex items-center justify-center gap-1"
                  >
                    <Eye className="w-3 h-3" /> 查看详情
                  </button>
                  {isManager(project) && (
                    <button
                      onClick={() => {
                        setEditProjectId(project.projectId);
                        setEditOpen(true);
                      }}
                      className="flex-1 py-2 text-xs text-slate-600 font-medium bg-slate-50 hover:bg-slate-100 rounded-lg transition-colors flex items-center justify-center gap-1"
                    >
                      <Pencil className="w-3 h-3" /> 编辑
                    </button>
                  )}
                  {isManager(project) && project.projectStatus === 1 && (
                    <button className="flex-1 py-2 text-xs text-blue-600 font-medium bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors flex items-center justify-center gap-1">
                      <ClipboardCheck className="w-3 h-3" /> 发起验工
                    </button>
                  )}
                  {project.projectStatus === 2 && (
                    <button className="flex-1 py-2 text-xs text-amber-600 font-medium bg-amber-50 hover:bg-amber-100 rounded-lg transition-colors flex items-center justify-center gap-1">
                      <Clock className="w-3 h-3" /> 等待审批
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Empty State */}
      {!loading && projects.length === 0 && (
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
      {!loading && total > 0 && (
        <div className="flex justify-between items-center pt-4">
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
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
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
            ))}
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
          fetchFilterCounts();
        }}
      />

      {/* 编辑项目弹窗 */}
      <EditProjectDialog
        projectId={editProjectId}
        open={editOpen}
        onOpenChange={setEditOpen}
        onSuccess={() => {
          fetchProjects();
          fetchFilterCounts();
        }}
      />

      {/* 新建项目弹窗 */}
      <CreateProjectDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={() => {
          fetchProjects();
          fetchFilterCounts();
        }}
      />

      {/* 项目详情弹窗 */}
      <ProjectDetailDialog
        projectId={selectedProjectId}
        open={detailOpen}
        onOpenChange={setDetailOpen}
        onStageChange={() => {
          fetchProjects();
          fetchFilterCounts();
        }}
      />
    </div>
  );
}
