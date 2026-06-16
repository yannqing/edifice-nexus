"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Archive,
  ArchiveRestore,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  FileText,
  Loader2,
  Search,
  TriangleAlert,
  XCircle,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import {
  archiveProjectWithRemark,
  downloadProjectArchivePackage,
  getArchivableProjects,
  getArchivedProjects,
  getProjectArchiveDetail,
  unarchiveProject,
} from "@/services/project-archive";
import { ResponseCode } from "@/types/api";
import { INSPECTION_STATUS_MAP } from "@/types/inspection";
import { OUTPUT_VALUE_STATUS_MAP } from "@/types/output-value";
import { PROJECT_FILE_STATUS_MAP } from "@/types/project-file";
import type { ArchiveChecklistItemVo, ProjectArchiveDetailVo, ProjectArchiveVo } from "@/types/project-archive";

const PAGE_SIZE = 10;

type ArchiveTab = "ready" | "archived";

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDate(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

const checklistStyles: Record<string, string> = {
  pass: "bg-emerald-100 text-emerald-700",
  warning: "bg-amber-100 text-amber-700",
  fail: "bg-rose-100 text-rose-700",
};

function checklistIcon(status: string) {
  if (status === "pass") return <CheckCircle2 className="w-4 h-4" />;
  if (status === "fail") return <XCircle className="w-4 h-4" />;
  return <TriangleAlert className="w-4 h-4" />;
}

export default function ProjectArchivePage() {
  const [activeTab, setActiveTab] = useState<ArchiveTab>("ready");
  const [items, setItems] = useState<ProjectArchiveVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [actionTarget, setActionTarget] = useState<ProjectArchiveVo | null>(null);
  const [archiveRemark, setArchiveRemark] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<ProjectArchiveDetailVo | null>(null);
  const [downloadLoading, setDownloadLoading] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => setCurrentPage(1), [activeTab]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const request = activeTab === "ready" ? getArchivableProjects : getArchivedProjects;
      const res = await request(
        {
          keywords: debouncedKeyword || undefined,
          current: currentPage,
          pageSize: PAGE_SIZE,
        },
        signal
      );
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setItems(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return;
      setItems([]);
      setTotal(0);
      setLoading(false);
    }
  }, [activeTab, currentPage, debouncedKeyword]);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const isArchivedTab = activeTab === "archived";

  const confirmAction = async () => {
    if (!actionTarget) return;
    setActionLoading(true);
    try {
      const res = isArchivedTab
        ? await unarchiveProject(actionTarget.projectId)
        : await archiveProjectWithRemark(actionTarget.projectId, archiveRemark);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(isArchivedTab ? "已取消归档" : "归档成功");
        setActionTarget(null);
        setArchiveRemark("");
        fetchList();
      }
    } finally {
      setActionLoading(false);
    }
  };

  const openDetail = async (item: ProjectArchiveVo) => {
    setDetailOpen(true);
    setDetail(null);
    setDetailLoading(true);
    try {
      const res = await getProjectArchiveDetail(item.projectId);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
      }
    } finally {
      setDetailLoading(false);
    }
  };

  const handleDownloadPackage = async (projectId: string) => {
    setDownloadLoading(projectId);
    try {
      await downloadProjectArchivePackage(projectId);
      toast.success("归档资料包已开始下载");
    } catch {
      toast.error("下载失败，请稍后重试");
    } finally {
      setDownloadLoading(null);
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">项目归档</h1>
          <p className="text-slate-500 text-sm mt-1">归档已完成阶段的项目，并保留历史项目查询入口</p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {[
            { key: "ready" as const, label: "可归档项目" },
            { key: "archived" as const, label: "已归档项目" },
          ].map((item) => (
            <button
              key={item.key}
              onClick={() => setActiveTab(item.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                activeTab === item.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索项目名称 / 编号..."
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-full sm:w-80 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {loading && <TablePageSkeleton columns={7} rows={5} />}

      {!loading && (
        <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
          <table className="w-full text-sm min-w-[980px]">
            <thead>
              <tr className="text-left text-slate-500 border-b border-slate-100">
                <th className="px-5 py-4 font-medium">项目</th>
                <th className="px-5 py-4 font-medium">类型</th>
                <th className="px-5 py-4 font-medium">合同金额</th>
                <th className="px-5 py-4 font-medium">阶段完成</th>
                <th className="px-5 py-4 font-medium">项目文件</th>
                <th className="px-5 py-4 font-medium">日期</th>
                <th className="px-5 py-4 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.projectId} className="border-b border-slate-50 last:border-0 hover:bg-slate-50/70">
                  <td className="px-5 py-4">
                    <div className="font-semibold text-slate-800">{item.projectName}</div>
                    <div className="text-xs text-slate-400 mt-1">{item.projectCode || "-"}</div>
                    {!isArchivedTab && !item.archiveReady && (
                      <div className="text-xs text-amber-600 mt-2">{item.archiveWarning}</div>
                    )}
                    {isArchivedTab && (
                      <div className="text-xs text-slate-400 mt-2">
                        归档：{formatDate(item.archiveTime)} · {item.archiveUserName || "未知操作人"}
                      </div>
                    )}
                  </td>
                  <td className="px-5 py-4 text-slate-600">{item.projectType?.projectTypeName ?? "-"}</td>
                  <td className="px-5 py-4 font-semibold text-slate-800">{formatMoney(item.contractAmount)}</td>
                  <td className="px-5 py-4">
                    <Badge className={cn(
                      "hover:bg-inherit",
                      item.archiveReady || isArchivedTab
                        ? "bg-emerald-100 text-emerald-700"
                        : "bg-amber-100 text-amber-700"
                    )}>
                      {item.completedStageCount} / {item.totalStageCount}
                    </Badge>
                  </td>
                  <td className="px-5 py-4 text-slate-600">{item.fileCount ?? 0} 个</td>
                  <td className="px-5 py-4 text-slate-500">
                    <div>开始：{formatDate(item.projectStartTime)}</div>
                    <div className="text-xs mt-1">结束：{formatDate(item.projectEndTime)}</div>
                  </td>
                  <td className="px-5 py-4 text-right">
                    <div className="flex justify-end gap-2">
                      <Button variant="ghost" size="sm" onClick={() => openDetail(item)}>
                        <Eye className="w-4 h-4 mr-1" /> 详情
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDownloadPackage(item.projectId)}
                        disabled={downloadLoading === item.projectId}
                      >
                        {downloadLoading === item.projectId
                          ? <Loader2 className="w-4 h-4 mr-1 animate-spin" />
                          : <Download className="w-4 h-4 mr-1" />}
                        下载
                      </Button>
                      <Button
                        variant={isArchivedTab ? "outline" : "default"}
                        size="sm"
                        disabled={!isArchivedTab && !item.archiveReady}
                        onClick={() => {
                          setActionTarget(item);
                          setArchiveRemark(item.archiveRemark || "");
                        }}
                        className={isArchivedTab ? "" : "bg-blue-600 hover:bg-blue-700"}
                      >
                        {isArchivedTab ? (
                          <ArchiveRestore className="w-4 h-4 mr-1" />
                        ) : (
                          <Archive className="w-4 h-4 mr-1" />
                        )}
                        {isArchivedTab ? "取消归档" : "归档"}
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-5 py-16 text-center text-slate-400">暂无项目数据</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between text-sm text-slate-500">
        <span>共 {total} 条</span>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={currentPage <= 1} onClick={() => setCurrentPage((page) => page - 1)}>
            <ChevronLeft className="w-4 h-4" />
          </Button>
          <span>{currentPage} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={currentPage >= totalPages} onClick={() => setCurrentPage((page) => page + 1)}>
            <ChevronRight className="w-4 h-4" />
          </Button>
        </div>
      </div>

      <Dialog open={Boolean(actionTarget)} onOpenChange={(open) => {
        if (!open) {
          setActionTarget(null);
          setArchiveRemark("");
        }
      }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{isArchivedTab ? "取消归档项目" : "归档项目"}</DialogTitle>
            <DialogDescription>
              {isArchivedTab
                ? "取消归档后，项目会恢复到进行中状态。"
                : "归档后项目将进入历史项目列表，后续业务操作应先取消归档。"}
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
            <div className="font-medium text-slate-800">{actionTarget?.projectName}</div>
            <div className="mt-1">{actionTarget?.projectCode}</div>
            {isArchivedTab && actionTarget?.archiveRemark && (
              <div className="mt-2 text-slate-500">备注：{actionTarget.archiveRemark}</div>
            )}
          </div>
          {!isArchivedTab && (
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-slate-600">归档备注</span>
              <textarea
                value={archiveRemark}
                onChange={(event) => setArchiveRemark(event.target.value)}
                rows={3}
                placeholder="可填写归档说明、资料交接情况等"
                className="form-input resize-none"
              />
            </label>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => setActionTarget(null)}>取消</Button>
            <Button onClick={confirmAction} disabled={actionLoading} className="bg-blue-600 hover:bg-blue-700">
              {actionLoading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
              确认
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="sm:max-w-5xl">
          <DialogHeader>
            <DialogTitle>归档详情</DialogTitle>
            <DialogDescription>
              {detail?.project.projectName ?? "加载项目归档资料"}
            </DialogDescription>
          </DialogHeader>
          {detailLoading && (
            <div className="py-12 flex items-center justify-center text-slate-400">
              <Loader2 className="w-5 h-5 mr-2 animate-spin" /> 加载中...
            </div>
          )}
          {!detailLoading && detail && (
            <div className="space-y-5">
              <div className="flex justify-end">
                <Button
                  variant="outline"
                  onClick={() => handleDownloadPackage(detail.project.projectId)}
                  disabled={downloadLoading === detail.project.projectId}
                >
                  {downloadLoading === detail.project.projectId
                    ? <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    : <Download className="w-4 h-4 mr-2" />}
                  下载归档资料包
                </Button>
              </div>
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                <SummaryCard label="合同金额" value={formatMoney(detail.summary.contractAmount)} />
                <SummaryCard label="已发放产值" value={formatMoney(detail.summary.paidOutputAmount)} />
                <SummaryCard label="累计回款" value={formatMoney(detail.summary.totalCollectionAmount)} />
                <SummaryCard label="项目文件" value={`${detail.summary.projectFileCount ?? 0} 个`} />
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <section className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
                  <h3 className="font-semibold text-slate-800 mb-3">归档清单</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {detail.checklist.map((item) => (
                      <ChecklistItem key={item.itemKey} item={item} />
                    ))}
                  </div>
                </section>
                <section className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
                  <h3 className="font-semibold text-slate-800 mb-3">归档信息</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                    <Info label="项目编号" value={detail.project.projectCode} />
                    <Info label="项目类型" value={detail.project.projectType?.projectTypeName ?? "-"} />
                    <Info label="归档时间" value={formatDate(detail.archive.archiveTime)} />
                    <Info label="归档人" value={detail.archive.archiveUserName || "-"} />
                    <Info label="归档备注" value={detail.archive.archiveRemark || "-"} wide />
                  </div>
                </section>
              </div>

              <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                <ArchiveTable title="验工单" empty="暂无验工单">
                  {detail.inspections.map((item) => (
                    <div key={item.inspectionFormId} className="flex items-center justify-between gap-3 rounded-lg bg-white border border-slate-100 px-3 py-2">
                      <div>
                        <div className="font-medium text-slate-700">{item.inspectionFormCode}</div>
                        <div className="text-xs text-slate-400 mt-1">{item.stageName || "-"} · {item.applyUserName || "未知申请人"}</div>
                      </div>
                      <Badge variant="secondary">{INSPECTION_STATUS_MAP[item.inspectionFormStatus] ?? "未知"}</Badge>
                    </div>
                  ))}
                </ArchiveTable>

                <ArchiveTable title="产值分配" empty="暂无产值单">
                  {detail.outputValues.map((item) => (
                    <div key={item.outputValueId} className="flex items-center justify-between gap-3 rounded-lg bg-white border border-slate-100 px-3 py-2">
                      <div>
                        <div className="font-medium text-slate-700">{item.stageName || "-"} · {item.quarter || "-"}</div>
                        <div className="text-xs text-slate-400 mt-1">{formatMoney(item.totalAmount)} · 发放：{formatDate(item.paidTime)}</div>
                      </div>
                      <Badge variant="secondary">{OUTPUT_VALUE_STATUS_MAP[item.status] ?? "未知"}</Badge>
                    </div>
                  ))}
                </ArchiveTable>

                <ArchiveTable title="回款记录" empty="暂无回款记录">
                  {detail.collections.map((item) => (
                    <div key={item.collectionRecordId} className="flex items-center justify-between gap-3 rounded-lg bg-white border border-slate-100 px-3 py-2">
                      <div>
                        <div className="font-medium text-slate-700">{formatMoney(item.amount)}</div>
                        <div className="text-xs text-slate-400 mt-1">{item.stageName || "未关联阶段"} · {item.collectDate || "-"}</div>
                      </div>
                      <div className="text-xs text-slate-400">{item.recordUserName || "-"}</div>
                    </div>
                  ))}
                </ArchiveTable>

                <ArchiveTable title="项目文件" empty="暂无项目文件">
                  {detail.projectFiles.map((item) => (
                    <div key={item.projectFileId} className="flex items-center justify-between gap-3 rounded-lg bg-white border border-slate-100 px-3 py-2">
                      <div className="flex items-start gap-2 min-w-0">
                        <FileText className="w-4 h-4 text-slate-400 mt-0.5 shrink-0" />
                        <div className="min-w-0">
                          <div className="font-medium text-slate-700 truncate">{item.fileName || "未命名文件"}</div>
                          <div className="text-xs text-slate-400 mt-1">{item.stageName || "-"} · {PROJECT_FILE_STATUS_MAP[item.approvalStatus] ?? "未知"}</div>
                        </div>
                      </div>
                      {item.fileId && <AttachmentFileActions fileId={item.fileId} fileName={item.fileName || "项目文件"} />}
                    </div>
                  ))}
                </ArchiveTable>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-4">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="text-lg font-semibold text-slate-800 mt-1">{value}</div>
    </div>
  );
}

function ChecklistItem({ item }: { item: ArchiveChecklistItemVo }) {
  return (
    <div className="rounded-lg bg-white border border-slate-100 p-3">
      <div className="flex items-center gap-2">
        <Badge className={cn("gap-1 hover:bg-inherit", checklistStyles[item.status] ?? checklistStyles.warning)}>
          {checklistIcon(item.status)} {item.itemName}
        </Badge>
      </div>
      <div className="text-xs text-slate-500 mt-2">{item.description}</div>
    </div>
  );
}

function Info({ label, value, wide }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={cn("rounded-lg bg-white border border-slate-100 p-3", wide && "sm:col-span-2")}>
      <div className="text-xs text-slate-400">{label}</div>
      <div className="text-sm text-slate-700 mt-1 break-all">{value}</div>
    </div>
  );
}

function ArchiveTable({ title, empty, children }: { title: string; empty: string; children: React.ReactNode[] }) {
  const rows = Array.isArray(children) ? children.filter(Boolean) : [];
  return (
    <section className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
      <h3 className="font-semibold text-slate-800 mb-3">{title}</h3>
      <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
        {rows.length > 0 ? rows : <div className="text-sm text-slate-400 py-6 text-center">{empty}</div>}
      </div>
    </section>
  );
}
