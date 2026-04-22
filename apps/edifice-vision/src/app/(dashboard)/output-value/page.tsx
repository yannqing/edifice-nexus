"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Clock,
  CheckCircle,
  Banknote,
  FileText,
  ChevronDown,
  ChevronUp,
  Loader2,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import {
  getOutputValueList,
  getOutputValueStats,
  confirmOutputValue,
  approveOutputValue,
  payOutputValue,
} from "@/services/output-value";
import { ResponseCode } from "@/types/api";
import type { OutputValueVo, OutputValueStats } from "@/types/output-value";
import { OUTPUT_VALUE_STATUS_MAP, WORK_TYPE_LABELS } from "@/types/output-value";

type TabKey = "all" | "pending" | "review" | "approved" | "paid";

const statusFilterMap: Record<TabKey, number | undefined> = {
  all: undefined,
  pending: 0,
  review: 1,
  approved: 2,
  paid: 3,
};

const statusStyles: Record<string, string> = {
  待确认: "bg-slate-100 text-slate-600",
  待审核: "bg-amber-100 text-amber-600",
  已审批: "bg-blue-100 text-blue-600",
  已发放: "bg-emerald-100 text-emerald-600",
};

const workTypeStyles: Record<number, string> = {
  0: "bg-blue-100 text-blue-600",
  1: "bg-emerald-100 text-emerald-600",
  2: "bg-amber-100 text-amber-600",
};

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.replace("T", " ").slice(0, 16);
}

function formatAmount(amount: number): string {
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(2)}万`;
  return `¥${amount.toLocaleString()}`;
}

export default function OutputValuePage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [items, setItems] = useState<OutputValueVo[]>([]);
  const [stats, setStats] = useState<OutputValueStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [listRes, statsRes] = await Promise.all([
        getOutputValueList(statusFilterMap[activeTab]),
        getOutputValueStats(),
      ]);
      if (listRes.code === ResponseCode.SUCCESS) {
        setItems(listRes.data ?? []);
      }
      if (statsRes.code === ResponseCode.SUCCESS) {
        setStats(statsRes.data ?? null);
      }
    } catch { /* 静默 */ }
    finally { setLoading(false); }
  }, [activeTab]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const filtered = items.filter((item) => {
    if (!searchText) return true;
    return (item.projectName ?? "").includes(searchText) || (item.projectCode ?? "").includes(searchText);
  });

  const handleAction = async (id: string, action: "confirm" | "approve" | "pay") => {
    setActionLoading(id);
    try {
      const actionMap = { confirm: confirmOutputValue, approve: approveOutputValue, pay: payOutputValue };
      const labelMap = { confirm: "确认成功", approve: "审批通过", pay: "发放成功" };
      const res = await actionMap[action](id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(labelMap[action]);
        fetchData();
      } else {
        toast.error(res.msg || "操作失败");
      }
    } catch { toast.error("操作失败"); }
    finally { setActionLoading(null); }
  };

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: items.length },
    { key: "pending", label: "待确认", count: items.filter((i) => i.status === 0).length },
    { key: "review", label: "待审核", count: items.filter((i) => i.status === 1).length },
    { key: "approved", label: "已审批", count: items.filter((i) => i.status === 2).length },
    { key: "paid", label: "已发放", count: items.filter((i) => i.status === 3).length },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">产值分配</h1>
          <p className="text-slate-500 text-sm mt-1">管理项目阶段产值的分配、审批与发放。</p>
        </div>
      </div>

      {loading && <TablePageSkeleton columns={4} rows={4} />}

      {!loading && (
        <>
          {/* Stats */}
          <div className="grid grid-cols-4 gap-4">
            <StatCard icon={<Clock className="w-5 h-5" />} label="待处理" value={stats?.pendingCount ?? 0} color="amber" />
            <StatCard icon={<CheckCircle className="w-5 h-5" />} label="已审批待发放" value={stats?.approvedCount ?? 0} color="blue" />
            <StatCard icon={<Banknote className="w-5 h-5" />} label="已发放产值" value={formatAmount(stats?.paidAmount ?? 0)} color="emerald" />
            <StatCard icon={<FileText className="w-5 h-5" />} label="产值总额" value={formatAmount(stats?.totalAmount ?? 0)} color="slate" />
          </div>

          {/* Tabs + Search */}
          <div className="flex items-center gap-4">
            <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
              {tabs.map((item) => (
                <button key={item.key} onClick={() => setActiveTab(item.key)}
                  className={cn("px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
                    activeTab === item.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700")}>
                  {item.label}
                  <span className={cn("text-xs px-1.5 py-0.5 rounded-full",
                    activeTab === item.key ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-500")}>
                    {item.count}
                  </span>
                </button>
              ))}
            </div>
            <div className="flex-1" />
            <input type="text" placeholder="搜索项目名称或编码..." value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              className="pl-4 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>

          {/* List */}
          {filtered.length > 0 ? (
            <div className="space-y-4">
              {filtered.map((item) => {
                const statusLabel = OUTPUT_VALUE_STATUS_MAP[item.status] ?? "未知";
                const isExpanded = expandedId === item.outputValueId;

                return (
                  <div key={item.outputValueId} className="glass-card rounded-2xl shadow-sm overflow-hidden">
                    <button onClick={() => setExpandedId(isExpanded ? null : item.outputValueId)}
                      className="w-full flex items-center gap-4 p-5 text-left hover:bg-slate-50/50 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm font-semibold text-slate-800">{item.projectName}</span>
                          <span className="text-xs text-slate-400">{item.projectCode}</span>
                        </div>
                        <div className="flex items-center gap-3 text-xs text-slate-500">
                          {item.projectTypeName && <span>{item.projectTypeName}</span>}
                          <span>·</span>
                          <span>{item.stageName}</span>
                          {item.stageOutput > 0 && (
                            <><span>·</span><span>产值比例 {item.stageOutput}%</span></>
                          )}
                        </div>
                      </div>
                      <div className="text-right mr-4">
                        <p className="text-lg font-bold text-slate-800">{formatAmount(item.totalAmount)}</p>
                      </div>
                      <Badge variant="secondary" className={cn("text-xs font-medium", statusStyles[statusLabel] ?? "")}>
                        {statusLabel}
                      </Badge>
                      {isExpanded ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
                    </button>

                    {isExpanded && (
                      <div className="px-5 pb-5 border-t border-slate-100">
                        <table className="w-full mt-4">
                          <thead>
                            <tr className="text-xs text-slate-400 uppercase tracking-wider">
                              <th className="text-left py-2 font-semibold">分配人员</th>
                              <th className="text-left py-2 font-semibold">角色</th>
                              <th className="text-left py-2 font-semibold">工作类型</th>
                              <th className="text-center py-2 font-semibold">比例</th>
                              <th className="text-right py-2 font-semibold">金额</th>
                            </tr>
                          </thead>
                          <tbody className="text-sm">
                            {item.distributions.map((d) => (
                              <tr key={d.distributionId} className="border-t border-slate-50">
                                <td className="py-3">
                                  <div className="flex items-center gap-2">
                                    <div className="w-6 h-6 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                                      {(d.userName ?? "?")[0]}
                                    </div>
                                    <span className="text-slate-700">{d.userName || "-"}</span>
                                  </div>
                                </td>
                                <td className="py-3 text-slate-500">{d.userRole}</td>
                                <td className="py-3">
                                  <span className={cn("text-xs px-2 py-0.5 rounded-full font-medium", workTypeStyles[d.workType] ?? "")}>
                                    {WORK_TYPE_LABELS[d.workType] ?? "-"}
                                  </span>
                                </td>
                                <td className="py-3 text-center text-slate-600">{d.ratio}%</td>
                                <td className="py-3 text-right font-semibold text-slate-800">{formatAmount(d.amount)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>

                        <div className="flex gap-6 mt-4 pt-4 border-t border-slate-100 text-xs text-slate-400">
                          <span>提交时间：{formatDate(item.submitTime)}</span>
                          {item.approvedTime && <span>审批时间：{formatDate(item.approvedTime)}</span>}
                          {item.paidTime && <span>发放时间：{formatDate(item.paidTime)}</span>}
                        </div>

                        <div className="flex justify-end gap-3 mt-4">
                          {item.status === 0 && (
                            <Button className="bg-blue-600 hover:bg-blue-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => handleAction(item.outputValueId, "confirm")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              确认分配
                            </Button>
                          )}
                          {item.status === 1 && (
                            <Button className="bg-emerald-600 hover:bg-emerald-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => handleAction(item.outputValueId, "approve")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              审批通过
                            </Button>
                          )}
                          {item.status === 2 && (
                            <Button className="bg-blue-600 hover:bg-blue-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => handleAction(item.outputValueId, "pay")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              发放产值
                            </Button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="glass-card rounded-2xl py-16 text-center">
              <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <Banknote className="w-8 h-8 text-slate-400" />
              </div>
              <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无产值分配记录</h3>
              <p className="text-sm text-slate-500">当前筛选条件下没有找到数据</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatCard({ icon, label, value, color }: {
  icon: React.ReactNode; label: string; value: string | number; color: string;
}) {
  const colorMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-600", amber: "bg-amber-100 text-amber-600",
    blue: "bg-blue-100 text-blue-600", emerald: "bg-emerald-100 text-emerald-600",
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
