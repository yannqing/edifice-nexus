"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { ComponentType } from "react";
import { useRouter } from "next/navigation";
import {
  Archive,
  Briefcase,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  Coins,
  FileText,
  FolderOpen,
  GitBranch,
  Loader2,
  Search,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import {
  getLifecycleProjects,
  getProjectLifecycle,
} from "@/services/project-lifecycle";
import { ResponseCode } from "@/types/api";
import type { ProjectListVo, ProjectStageVo } from "@/types/project";
import { PROJECT_STATUS_MAP, STAGE_COMPLETED_STATUSES } from "@/types/project";
import type { ProjectLifecycleVo, LifecycleEventVo, LifecycleStageVo } from "@/types/project-lifecycle";
import { INSPECTION_STATUS_MAP } from "@/types/inspection";
import { PROJECT_FILE_STATUS_MAP } from "@/types/project-file";

const PAGE_SIZE = 8;

const stageStatusMap: Record<number, string> = {
  0: "未开始",
  1: "进行中",
  2: "待验收",
  3: "已验收",
  4: "已驳回",
  5: "待分配",
  6: "已完成",
};

const eventIconMap: Record<string, ComponentType<{ className?: string }>> = {
  project: Briefcase,
  contract: FileText,
  stage: GitBranch,
  inspection: CheckCircle2,
  output: Coins,
  file: FileText,
  archive: Archive,
};

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 2,
  }).format(value);
}

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

function percentText(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return `${Number(value).toFixed(2).replace(/\.00$/, "")}%`;
}

function stageStatusClass(status?: number | null) {
  if (status === 6 || status === 3) return "bg-emerald-100 text-emerald-700";
  if (status === 1 || status === 2 || status === 5) return "bg-blue-100 text-blue-700";
  if (status === 4) return "bg-rose-100 text-rose-700";
  return "bg-slate-100 text-slate-600";
}

function eventAccentClass(type: string) {
  if (type === "archive") return "bg-emerald-100 text-emerald-700";
  if (type === "output") return "bg-amber-100 text-amber-700";
  if (type === "inspection") return "bg-blue-100 text-blue-700";
  if (type === "file") return "bg-violet-100 text-violet-700";
  return "bg-slate-100 text-slate-600";
}

function completedStagePercent(stages?: ProjectStageVo[] | null) {
  if (!stages?.length) return 0;
  // 按完成比例加权计算：已完成的算100%，部分完成的按比例，未开始的算0%
  const totalRatio = stages.reduce((sum, stage) => {
    if (STAGE_COMPLETED_STATUSES.includes(stage.stageStatus)) return sum + 100;
    const cr = stage.completionRatio ?? 0;
    return sum + cr;
  }, 0);
  return Math.round(totalRatio / stages.length);
}

export default function ProjectLifecyclePage() {
  const router = useRouter();
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [listLoading, setListLoading] = useState(true);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ProjectLifecycleVo | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const projectId = params.get("projectId");
    if (projectId) setSelectedProjectId(projectId);
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const fetchProjects = useCallback(async (signal?: AbortSignal) => {
    setListLoading(true);
    try {
      const res = await getLifecycleProjects({
        keywords: debouncedKeyword || undefined,
        current: currentPage,
        pageSize: PAGE_SIZE,
      }, signal);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        const records = res.data.records ?? [];
        setProjects(records);
        setTotal(res.data.total ?? 0);
        setSelectedProjectId((current) => current ?? records[0]?.projectId ?? null);
      }
      setListLoading(false);
    } catch (err) {
      if (isAbortError(err)) return;
      setProjects([]);
      setTotal(0);
      setListLoading(false);
    }
  }, [currentPage, debouncedKeyword]);

  useEffect(() => {
    const controller = new AbortController();
    fetchProjects(controller.signal);
    return () => controller.abort();
  }, [fetchProjects]);

  useEffect(() => {
    if (!selectedProjectId) {
      setDetail(null);
      return;
    }
    const controller = new AbortController();
    setDetailLoading(true);
    getProjectLifecycle(selectedProjectId, controller.signal)
      .then((res) => {
        if (res.code === ResponseCode.SUCCESS && res.data) setDetail(res.data);
      })
      .catch((err) => {
        if (!isAbortError(err)) setDetail(null);
      })
      .finally(() => setDetailLoading(false));
    return () => controller.abort();
  }, [selectedProjectId]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const completedPercent = useMemo(
    () => completedStagePercent(detail?.project.projectStages),
    [detail?.project.projectStages]
  );

  const selectProject = (projectId: string) => {
    setSelectedProjectId(projectId);
    const params = new URLSearchParams(window.location.search);
    params.set("projectId", projectId);
    window.history.replaceState(null, "", `/project-lifecycle?${params.toString()}`);
  };

  const openEvent = (event: LifecycleEventVo) => {
    if (event.link) router.push(event.link);
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">项目生命周期</h1>
          <p className="text-sm text-slate-500 mt-1">按项目串联立项、合同、阶段、验工、产值、文件和归档动态</p>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[360px_1fr] gap-6 items-start">
        <div className="glass-card rounded-2xl p-4 space-y-4">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索项目名称 / 编号..."
              className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>

          {listLoading && <TablePageSkeleton columns={2} rows={5} />}

          {!listLoading && projects.length === 0 && (
            <div className="text-center py-12 text-sm text-slate-500">
              <FolderOpen className="w-10 h-10 mx-auto mb-3 text-slate-300" />
              暂无项目
            </div>
          )}

          {!listLoading && projects.length > 0 && (
            <div className="space-y-2">
              {projects.map((project) => {
                const active = project.projectId === selectedProjectId;
                const progress = completedStagePercent(project.projectStages);
                return (
                  <button
                    key={project.projectId}
                    onClick={() => selectProject(project.projectId)}
                    className={cn(
                      "w-full text-left rounded-xl border p-4 transition-colors",
                      active
                        ? "border-blue-300 bg-blue-50"
                        : "border-slate-100 bg-white hover:border-slate-200 hover:bg-slate-50"
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="font-semibold text-slate-900 truncate">{project.projectName}</div>
                        <div className="text-xs text-slate-400 mt-1">{project.projectCode || "-"}</div>
                      </div>
                      <Badge variant="secondary" className="shrink-0">
                        {PROJECT_STATUS_MAP[project.projectStatus] ?? "未知"}
                      </Badge>
                    </div>
                    <div className="mt-3 flex items-center gap-2">
                      <Progress value={progress} className="h-1.5 bg-slate-100" />
                      <span className="text-xs text-slate-500 w-10 text-right">{progress}%</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          <div className="flex items-center justify-between text-sm text-slate-500">
            <span>共 {total} 个项目</span>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" disabled={currentPage <= 1} onClick={() => setCurrentPage((p) => p - 1)}>
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <span>{currentPage} / {totalPages}</span>
              <Button variant="outline" size="sm" disabled={currentPage >= totalPages} onClick={() => setCurrentPage((p) => p + 1)}>
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </div>

        <div className="space-y-6">
          {detailLoading && (
            <div className="glass-card rounded-2xl p-6">
              <div className="flex items-center gap-2 text-slate-500 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" /> 正在加载生命周期数据...
              </div>
            </div>
          )}

          {!detailLoading && !detail && (
            <div className="glass-card rounded-2xl p-12 text-center text-sm text-slate-500">
              请选择一个项目查看生命周期
            </div>
          )}

          {!detailLoading && detail && (
            <>
              <section className="glass-card rounded-2xl p-6 space-y-5">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-xl font-bold text-slate-900">{detail.project.projectName}</h2>
                      <Badge className={cn("hover:bg-inherit", detail.archive.archiveStatus === 1 ? "bg-emerald-100 text-emerald-700" : "bg-blue-100 text-blue-700")}>
                        {detail.archive.archiveStatus === 1 ? "已归档" : PROJECT_STATUS_MAP[detail.project.projectStatus] ?? "未知"}
                      </Badge>
                    </div>
                    <p className="text-sm text-slate-500 mt-1">
                      {detail.project.projectCode || "-"} · {detail.project.projectType?.projectTypeName ?? "未设置类型"}
                    </p>
                  </div>
                  <div className="text-sm text-slate-500">
                    预计周期：{formatTime(detail.project.preStartTime).slice(0, 10)} 至 {formatTime(detail.project.preEndTime).slice(0, 10)}
                  </div>
                </div>

                <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
                  <SummaryItem label="合同金额" value={formatMoney(detail.summary.contractAmount)} />
                  <SummaryItem label="已发放产值" value={formatMoney(detail.summary.paidOutputAmount)} />
                  <SummaryItem label="阶段完成" value={`${detail.summary.completedStageCount}/${detail.summary.stageCount}`} />
                </div>

                <div>
                  <div className="flex items-center justify-between text-sm mb-2">
                    <span className="font-medium text-slate-700">整体阶段进度</span>
                    <span className="text-slate-500">{completedPercent}%</span>
                  </div>
                  <Progress value={completedPercent} className="h-2 bg-slate-100" />
                </div>
              </section>

              <section className="glass-card rounded-2xl p-6">
                <h3 className="text-base font-semibold text-slate-900 mb-4">阶段节点</h3>
                {detail.stages.length === 0 ? (
                  <div className="text-sm text-slate-500 py-8 text-center">暂无阶段</div>
                ) : (
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                    {detail.stages.map((stage) => (
                      <StageNode key={stage.projectStageId} stage={stage} />
                    ))}
                  </div>
                )}
              </section>

              <div className="grid grid-cols-1 2xl:grid-cols-[1fr_360px] gap-6">
                <section className="glass-card rounded-2xl p-6">
                  <h3 className="text-base font-semibold text-slate-900 mb-4">生命周期动态</h3>
                  {detail.events.length === 0 ? (
                    <div className="text-sm text-slate-500 py-8 text-center">暂无动态</div>
                  ) : (
                    <div className="space-y-3">
                      {detail.events.map((event) => {
                        const Icon = eventIconMap[event.eventType] ?? CircleDot;
                        return (
                          <button
                            key={event.eventId}
                            onClick={() => openEvent(event)}
                            className="w-full text-left flex gap-3 rounded-xl border border-slate-100 bg-white p-4 hover:bg-slate-50 transition-colors"
                          >
                            <div className={cn("w-9 h-9 rounded-lg flex items-center justify-center shrink-0", eventAccentClass(event.eventType))}>
                              <Icon className="w-4 h-4" />
                            </div>
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-center gap-2">
                                <span className="font-medium text-slate-900">{event.title}</span>
                                <Badge variant="secondary">{event.eventTypeLabel}</Badge>
                              </div>
                              <p className="text-sm text-slate-500 mt-1">{event.content || "点击查看详情"}</p>
                              <div className="text-xs text-slate-400 mt-2">
                                {formatTime(event.occurredTime)}
                                {event.operatorName ? ` · ${event.operatorName}` : ""}
                              </div>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </section>

                <section className="glass-card rounded-2xl p-6">
                  <h3 className="text-base font-semibold text-slate-900 mb-4">最近文件</h3>
                  {detail.recentFiles.length === 0 ? (
                    <div className="text-sm text-slate-500 py-8 text-center">暂无文件</div>
                  ) : (
                    <div className="space-y-3">
                      {detail.recentFiles.map((file) => (
                        <div key={file.projectFileId} className="rounded-xl border border-slate-100 bg-white p-3">
                          <div className="font-medium text-sm text-slate-900 line-clamp-1">{file.fileName || "未命名文件"}</div>
                          <div className="text-xs text-slate-400 mt-1">{file.stageName || "未关联阶段"} · {PROJECT_FILE_STATUS_MAP[file.approvalStatus] ?? "-"}</div>
                          {file.fileId && (
                            <div className="mt-3">
                              <AttachmentFileActions
                                fileId={file.fileId}
                                fileName={file.fileName}
                              />
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-4">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="text-lg font-bold text-slate-900 mt-1">{value}</div>
    </div>
  );
}

function StageNode({ stage }: { stage: LifecycleStageVo }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="font-semibold text-slate-900 truncate">{stage.stageName}</div>
          <div className="text-xs text-slate-400 mt-1">最近动态：{formatTime(stage.latestActivityTime)}</div>
        </div>
        <Badge className={cn("shrink-0 hover:bg-inherit", stageStatusClass(stage.stageStatus))}>
          {stageStatusMap[stage.stageStatus] ?? "未知"}
        </Badge>
      </div>
      <div className="grid grid-cols-2 gap-3 text-sm mt-4">
        <MiniMetric label="基本比例" value={percentText(stage.stageOutput)} />
        <MiniMetric label="效益比例" value={percentText(stage.benefitInclusionRatio ?? stage.stageOutput)} />
        <MiniMetric label="完成进度" value={percentText(stage.completionRatio ?? (stage.stageStatus === 6 ? 100 : 0))} />
        <MiniMetric label="验工单" value={`${stage.inspectionCount} 个`} />
        <MiniMetric label="产值单" value={`${stage.outputValueCount} 个`} />
        <MiniMetric label="已发放" value={formatMoney(stage.paidOutputAmount)} />
      </div>
      <div className="flex flex-wrap gap-2 mt-4">
        {stage.latestInspectionStatus !== null && stage.latestInspectionStatus !== undefined && (
          <Badge variant="secondary">最近验工：{INSPECTION_STATUS_MAP[stage.latestInspectionStatus] ?? "未知"}</Badge>
        )}
        <Badge variant="secondary">文件 {stage.projectFileCount}</Badge>
      </div>
    </div>
  );
}

function MiniMetric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs text-slate-400">{label}</div>
      <div className="font-semibold text-slate-800 mt-0.5">{value}</div>
    </div>
  );
}
