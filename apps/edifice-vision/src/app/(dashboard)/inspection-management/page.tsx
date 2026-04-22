"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Download,
  Plus,
  Search,
  Eye,
  FileText,
  Clock,
  CheckCircle,
  XCircle,
  Banknote,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Upload,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { TablePageSkeleton, DialogSkeleton } from "@/components/ui/skeleton";
import {
  getAllInspectionList,
  getInspectionDetail,
  getInspectionOverview,
  applyInspection,
} from "@/services/inspection";
import { getMyProjects, getProjectDetail, uploadDocument } from "@/services/project";
import { ResponseCode } from "@/types/api";
import type {
  InspectionFormListVo,
  InspectionFormDetailVo,
  InspectionOverviewVo,
} from "@/types/inspection";
import { INSPECTION_STATUS_MAP } from "@/types/inspection";
import type { ProjectListVo, ProjectStageVo, FilesVo } from "@/types/project";

type TabKey = "all" | "pending" | "passed" | "rejected";

const statusStyles: Record<string, string> = {
  待审核: "bg-amber-100 text-amber-600",
  审核中: "bg-blue-100 text-blue-600",
  已通过: "bg-emerald-100 text-emerald-600",
  已驳回: "bg-rose-100 text-rose-600",
  草稿: "bg-slate-100 text-slate-500",
};

const statusFilterMap: Record<TabKey, number | undefined> = {
  all: undefined,
  pending: 0,
  passed: 3,
  rejected: 2,
};

function getStatusLabel(status: number): string {
  return INSPECTION_STATUS_MAP[status] ?? "未知";
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.replace("T", " ").slice(0, 16);
}

function formatAmount(amount: number | null | undefined): string {
  if (!amount) return "-";
  return `¥${(amount / 10000).toFixed(2)}万`;
}

const PAGE_SIZE = 10;

export default function InspectionManagementPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [inspections, setInspections] = useState<InspectionFormListVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statistics, setStatistics] = useState<InspectionOverviewVo | null>(null);

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<InspectionFormDetailVo | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 发起验工弹窗
  const [createOpen, setCreateOpen] = useState(false);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, debouncedSearch]);

  // 加载列表
  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getAllInspectionList({
        inspectionFormCode: debouncedSearch || undefined,
        inspectionFormStatus: statusFilterMap[activeTab],
        current: currentPage,
        pageSize: PAGE_SIZE,
      }, signal);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setInspections(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      }
    } catch (err) {
      if (isAbortError(err)) return;
      setInspections([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [activeTab, debouncedSearch, currentPage]);

  const fetchStats = useCallback(async () => {
    try {
      const res = await getInspectionOverview();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setStatistics(res.data);
      }
    } catch { /* 静默 */ }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  useEffect(() => { fetchStats(); }, [fetchStats]);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  // 查看详情
  const openDetail = async (id: string) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      const res = await getInspectionDetail(id);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
      }
    } catch { /* 静默 */ }
    finally { setDetailLoading(false); }
  };

  const allCount = (statistics?.pendingApproval ?? 0) + (statistics?.pendingFirstReview ?? 0)
    + (statistics?.approved ?? 0) + (statistics?.rejected ?? 0);

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: allCount },
    { key: "pending", label: "审批中", count: statistics?.pendingApproval ?? 0 },
    { key: "passed", label: "已通过", count: statistics?.approved ?? 0 },
    { key: "rejected", label: "已驳回", count: statistics?.rejected ?? 0 },
  ];

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
            onClick={() => setCreateOpen(true)}
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
          >
            <Plus className="w-4 h-4" /> 发起验工
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-5 gap-4">
        <StatCard icon={<FileText className="w-5 h-5" />} label="验工单总数" value={allCount} color="slate" />
        <StatCard icon={<Clock className="w-5 h-5" />} label="审批中" value={statistics?.pendingApproval ?? 0} color="amber" />
        <StatCard icon={<CheckCircle className="w-5 h-5" />} label="已通过" value={statistics?.approved ?? 0} color="emerald" />
        <StatCard icon={<XCircle className="w-5 h-5" />} label="已驳回" value={statistics?.rejected ?? 0} color="rose" />
        <StatCard icon={<Banknote className="w-5 h-5" />} label="审核中" value={statistics?.pendingFirstReview ?? 0} color="blue" />
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
                activeTab === item.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
              <span className={cn("text-xs px-1.5 py-0.5 rounded-full",
                activeTab === item.key ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-500")}>
                {item.count}
              </span>
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="搜索验工单号..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Loading */}
      {loading && <TablePageSkeleton columns={5} rows={5} />}

      {/* Table */}
      {!loading && inspections.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-slate-50/50">
              <tr className="text-slate-500 text-xs uppercase tracking-wider">
                <th className="text-left py-4 px-6 font-semibold">验工单信息</th>
                <th className="text-left py-4 px-4 font-semibold">项目阶段</th>
                <th className="text-left py-4 px-4 font-semibold">阶段产值</th>
                <th className="text-left py-4 px-4 font-semibold">发起人</th>
                <th className="text-left py-4 px-4 font-semibold">发起时间</th>
                <th className="text-center py-4 px-4 font-semibold">状态</th>
                <th className="text-right py-4 px-6 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {inspections.map((item) => {
                const statusLabel = getStatusLabel(item.inspectionFormStatus);
                const phaseAmount = item.contractAmount && item.stageOutput
                  ? (item.contractAmount * item.stageOutput) / 100 : 0;

                return (
                  <tr key={item.inspectionFormId} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-4 px-6">
                      <p className="text-sm font-semibold text-slate-800">{item.projectName || "-"}</p>
                      <p className="text-xs text-slate-400 mt-0.5">{item.inspectionFormCode}</p>
                    </td>
                    <td className="py-4 px-4">
                      <span className="text-sm text-slate-600">{item.stageName || "-"}</span>
                      {item.projectTypeName && (
                        <p className="text-xs text-slate-400 mt-0.5">
                          {item.projectTypeName} · 产值比例 {item.stageOutput ?? 0}%
                        </p>
                      )}
                    </td>
                    <td className="py-4 px-4">
                      <span className="text-sm font-semibold text-slate-800">
                        {phaseAmount > 0 ? formatAmount(phaseAmount) : "-"}
                      </span>
                    </td>
                    <td className="py-4 px-4">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                          {(item.applyUserName ?? "?")[0]}
                        </div>
                        <span className="text-sm text-slate-600">{item.applyUserName || "-"}</span>
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      <span className="text-sm text-slate-500">{formatDate(item.createdTime)}</span>
                    </td>
                    <td className="py-4 px-4 text-center">
                      <Badge variant="secondary" className={cn("text-xs font-medium", statusStyles[statusLabel] ?? "")}>
                        {statusLabel}
                      </Badge>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <button
                        onClick={() => openDetail(item.inspectionFormId)}
                        className="px-3 py-1.5 text-xs text-slate-600 font-medium bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
                      >
                        查看详情
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Empty */}
      {!loading && inspections.length === 0 && (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <FileText className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无验工单</h3>
          <p className="text-sm text-slate-500">当前筛选条件下没有找到验工单</p>
        </div>
      )}

      {/* Pagination */}
      {!loading && total > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{total}</span> 条记录
          </p>
          <div className="flex items-center gap-2">
            <button disabled={currentPage <= 1} onClick={() => setCurrentPage((p) => p - 1)}
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50">
              <ChevronLeft className="w-4 h-4" />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <button key={page} onClick={() => setCurrentPage(page)}
                className={cn("px-3 py-1.5 text-sm font-medium rounded-lg",
                  page === currentPage ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-100")}>
                {page}
              </button>
            ))}
            <button disabled={currentPage >= totalPages} onClick={() => setCurrentPage((p) => p + 1)}
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50">
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Detail Dialog */}
      <InspectionDetailDialog
        open={detailOpen}
        onOpenChange={setDetailOpen}
        detail={detail}
        loading={detailLoading}
      />

      {/* Create Dialog */}
      <CreateInspectionDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={() => { fetchList(); fetchStats(); }}
      />
    </div>
  );
}

// ==================== 统计卡片 ====================

function StatCard({ icon, label, value, color }: {
  icon: React.ReactNode; label: string; value: number; color: string;
}) {
  const colorMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-600",
    amber: "bg-amber-100 text-amber-600",
    emerald: "bg-emerald-100 text-emerald-600",
    rose: "bg-rose-100 text-rose-600",
    blue: "bg-blue-100 text-blue-600",
  };
  return (
    <div className="glass-card p-4 rounded-xl">
      <div className="flex items-center gap-3">
        <div className={cn("p-2 rounded-lg", colorMap[color])}>{icon}</div>
        <div>
          <p className="text-xs text-slate-500">{label}</p>
          <p className="text-xl font-bold text-slate-800">{value}</p>
        </div>
      </div>
    </div>
  );
}

// ==================== 验工单详情弹窗 ====================

function InspectionDetailDialog({ open, onOpenChange, detail, loading }: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  detail: InspectionFormDetailVo | null;
  loading: boolean;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
        {loading && (
          <>
            <DialogHeader><DialogTitle>验工单详情</DialogTitle></DialogHeader>
            <DialogSkeleton />
          </>
        )}
        {!loading && detail && (
          <>
            <DialogHeader>
              <div className="flex items-center gap-3 mb-1">
                <DialogTitle>验工单详情</DialogTitle>
                <Badge variant="secondary" className={cn("text-xs font-medium",
                  statusStyles[getStatusLabel(detail.inspectionFormStatus)] ?? "")}>
                  {getStatusLabel(detail.inspectionFormStatus)}
                </Badge>
              </div>
              <DialogDescription>{detail.inspectionFormCode}</DialogDescription>
            </DialogHeader>
            <div className="mt-4 space-y-5">
              <div className="grid grid-cols-2 gap-4">
                <InfoItem label="项目名称" value={detail.projectName || "-"} />
                <InfoItem label="项目分类" value={detail.projectTypeName || "-"} />
                <InfoItem label="验工阶段" value={detail.stageName || "-"} />
                <InfoItem label="产值比例" value={detail.stageOutput ? `${detail.stageOutput}%` : "-"} />
                <InfoItem label="发起人" value={detail.applyUserName || "-"} />
                <InfoItem label="发起时间" value={formatDate(detail.createdTime)} />
              </div>
              {detail.inspectionFormDescription && (
                <div>
                  <p className="text-xs text-slate-400 mb-2">验工说明</p>
                  <div className="p-4 bg-slate-50 rounded-xl text-sm text-slate-600 leading-relaxed">
                    {detail.inspectionFormDescription}
                  </div>
                </div>
              )}
              {detail.approvalRecords && detail.approvalRecords.length > 0 && (
                <div>
                  <p className="text-xs text-slate-400 mb-2">审批记录</p>
                  <div className="space-y-3">
                    {detail.approvalRecords.map((record) => (
                      <div key={record.approvalRecordId} className="flex gap-4 p-4 bg-slate-50 rounded-xl">
                        <div className={cn("w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-medium shrink-0",
                          record.inspectionFormStatus === 1 ? "bg-emerald-500" : "bg-rose-500")}>
                          {record.inspectionFormStatus === 1 ? "✓" : "✗"}
                        </div>
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-medium text-slate-800">{record.approverName || "审批人"}</span>
                            <Badge variant="secondary" className={cn("text-xs",
                              record.inspectionFormStatus === 1 ? "bg-emerald-100 text-emerald-600" : "bg-rose-100 text-rose-600")}>
                              {record.inspectionFormStatus === 1 ? "通过" : "驳回"}
                            </Badge>
                          </div>
                          {record.approvalDescription && <p className="text-sm text-slate-600">{record.approvalDescription}</p>}
                          <p className="text-xs text-slate-400 mt-1">{formatDate(record.createdTime)}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

// ==================== 发起验工弹窗 ====================

function CreateInspectionDialog({ open, onOpenChange, onSuccess }: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onSuccess: () => void;
}) {
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [selectedStageId, setSelectedStageId] = useState("");
  const [description, setDescription] = useState("");
  const [files, setFiles] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 加载我的项目（进行中的）
  useEffect(() => {
    if (!open) {
      setSelectedProjectId("");
      setSelectedStageId("");
      setDescription("");
      setFiles([]);
      setStages([]);
      return;
    }
    async function loadProjects() {
      try {
        const res = await getMyProjects({ projectStatus: 1, pageSize: 100 });
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setProjects(res.data.records ?? []);
        }
      } catch { /* 静默 */ }
    }
    loadProjects();
  }, [open]);

  // 选择项目后加载阶段
  useEffect(() => {
    if (!selectedProjectId) {
      setStages([]);
      setSelectedStageId("");
      return;
    }
    async function loadStages() {
      try {
        const res = await getProjectDetail(selectedProjectId);
        if (res.code === ResponseCode.SUCCESS && res.data?.projectStages) {
          // 只显示状态为 1(进行中) 的阶段
          const inProgressStages = res.data.projectStages.filter((s) => s.stageStatus === 1);
          setStages(inProgressStages);
        }
      } catch { /* 静默 */ }
    }
    loadStages();
  }, [selectedProjectId]);

  const handleFileUpload = async (file: File) => {
    setUploading(true);
    try {
      const res = await uploadDocument(file);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setFiles((prev) => [...prev, res.data]);
      } else {
        toast.error(res.msg || "文件上传失败");
      }
    } catch {
      toast.error("文件上传失败");
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async () => {
    if (!selectedProjectId) { toast.error("请选择项目"); return; }
    if (!selectedStageId) { toast.error("请选择验工阶段"); return; }
    if (!description.trim()) { toast.error("请填写验工说明"); return; }

    setSubmitting(true);
    try {
      const fileIds = files.length > 0
        ? JSON.stringify(files.map((f) => f.fileId))
        : undefined;

      const res = await applyInspection({
        projectId: Number(selectedProjectId),
        projectStageId: Number(selectedStageId),
        inspectionFormDescription: description,
        fileIds,
      });

      if (res.code === ResponseCode.SUCCESS) {
        toast.success("验工单提交成功");
        onOpenChange(false);
        onSuccess();
      } else {
        toast.error(res.msg || "提交失败");
      }
    } catch {
      toast.error("网络异常，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>发起验工申请</DialogTitle>
          <DialogDescription>选择项目和阶段，提交验工材料</DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          {/* 选择项目 */}
          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              选择项目 <span className="text-rose-500">*</span>
            </label>
            <select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="form-input"
            >
              <option value="">请选择进行中的项目</option>
              {projects.map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName} ({p.projectCode})
                </option>
              ))}
            </select>
          </div>

          {/* 选择阶段 */}
          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              验工阶段 <span className="text-rose-500">*</span>
            </label>
            <select
              value={selectedStageId}
              onChange={(e) => setSelectedStageId(e.target.value)}
              className="form-input"
              disabled={!selectedProjectId}
            >
              <option value="">{selectedProjectId ? "请选择阶段" : "请先选择项目"}</option>
              {stages.map((s) => (
                <option key={s.projectStageId} value={s.projectStageId}>
                  {s.stageName} (产值比例 {s.stageOutput}%)
                </option>
              ))}
            </select>
            {selectedProjectId && stages.length === 0 && (
              <p className="text-xs text-amber-500 mt-1">该项目暂无进行中的阶段，请先在项目详情中启动阶段</p>
            )}
          </div>

          {/* 验工说明 */}
          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              验工说明 <span className="text-rose-500">*</span>
            </label>
            <textarea
              rows={4}
              placeholder="请描述本阶段完成的主要工作内容..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="form-input resize-none"
            />
          </div>

          {/* 附件上传 */}
          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">验收材料</label>
            {files.length > 0 && (
              <div className="space-y-2 mb-2">
                {files.map((f) => (
                  <div key={f.fileId} className="flex items-center gap-2 p-2 bg-slate-50 rounded-lg">
                    <FileText className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                    <span className="text-xs text-slate-600 truncate flex-1">{f.displayName}</span>
                    <button onClick={() => setFiles((prev) => prev.filter((x) => x.fileId !== f.fileId))}
                      className="p-0.5 text-slate-400 hover:text-rose-500 transition-colors">
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
            <label className="flex items-center justify-center gap-2 p-4 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors">
              {uploading
                ? <Loader2 className="w-4 h-4 text-blue-500 animate-spin" />
                : <Upload className="w-4 h-4 text-slate-400" />}
              <span className="text-sm text-slate-500">{uploading ? "上传中..." : "点击上传验收材料"}</span>
              <input type="file" className="hidden" accept=".pdf,.doc,.docx,.xls,.xlsx"
                disabled={uploading} onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleFileUpload(file);
                  e.target.value = "";
                }} />
            </label>
          </div>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-4 border-t border-slate-100 mt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button className="bg-blue-600 hover:bg-blue-700 text-white" disabled={submitting} onClick={handleSubmit}>
            {submitting ? <><Loader2 className="w-4 h-4 animate-spin mr-1" />提交中...</> : "提交验工"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ==================== 工具组件 ====================

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="py-2 px-3 bg-slate-50 rounded-lg">
      <p className="text-xs text-slate-400 mb-0.5">{label}</p>
      <p className="text-sm font-medium text-slate-800">{value}</p>
    </div>
  );
}
