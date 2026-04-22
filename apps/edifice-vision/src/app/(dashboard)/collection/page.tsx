"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Plus,
  Search,
  Banknote,
  CheckCircle,
  PieChart,
  Clock,
  X,
  AlertCircle,
  Loader2,
  Trash2,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  COLLECTION_STATUS_MAP,
  type CollectionDetailVo,
  type CollectionStatisticsVo,
  type CollectionSummaryVo,
} from "@/types/collection";
import type { ProjectListVo, ProjectStageVo } from "@/types/project";
import {
  createCollectionRecord,
  deleteCollectionRecord,
  getCollectionDetail,
  getCollectionList,
  getCollectionStatistics,
} from "@/services/collection";
import { getMyProjects, getProjectDetail } from "@/services/project";

type TabKey = "all" | "collected" | "partial" | "uncollected";

const statusStyles: Record<string, string> = {
  已回款: "bg-emerald-100 text-emerald-700",
  部分回款: "bg-amber-100 text-amber-700",
  未回款: "bg-rose-100 text-rose-700",
};

const statusFilterMap: Record<TabKey, number | undefined> = {
  all: undefined,
  collected: 2,
  partial: 1,
  uncollected: 0,
};

function getStatusLabel(status: number): string {
  return COLLECTION_STATUS_MAP[status] ?? "未知";
}

function formatAmount(v: number | null | undefined): string {
  const value = v ?? 0;
  if (value >= 10000) return `¥${(value / 10000).toFixed(1)}万`;
  return `¥${value.toLocaleString()}`;
}

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.slice(0, 10);
}

function getRateColor(rate: number) {
  if (rate >= 100) return "text-emerald-600";
  if (rate >= 50) return "text-amber-600";
  return "text-rose-600";
}

function getRateBarColor(rate: number) {
  if (rate >= 100) return "bg-emerald-500";
  if (rate >= 50) return "bg-amber-500";
  return "bg-rose-500";
}

const PAGE_SIZE = 10;

export default function CollectionPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [items, setItems] = useState<CollectionSummaryVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<CollectionStatisticsVo | null>(null);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<CollectionDetailVo | null>(null);

  const [addOpen, setAddOpen] = useState(false);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, debouncedSearch]);

  // 列表加载（abort 时保留 loading 让下一轮接管）
  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getCollectionList(
        {
          keywords: debouncedSearch || undefined,
          collectionStatus: statusFilterMap[activeTab],
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
  }, [activeTab, debouncedSearch, currentPage]);

  const fetchStats = useCallback(async () => {
    try {
      const res = await getCollectionStatistics();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setStats(res.data);
      }
    } catch {
      /* 静默 */
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const openDetail = async (projectId: string) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      const res = await getCollectionDetail(projectId);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
      } else {
        toast.error(res.msg || "加载失败");
      }
    } catch {
      toast.error("网络异常");
    } finally {
      setDetailLoading(false);
    }
  };

  const handleDeleteRecord = async (recordId: string) => {
    try {
      const res = await deleteCollectionRecord(recordId);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("删除成功");
        // 刷新详情 + 列表 + 统计
        if (detail) await openDetail(detail.summary.projectId);
        fetchList();
        fetchStats();
      } else {
        toast.error(res.msg || "删除失败");
      }
    } catch {
      toast.error("网络异常");
    }
  };

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: total },
    {
      key: "collected",
      label: "已回款",
      count: items.filter((i) => i.collectionStatus === 2).length,
    },
    {
      key: "partial",
      label: "部分回款",
      count: items.filter((i) => i.collectionStatus === 1).length,
    },
    {
      key: "uncollected",
      label: "未回款",
      count: items.filter((i) => i.collectionStatus === 0).length,
    },
  ];

  const statCards = [
    {
      label: "应收款总额",
      value: formatAmount(stats?.totalExpected ?? 0),
      icon: Banknote,
      color: "text-blue-600",
      bgColor: "bg-blue-50",
    },
    {
      label: "已回款金额",
      value: formatAmount(stats?.totalCollected ?? 0),
      icon: CheckCircle,
      color: "text-emerald-600",
      bgColor: "bg-emerald-50",
    },
    {
      label: "整体回款率",
      value: `${stats?.overallRate ?? 0}%`,
      icon: PieChart,
      color: "text-amber-600",
      bgColor: "bg-amber-50",
    },
    {
      label: "待回款金额",
      value: formatAmount(stats?.totalPending ?? 0),
      icon: Clock,
      color: "text-rose-600",
      bgColor: "bg-rose-50",
    },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            回款管理
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            管理项目回款计划与实际回款记录
          </p>
        </div>
        <Button
          onClick={() => setAddOpen(true)}
          className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
        >
          <Plus className="w-4 h-4" /> 录入回款
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-6">
        {statCards.map((stat) => (
          <div key={stat.label} className="glass-card rounded-2xl p-5 shadow-sm">
            <div className={cn("w-10 h-10 rounded-xl flex items-center justify-center mb-3", stat.bgColor)}>
              <stat.icon className={cn("w-5 h-5", stat.color)} />
            </div>
            <p className="text-2xl font-bold text-slate-800">{stat.value}</p>
            <p className="text-slate-500 text-sm mt-1">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={cn(
                  "px-4 py-2 rounded-lg text-sm font-medium transition-colors",
                  activeTab === tab.key
                    ? "bg-blue-600 text-white"
                    : "text-slate-600 hover:bg-slate-100"
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="搜索项目名称或编号..."
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              className="pl-10 pr-4 py-2 border border-slate-200 rounded-lg text-sm w-64 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </div>

      {loading && <TablePageSkeleton columns={4} rows={5} />}

      {!loading && items.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-slate-50 border-b border-slate-100">
              <tr>
                <th className="text-left py-4 px-6 text-sm font-semibold text-slate-600">项目信息</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">合同金额</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">已完成阶段</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">应收金额</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">已回款金额</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">回款率</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">状态</th>
                <th className="text-center py-4 px-4 text-sm font-semibold text-slate-600">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const statusLabel = getStatusLabel(item.collectionStatus);
                return (
                  <tr
                    key={item.projectId}
                    className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors"
                  >
                    <td className="py-4 px-6">
                      <div className="font-medium text-slate-800">{item.projectName}</div>
                      <div className="text-xs text-slate-400 mt-0.5">
                        {item.projectCode}
                        {item.projectTypeName ? ` · ${item.projectTypeName}` : ""}
                        {item.managerName ? ` · ${item.managerName}` : ""}
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      <span className="text-slate-700">{formatAmount(item.contractAmount)}</span>
                    </td>
                    <td className="py-4 px-4">
                      <span className="text-slate-600">{item.completedPhases}</span>
                    </td>
                    <td className="py-4 px-4">
                      <span className="font-medium text-slate-800">{formatAmount(item.expectedAmount)}</span>
                    </td>
                    <td className="py-4 px-4">
                      <span className="font-medium text-emerald-600">{formatAmount(item.collectedAmount)}</span>
                    </td>
                    <td className="py-4 px-4">
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-2 bg-slate-200 rounded-full overflow-hidden">
                          <div
                            className={cn("h-full rounded-full", getRateBarColor(item.collectionRate))}
                            style={{ width: `${Math.min(item.collectionRate, 100)}%` }}
                          />
                        </div>
                        <span className={cn("text-sm font-medium", getRateColor(item.collectionRate))}>
                          {item.collectionRate}%
                        </span>
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      <Badge variant="secondary" className={cn("text-xs", statusStyles[statusLabel])}>
                        {statusLabel}
                      </Badge>
                    </td>
                    <td className="py-4 px-4 text-center">
                      <button
                        onClick={() => openDetail(item.projectId)}
                        className="text-blue-600 hover:text-blue-700 text-sm font-medium"
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

      {!loading && items.length === 0 && (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Banknote className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无回款数据</h3>
          <p className="text-sm text-slate-500">当前筛选条件下没有找到数据</p>
        </div>
      )}

      {!loading && total > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{total}</span> 个项目
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

      {/* 详情弹窗 */}
      {detailOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold text-slate-800">回款详情</h2>
                <p className="text-slate-500 text-sm mt-1">
                  {detail?.summary.projectName ?? ""}
                </p>
              </div>
              <button
                onClick={() => setDetailOpen(false)}
                className="text-slate-400 hover:text-slate-600"
              >
                <X className="w-6 h-6" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto flex-1">
              {detailLoading && (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
                </div>
              )}

              {!detailLoading && detail && (
                <>
                  <div className="grid grid-cols-4 gap-4 mb-6">
                    <div className="bg-slate-50 rounded-xl p-4">
                      <p className="text-slate-500 text-sm">合同金额</p>
                      <p className="text-xl font-bold text-slate-800 mt-1">
                        {formatAmount(detail.summary.contractAmount)}
                      </p>
                    </div>
                    <div className="bg-blue-50 rounded-xl p-4">
                      <p className="text-blue-600 text-sm">应收金额</p>
                      <p className="text-xl font-bold text-blue-700 mt-1">
                        {formatAmount(detail.summary.expectedAmount)}
                      </p>
                    </div>
                    <div className="bg-emerald-50 rounded-xl p-4">
                      <p className="text-emerald-600 text-sm">已回款金额</p>
                      <p className="text-xl font-bold text-emerald-700 mt-1">
                        {formatAmount(detail.summary.collectedAmount)}
                      </p>
                    </div>
                    <div className="bg-amber-50 rounded-xl p-4">
                      <p className="text-amber-600 text-sm">回款率</p>
                      <p className="text-xl font-bold text-amber-700 mt-1">
                        {detail.summary.collectionRate}%
                      </p>
                    </div>
                  </div>

                  <div className="mb-6">
                    <h3 className="text-sm font-semibold text-slate-700 mb-3">回款进度</h3>
                    <div className="h-4 bg-slate-100 rounded-full overflow-hidden">
                      <div
                        className={cn(
                          "h-full rounded-full transition-all",
                          getRateBarColor(detail.summary.collectionRate)
                        )}
                        style={{ width: `${Math.min(detail.summary.collectionRate, 100)}%` }}
                      />
                    </div>
                    <div className="flex justify-between mt-2 text-sm text-slate-500">
                      <span>已回款 {formatAmount(detail.summary.collectedAmount)}</span>
                      <span>
                        待回款{" "}
                        {formatAmount(
                          Math.max(0, detail.summary.expectedAmount - detail.summary.collectedAmount)
                        )}
                      </span>
                    </div>
                  </div>

                  {detail.stageCollections && detail.stageCollections.length > 0 && (
                    <div className="mb-6">
                      <h3 className="text-sm font-semibold text-slate-700 mb-3">按阶段回款情况</h3>
                      <table className="w-full">
                        <thead className="bg-slate-50">
                          <tr>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">阶段</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">产值比例</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">计划回款</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">实际回款</th>
                            <th className="text-center py-3 px-4 text-sm font-medium text-slate-600">状态</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.stageCollections.map((s) => {
                            const label = getStatusLabel(s.status);
                            return (
                              <tr key={s.projectStageId} className="border-b border-slate-100">
                                <td className="py-3 px-4 text-sm text-slate-700">{s.stageName}</td>
                                <td className="py-3 px-4 text-sm text-slate-500">{s.stageOutput}%</td>
                                <td className="py-3 px-4 text-sm text-slate-700">
                                  {formatAmount(s.planAmount)}
                                </td>
                                <td className="py-3 px-4 text-sm font-medium text-emerald-600">
                                  {formatAmount(s.actualAmount)}
                                </td>
                                <td className="py-3 px-4 text-center">
                                  <Badge
                                    variant="secondary"
                                    className={cn("text-xs", statusStyles[label])}
                                  >
                                    {label}
                                  </Badge>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}

                  <div>
                    <h3 className="text-sm font-semibold text-slate-700 mb-3">回款记录明细</h3>
                    {detail.records.length > 0 ? (
                      <table className="w-full">
                        <thead className="bg-slate-50">
                          <tr>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">阶段</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">金额</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">日期</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">录入人</th>
                            <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">备注</th>
                            <th className="text-center py-3 px-4 text-sm font-medium text-slate-600">操作</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.records.map((r) => (
                            <tr key={r.collectionRecordId} className="border-b border-slate-100">
                              <td className="py-3 px-4 text-sm text-slate-700">{r.stageName || "-"}</td>
                              <td className="py-3 px-4 text-sm font-medium text-emerald-600">
                                {formatAmount(r.amount)}
                              </td>
                              <td className="py-3 px-4 text-sm text-slate-500">{formatDate(r.collectDate)}</td>
                              <td className="py-3 px-4 text-sm text-slate-500">{r.recordUserName || "-"}</td>
                              <td className="py-3 px-4 text-sm text-slate-500 max-w-[200px] truncate">
                                {r.remark || "-"}
                              </td>
                              <td className="py-3 px-4 text-center">
                                <button
                                  onClick={() => handleDeleteRecord(r.collectionRecordId)}
                                  className="text-rose-500 hover:text-rose-600"
                                  title="删除"
                                >
                                  <Trash2 className="w-4 h-4" />
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <div className="text-center py-8 text-slate-400">
                        <AlertCircle className="w-12 h-12 mx-auto mb-2" />
                        <p>暂无回款记录</p>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>

            <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
              <button
                onClick={() => setDetailOpen(false)}
                className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 录入弹窗 */}
      <AddCollectionDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        onSuccess={() => {
          fetchList();
          fetchStats();
        }}
      />
    </div>
  );
}

// ==================== 录入回款弹窗 ====================

function AddCollectionDialog({
  open,
  onOpenChange,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onSuccess: () => void;
}) {
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [projectId, setProjectId] = useState("");
  const [stageId, setStageId] = useState("");
  const [amount, setAmount] = useState("");
  const [collectDate, setCollectDate] = useState(
    new Date().toISOString().slice(0, 10)
  );
  const [remark, setRemark] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // 加载项目列表
  useEffect(() => {
    if (!open) return;
    async function load() {
      try {
        const res = await getMyProjects({ pageSize: 100 });
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setProjects(res.data.records ?? []);
        }
      } catch {
        /* 静默 */
      }
    }
    load();
  }, [open]);

  // 重置表单
  useEffect(() => {
    if (!open) {
      setProjectId("");
      setStageId("");
      setAmount("");
      setRemark("");
      setCollectDate(new Date().toISOString().slice(0, 10));
    }
  }, [open]);

  // 选择项目 → 加载阶段
  useEffect(() => {
    if (!projectId) {
      setStages([]);
      setStageId("");
      return;
    }
    async function load() {
      try {
        const res = await getProjectDetail(projectId);
        if (res.code === ResponseCode.SUCCESS && res.data?.projectStages) {
          setStages(res.data.projectStages);
        }
      } catch {
        /* 静默 */
      }
    }
    load();
  }, [projectId]);

  const handleSubmit = async () => {
    if (!projectId) {
      toast.error("请选择项目");
      return;
    }
    const amt = Number(amount);
    if (!amt || amt <= 0) {
      toast.error("请输入回款金额");
      return;
    }
    if (!collectDate) {
      toast.error("请选择回款日期");
      return;
    }

    setSubmitting(true);
    try {
      const res = await createCollectionRecord({
        projectId,
        projectStageId: stageId || undefined,
        amount: amt,
        collectDate,
        remark: remark || undefined,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("录入成功");
        onOpenChange(false);
        onSuccess();
      } else {
        toast.error(res.msg || "提交失败");
      }
    } catch {
      toast.error("网络异常");
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-lg overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-xl font-bold text-slate-800">录入回款</h2>
          <button
            onClick={() => onOpenChange(false)}
            className="text-slate-400 hover:text-slate-600"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              选择项目 <span className="text-rose-500">*</span>
            </label>
            <select
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">请选择项目</option>
              {projects.map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName} ({p.projectCode})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">回款阶段</label>
            <select
              value={stageId}
              onChange={(e) => setStageId(e.target.value)}
              disabled={!projectId}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-slate-50"
            >
              <option value="">{projectId ? "请选择阶段（可选）" : "请先选择项目"}</option>
              {stages.map((s) => (
                <option key={s.projectStageId} value={s.projectStageId}>
                  {s.stageName}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                回款金额 <span className="text-rose-500">*</span>
              </label>
              <input
                type="number"
                min="1"
                placeholder="请输入金额（元）"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                回款日期 <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                value={collectDate}
                onChange={(e) => setCollectDate(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">备注</label>
            <textarea
              rows={2}
              placeholder="请输入备注信息（选填）"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
        </div>

        <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
          <button
            onClick={() => onOpenChange(false)}
            disabled={submitting}
            className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
          >
            取消
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors flex items-center gap-2 disabled:opacity-60"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            确认提交
          </button>
        </div>
      </div>
    </div>
  );
}
