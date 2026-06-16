"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Download,
  Search,
  Clock,
  UserCheck,
  CheckCircle,
  XCircle,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  X,
  Check,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { TablePageSkeleton, DialogSkeleton } from "@/components/ui/skeleton";
import {
  getMyPendingInspectionList,
  getInspectionDetail,
  getMyPendingInspectionOverview,
  approvalInspection,
  getMyInspectionExportUrl,
} from "@/services/inspection";
import { getUserList } from "@/services/project";
import { getAccessToken } from "@/lib/token";
import { useAuth } from "@/store/auth-context";
import { ResponseCode } from "@/types/api";
import type {
  InspectionFormListVo,
  InspectionFormDetailVo,
  InspectionOverviewVo,
} from "@/types/inspection";
import type { UserListItem } from "@/types/project";
import { INSPECTION_STATUS_MAP } from "@/types/inspection";
import { AttachmentFileList, parseFileIdList } from "@/components/file/attachment-file-list";
import { useDetailLink } from "@/hooks/use-detail-link";

type TabKey = "pending" | "passed" | "rejected" | "all";

const statusStyles: Record<string, string> = {
  待审核: "bg-amber-100 text-amber-600",
  审核中: "bg-blue-100 text-blue-600",
  已通过: "bg-emerald-100 text-emerald-600",
  已驳回: "bg-rose-100 text-rose-600",
  草稿: "bg-slate-100 text-slate-500",
};

/** pending tab 同时包含 0-待审核 / 1-审核中；其余单选 */
const statusFilterMap: Record<TabKey, { single?: number; multi?: number[] }> = {
  pending: { multi: [0, 1] },
  passed: { single: 3 },
  rejected: { single: 2 },
  all: {},
};

function getStatusLabel(status: number): string {
  return INSPECTION_STATUS_MAP[status] ?? "未知";
}

function getApprovalRecordLabel(status: number): string {
  if (status === 0) return "待审核";
  if (status === 1) return "通过";
  if (status === 2) return "驳回";
  return "未知";
}

function getApprovalRecordMarker(status: number) {
  if (status === 1) return <Check className="w-4 h-4" />;
  if (status === 2) return <X className="w-4 h-4" />;
  return <Clock className="w-4 h-4" />;
}

function getApprovalRecordDotStyle(status: number): string {
  if (status === 1) return "bg-emerald-500";
  if (status === 2) return "bg-rose-500";
  return "bg-amber-500";
}

function getApprovalRecordBadgeStyle(status: number): string {
  if (status === 1) return "bg-emerald-100 text-emerald-600";
  if (status === 2) return "bg-rose-100 text-rose-600";
  return "bg-amber-100 text-amber-600";
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

export default function InspectionApprovalPage() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabKey>("pending");
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
  const [approvalComment, setApprovalComment] = useState("");
  const [approving, setApproving] = useState(false);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [nextApproverId, setNextApproverId] = useState("");
  const [terminateHere, setTerminateHere] = useState(false);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, debouncedSearch]);

  // 加载列表（带请求取消，防止切换 tab 时旧数据闪现）
  // 注意：abort 时不能关闭 loading——否则 StrictMode 双调用或快速切 tab 时，
  // 被 abort 的请求会把 loading 置 false，导致骨架提前消失、闪出空态。
  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const filter = statusFilterMap[activeTab];
      const res = await getMyPendingInspectionList({
        inspectionFormCode: debouncedSearch || undefined,
        inspectionFormStatus: filter.single,
        inspectionFormStatuses: filter.multi,
        current: currentPage,
        pageSize: PAGE_SIZE,
      }, signal);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setInspections(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return; // 被取消的请求，保持 loading 让新请求接管
      setInspections([]);
      setTotal(0);
      setLoading(false);
    }
  }, [activeTab, debouncedSearch, currentPage]);

  // 加载统计
  const fetchStats = useCallback(async () => {
    try {
      const res = await getMyPendingInspectionOverview();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setStatistics(res.data);
      }
    } catch {
      // 静默
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  // 打开详情
  const openDetail = async (id: string) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    setApprovalComment("");
    setNextApproverId("");
    setTerminateHere(false);
    try {
      const [res, userRes] = await Promise.all([
        getInspectionDetail(id),
        getUserList(),
      ]);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
      }
      if (userRes.code === ResponseCode.SUCCESS && userRes.data) {
        setUsers(userRes.data.records ?? []);
      }
    } catch {
      // 静默
    } finally {
      setDetailLoading(false);
    }
  };
  useDetailLink(openDetail);

  // 审批操作
  const handleApproval = async (result: number) => {
    if (!detail) return;
    const comment = approvalComment.trim();
    if (!comment) {
      toast.error("请输入审批意见");
      return;
    }
    const pass = result === 1;
    const currentRecord = (detail.approvalRecords ?? []).find((record) => record.inspectionFormStatus === 0);
    const currentLevel = currentRecord?.approvalLevel ?? 1;
    const defaultTerminate = currentLevel >= 3;
    const shouldTerminate = pass ? terminateHere || defaultTerminate : true;
    if (pass && !shouldTerminate && !nextApproverId) {
      toast.error("请选择下一级审批人，或勾选终审通过");
      return;
    }

    setApproving(true);
    try {
      const res = await approvalInspection({
        inspectionFormId: detail.inspectionFormId,
        result,
        approvalDescription: comment,
        nextApproverId: pass && !shouldTerminate ? nextApproverId : undefined,
        terminate: shouldTerminate,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(result === 1 ? "审批通过" : "已驳回");
        setDetailOpen(false);
        fetchList();
        fetchStats();
      }
    } catch {
      /* 网络错误由 request.ts 提示 */
    } finally {
      setApproving(false);
    }
  };

  const pendingTotal =
    (statistics?.pendingApproval ?? 0) + (statistics?.pendingFirstReview ?? 0);
  const grandTotal =
    pendingTotal + (statistics?.approved ?? 0) + (statistics?.rejected ?? 0);
  const detailCurrentRecord = detail?.approvalRecords?.find((record) => record.inspectionFormStatus === 0);
  const detailCurrentLevel = detailCurrentRecord?.approvalLevel ?? 1;
  const detailDefaultTerminate = detailCurrentLevel >= 3;
  const currentUserId = user?.userId == null ? "" : String(user.userId);
  const canApproveDetail =
    !!detailCurrentRecord &&
    !!currentUserId &&
    String(detailCurrentRecord.approver) === currentUserId &&
    (detail?.inspectionFormStatus === 0 || detail?.inspectionFormStatus === 1);

  const canApproveItem = (item: InspectionFormListVo) =>
    !!currentUserId &&
    String(item.currentApproverId ?? "") === currentUserId &&
    (item.inspectionFormStatus === 0 || item.inspectionFormStatus === 1);

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "pending", label: "待审批", count: pendingTotal },
    { key: "passed", label: "已通过", count: statistics?.approved ?? 0 },
    { key: "rejected", label: "已驳回", count: statistics?.rejected ?? 0 },
    { key: "all", label: "全部", count: grandTotal },
  ];

  return (
    <div className="p-4 md:p-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            验工审批
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            审核项目阶段验工申请，确保工作质量与文档完整性。
          </p>
        </div>
        <Button
          variant="outline"
          className="flex items-center gap-2"
          onClick={() => {
            const filter = statusFilterMap[activeTab];
            const url = getMyInspectionExportUrl({
              inspectionFormCode: debouncedSearch || undefined,
              inspectionFormStatus: filter.single,
            });
            const token = getAccessToken();
            const separator = url.includes("?") ? "&" : "?";
            window.open(token ? `${url}${separator}token=${token}` : url, "_blank");
          }}
        >
          <Download className="w-4 h-4" /> 导出
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-amber-100 text-amber-600 rounded-lg"><Clock className="w-5 h-5" /></div>
            <div>
              <p className="text-xs text-slate-500">待审批</p>
              <p className="text-xl font-bold text-slate-800">{statistics?.pendingApproval ?? 0}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-600 rounded-lg"><UserCheck className="w-5 h-5" /></div>
            <div>
              <p className="text-xs text-slate-500">审核中</p>
              <p className="text-xl font-bold text-slate-800">{statistics?.pendingFirstReview ?? 0}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-emerald-100 text-emerald-600 rounded-lg"><CheckCircle className="w-5 h-5" /></div>
            <div>
              <p className="text-xs text-slate-500">已通过</p>
              <p className="text-xl font-bold text-slate-800">{statistics?.approved ?? 0}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-rose-100 text-rose-600 rounded-lg"><XCircle className="w-5 h-5" /></div>
            <div>
              <p className="text-xs text-slate-500">已驳回</p>
              <p className="text-xl font-bold text-slate-800">{statistics?.rejected ?? 0}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs + Search */}
      <div className="flex flex-wrap items-center gap-3">
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
              <span className={cn("text-xs px-1.5 py-0.5 rounded-full", activeTab === item.key ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-500")}>
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
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-full sm:w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Loading */}
      {loading && <TablePageSkeleton columns={4} rows={5} />}

      {/* Mobile card list（md 以下） */}
      {!loading && inspections.length > 0 && (
        <div className="md:hidden space-y-3">
          {inspections.map((item) => {
            const statusLabel = getStatusLabel(item.inspectionFormStatus);
            const phaseAmount = item.contractAmount && item.stageOutput
              ? (item.contractAmount * item.stageOutput) / 100
              : 0;
            return (
              <button
                key={item.inspectionFormId}
                onClick={() => openDetail(item.inspectionFormId)}
                className="w-full glass-card rounded-2xl shadow-sm p-4 text-left space-y-2 active:scale-[0.99] transition-transform"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-slate-800 truncate">
                      {item.projectName || "-"}
                    </p>
                    <p className="text-xs text-slate-400 mt-0.5">{item.inspectionFormCode}</p>
                  </div>
                  <Badge
                    variant="secondary"
                    className={cn("text-xs font-medium shrink-0", statusStyles[statusLabel] ?? "")}
                  >
                    {statusLabel}
                  </Badge>
                </div>
                <div className="text-xs text-slate-500 space-y-0.5">
                  <p>阶段：{item.stageName || "-"}</p>
                  {phaseAmount > 0 && (
                    <p>阶段产值：<span className="font-semibold text-slate-700">{formatAmount(phaseAmount)}</span></p>
                  )}
                  <p>
                    {item.applyUserName || "-"} · {formatDate(item.createdTime)}
                  </p>
                </div>
                {canApproveItem(item) && (
                  <div className="flex justify-end pt-2 border-t border-slate-100">
                    <span className="px-3 py-1.5 text-xs text-white font-medium bg-blue-600 rounded-lg">
                      审批
                    </span>
                  </div>
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* Desktop Table（md 以上） */}
      {!loading && inspections.length > 0 && (
        <div className="hidden md:block glass-card rounded-2xl shadow-sm overflow-x-auto">
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
                  ? (item.contractAmount * item.stageOutput) / 100
                  : 0;

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
                        <div className="w-7 h-7 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
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
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => openDetail(item.inspectionFormId)}
                          className="px-3 py-1.5 text-xs text-slate-600 font-medium bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
                        >
                          查看详情
                        </button>
                        {canApproveItem(item) && (
                          <button
                            onClick={() => openDetail(item.inspectionFormId)}
                            className="px-3 py-1.5 text-xs text-white font-medium bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                          >
                            审批
                          </button>
                        )}
                      </div>
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
            <ClipboardCheck className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无验工单</h3>
          <p className="text-sm text-slate-500">当前筛选条件下没有找到验工单</p>
        </div>
      )}

      {/* Pagination */}
      {!loading && total > 0 && (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{total}</span> 条记录
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
                  page === currentPage ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-100"
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

      {/* Detail Dialog */}
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
          {detailLoading && (
            <>
              <DialogHeader><DialogTitle>验工单详情</DialogTitle></DialogHeader>
              <DialogSkeleton />
            </>
          )}

          {!detailLoading && detail && (
            <>
              <DialogHeader>
                <div className="flex items-center gap-3 mb-1">
                  <DialogTitle>验工单详情</DialogTitle>
                  <Badge variant="secondary" className={cn("text-xs font-medium", statusStyles[getStatusLabel(detail.inspectionFormStatus)] ?? "")}>
                    {getStatusLabel(detail.inspectionFormStatus)}
                  </Badge>
                </div>
                <DialogDescription>{detail.inspectionFormCode}</DialogDescription>
              </DialogHeader>

              <div className="mt-4 space-y-5">
                {/* 基本信息 */}
                <div className="grid grid-cols-2 gap-4">
                  <InfoItem label="项目名称" value={detail.projectName || "-"} />
                  <InfoItem label="项目分类" value={detail.projectTypeName || "-"} />
                  <InfoItem label="验工阶段" value={detail.stageName || "-"} />
                  <InfoItem label="阶段产值比例" value={detail.stageOutput ? `${detail.stageOutput}%` : "-"} />
                  <InfoItem label="发起人" value={detail.applyUserName || "-"} />
                  <InfoItem label="发起时间" value={formatDate(detail.createdTime)} />
                </div>

                {/* 验工说明 */}
                {detail.inspectionFormDescription && (
                  <div>
                    <p className="text-xs text-slate-400 mb-2">验工说明</p>
                    <div className="p-4 bg-slate-50 rounded-xl text-sm text-slate-600 leading-relaxed">
                      {detail.inspectionFormDescription}
                    </div>
                  </div>
                )}

                {/* 附件 */}
                {parseFileIdList(detail.fileIds).length > 0 && (
                  <div>
                    <p className="text-xs text-slate-400 mb-2">验收材料</p>
                    <AttachmentFileList fileIds={parseFileIdList(detail.fileIds)} labelPrefix="验收材料" />
                  </div>
                )}

                {/* 审批记录 */}
                {detail.approvalRecords && detail.approvalRecords.length > 0 && (
                  <div>
                    <p className="text-xs text-slate-400 mb-2">审批记录</p>
                    <div className="space-y-3">
                      {detail.approvalRecords.map((record) => (
                        <div key={record.approvalRecordId} className="flex gap-4 p-4 bg-slate-50 rounded-xl">
                          <div className={cn(
                            "w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-medium shrink-0",
                            getApprovalRecordDotStyle(record.inspectionFormStatus)
                          )}>
                            {getApprovalRecordMarker(record.inspectionFormStatus)}
                          </div>
                          <div className="flex-1">
                            <div className="flex items-center gap-2 mb-1">
                              <span className="text-sm font-medium text-slate-800">{record.approverName || "审批人"}</span>
                              <Badge variant="secondary" className={cn("text-xs", getApprovalRecordBadgeStyle(record.inspectionFormStatus))}>
                                {getApprovalRecordLabel(record.inspectionFormStatus)}
                              </Badge>
                            </div>
                            {record.approvalDescription && (
                              <p className="text-sm text-slate-600">{record.approvalDescription}</p>
                            )}
                            <p className="text-xs text-slate-400 mt-1">{formatDate(record.createdTime)}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 审批操作 */}
                {canApproveDetail && (
                  <div className="border-t border-slate-100 pt-5">
                    <p className="text-sm font-semibold text-slate-800 mb-3">审批操作</p>
                    <div className="flex items-center gap-2 mb-3">
                      <input
                        id="inspection-terminate"
                        type="checkbox"
                        checked={terminateHere || detailDefaultTerminate}
                        disabled={detailDefaultTerminate}
                        onChange={(e) => setTerminateHere(e.target.checked)}
                      />
                      <label htmlFor="inspection-terminate" className="text-sm text-slate-600">
                        终审（通过后直接完成验工；不再流转下一级）
                        {detailDefaultTerminate && <span className="text-slate-400 ml-1">(L3 自动终审)</span>}
                      </label>
                    </div>
                    {!(terminateHere || detailDefaultTerminate) && (
                      <div className="mb-3">
                        <label className="text-xs text-slate-500 mb-2 block">
                          下一级审批人 <span className="text-rose-500">*</span>
                        </label>
                        <select
                          className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                          value={nextApproverId}
                          onChange={(e) => setNextApproverId(e.target.value)}
                        >
                          <option value="">请选择下一级审批人</option>
                          {users.map((user) => (
                            <option key={user.userId} value={user.userId}>
                              {user.realName || user.username}
                            </option>
                          ))}
                        </select>
                        <p className="text-[11px] text-slate-400 mt-1">
                          L1 建议选专业主管 · L2 建议选总工；当前 L{detailCurrentLevel}
                        </p>
                      </div>
                    )}
                    <div>
                      <label className="text-xs text-slate-500 mb-2 block">
                        审批意见 <span className="text-rose-500">*</span>
                      </label>
                      <textarea
                        className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                        rows={3}
                        placeholder="请输入审批意见..."
                        value={approvalComment}
                        onChange={(e) => setApprovalComment(e.target.value)}
                      />
                    </div>
                    <div className="flex justify-end gap-3 mt-4">
                      <Button
                        variant="outline"
                        className="text-rose-600 bg-rose-50 hover:bg-rose-100 border-rose-200"
                        disabled={approving}
                        onClick={() => handleApproval(2)}
                      >
                        {approving ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <X className="w-4 h-4 mr-1" />}
                        驳回
                      </Button>
                      <Button
                        className="bg-emerald-600 hover:bg-emerald-700"
                        disabled={approving}
                        onClick={() => handleApproval(1)}
                      >
                        {approving ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <Check className="w-4 h-4 mr-1" />}
                        通过
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="py-2 px-3 bg-slate-50 rounded-lg">
      <p className="text-xs text-slate-400 mb-0.5">{label}</p>
      <p className="text-sm font-medium text-slate-800">{value}</p>
    </div>
  );
}
