"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  CheckCircle2,
  Clock,
  Loader2,
  RefreshCcw,
  Sparkles,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  generatePerformanceRestore,
  getPerformanceRestoreList,
  markPerformanceRestored,
  markQuarterRestored,
  deletePerformanceRestore,
} from "@/services/performance-restore";
import type { PerformanceRestoreVo } from "@/types/performance";
import { RESTORE_STATUS_MAP } from "@/types/performance";
import {
  currentQuarter,
  generateQuarterOptions,
} from "@/types/output-value";

function formatAmount(amount: number): string {
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(2)}万`;
  return `¥${(amount || 0).toLocaleString()}`;
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.replace("T", " ").slice(0, 16);
}

type StatusTab = "all" | "pending" | "restored";

const statusFilterMap: Record<StatusTab, number | undefined> = {
  all: undefined,
  pending: 0,
  restored: 1,
};

export default function PerformanceRestorePage() {
  const [quarter, setQuarter] = useState<string>(currentQuarter());
  const [tab, setTab] = useState<StatusTab>("all");
  const [rows, setRows] = useState<PerformanceRestoreVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionId, setActionId] = useState<string | null>(null);
  const [batchLoading, setBatchLoading] = useState(false);
  const quarterOptions = useMemo(() => generateQuarterOptions(8), []);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getPerformanceRestoreList({
        quarter,
        status: statusFilterMap[tab],
      });
      if (res.code === ResponseCode.SUCCESS) {
        setRows(res.data ?? []);
      }
    } finally {
      setLoading(false);
    }
  }, [quarter, tab]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleGenerate = async () => {
    setBatchLoading(true);
    try {
      const res = await generatePerformanceRestore(quarter);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        toast.success(
          `生成 ${res.data.generated} 条，已存在跳过 ${res.data.skipped} 条，金额合计 ${formatAmount(
            res.data.totalAmount,
          )}`,
        );
        fetchData();
      }
    } finally {
      setBatchLoading(false);
    }
  };

  const handleMark = async (id: string) => {
    setActionId(id);
    try {
      const res = await markPerformanceRestored(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已标记为已还原");
        fetchData();
      }
    } finally {
      setActionId(null);
    }
  };

  const handleMarkQuarter = async () => {
    if (!confirm(`确认将 ${quarter} 所有待还原记录一键标记为已还原？`)) return;
    setBatchLoading(true);
    try {
      const res = await markQuarterRestored(quarter);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        toast.success(`已标记 ${res.data.restored} 条`);
        fetchData();
      }
    } finally {
      setBatchLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("确认删除此条待还原记录？")) return;
    setActionId(id);
    try {
      const res = await deletePerformanceRestore(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已删除");
        fetchData();
      }
    } finally {
      setActionId(null);
    }
  };

  const total = rows.reduce((s, r) => s + (r.restoreAmount || 0), 0);
  const pending = rows.filter((r) => r.status === 0);
  const restored = rows.filter((r) => r.status === 1);

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">绩效还原</h1>
          <p className="text-slate-500 text-sm mt-1">
            按季度生成还原记录，财务按条或按季度标记已还原。
          </p>
        </div>
        <div className="flex items-center gap-3">
          <select
            className="px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
            value={quarter}
            onChange={(e) => setQuarter(e.target.value)}
          >
            {quarterOptions.map((q) => (
              <option key={q} value={q}>
                {q}
              </option>
            ))}
          </select>
          <Button variant="outline" onClick={handleGenerate} disabled={batchLoading}>
            {batchLoading ? (
              <Loader2 className="w-4 h-4 animate-spin mr-1" />
            ) : (
              <Sparkles className="w-4 h-4 mr-1" />
            )}
            按季度生成
          </Button>
          <Button
            className="bg-emerald-600 hover:bg-emerald-700 text-white"
            onClick={handleMarkQuarter}
            disabled={batchLoading || pending.length === 0}
          >
            {batchLoading ? (
              <Loader2 className="w-4 h-4 animate-spin mr-1" />
            ) : (
              <CheckCircle2 className="w-4 h-4 mr-1" />
            )}
            当季全部还完
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard
          icon={<RefreshCcw className="w-5 h-5" />}
          label={`${quarter} 记录数`}
          value={rows.length}
          tone="blue"
        />
        <StatCard
          icon={<Clock className="w-5 h-5" />}
          label="待还原金额"
          value={formatAmount(pending.reduce((s, r) => s + (r.restoreAmount || 0), 0))}
          tone="amber"
        />
        <StatCard
          icon={<CheckCircle2 className="w-5 h-5" />}
          label="已还原金额"
          value={formatAmount(restored.reduce((s, r) => s + (r.restoreAmount || 0), 0))}
          tone="emerald"
        />
      </div>

      <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100 w-fit">
        {([
          { key: "all", label: "全部", count: rows.length },
          { key: "pending", label: "待还原", count: pending.length },
          { key: "restored", label: "已还原", count: restored.length },
        ] as const).map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              "px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
              tab === t.key
                ? "bg-blue-600 text-white shadow-sm"
                : "text-slate-500 hover:text-slate-700",
            )}
          >
            {t.label}
            <span
              className={cn(
                "text-xs px-1.5 py-0.5 rounded-full",
                tab === t.key ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-500",
              )}
            >
              {t.count}
            </span>
          </button>
        ))}
      </div>

      {loading ? (
        <TablePageSkeleton columns={6} rows={6} />
      ) : (
        <div className="glass-card rounded-2xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
              <tr>
                <th className="text-left py-3 px-4 font-semibold">姓名</th>
                <th className="text-left py-3 px-4 font-semibold">项目</th>
                <th className="text-center py-3 px-4 font-semibold">季度</th>
                <th className="text-right py-3 px-4 font-semibold">金额</th>
                <th className="text-center py-3 px-4 font-semibold">状态</th>
                <th className="text-left py-3 px-4 font-semibold">还原时间 / 操作人</th>
                <th className="text-right py-3 px-4 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-16 text-center text-slate-400">
                    暂无记录，点击"按季度生成"可从已确认产值中提取
                  </td>
                </tr>
              ) : (
                rows.map((r) => (
                  <tr key={r.restoreId} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className="py-3 px-4 text-slate-700 font-medium">{r.realName}</td>
                    <td className="py-3 px-4 text-slate-500">{r.projectName ?? "-"}</td>
                    <td className="py-3 px-4 text-center text-slate-600">{r.quarter}</td>
                    <td className="py-3 px-4 text-right font-semibold text-slate-800">
                      {formatAmount(r.restoreAmount)}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={cn(
                          "text-xs px-2 py-0.5 rounded-full font-medium",
                          r.status === 1
                            ? "bg-emerald-100 text-emerald-600"
                            : "bg-amber-100 text-amber-600",
                        )}
                      >
                        {RESTORE_STATUS_MAP[r.status] ?? "-"}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-500 text-xs">
                      {r.status === 1 ? (
                        <>
                          {formatDate(r.restoredTime)}
                          {r.operatorName && (
                            <span className="ml-2 text-slate-400">/ {r.operatorName}</span>
                          )}
                        </>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="py-3 px-4 text-right">
                      {r.status === 0 ? (
                        <div className="flex justify-end gap-2">
                          <Button
                            size="sm"
                            className="bg-emerald-600 hover:bg-emerald-700 text-white"
                            onClick={() => handleMark(r.restoreId)}
                            disabled={actionId === r.restoreId}
                          >
                            {actionId === r.restoreId && (
                              <Loader2 className="w-4 h-4 animate-spin mr-1" />
                            )}
                            标记已还原
                          </Button>
                          <button
                            type="button"
                            onClick={() => handleDelete(r.restoreId)}
                            className="p-2 text-slate-400 hover:text-rose-500"
                            disabled={actionId === r.restoreId}
                            title="删除"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <p className="text-xs text-slate-400">
        当前展示共 {rows.length} 条，金额合计 {formatAmount(total)}
      </p>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  tone: "blue" | "amber" | "emerald";
}) {
  const toneMap: Record<string, string> = {
    blue: "bg-blue-100 text-blue-600",
    amber: "bg-amber-100 text-amber-600",
    emerald: "bg-emerald-100 text-emerald-600",
  };
  return (
    <div className="glass-card p-4 rounded-xl">
      <div className="flex items-center gap-3">
        <div className={cn("p-2 rounded-lg", toneMap[tone])}>{icon}</div>
        <div>
          <p className="text-xs text-slate-500">{label}</p>
          <p className="text-xl font-bold text-slate-800">{value}</p>
        </div>
      </div>
    </div>
  );
}
