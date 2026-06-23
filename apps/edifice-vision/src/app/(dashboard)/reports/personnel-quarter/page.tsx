"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Users, TrendingUp, Wallet, TrendingDown, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  getPersonnelQuarterSummary,
  type PersonnelQuarterSummaryVo,
} from "@/services/report";
import {
  currentQuarter,
  generateQuarterOptions,
} from "@/types/output-value";

function formatAmount(amount: number): string {
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(2)}万`;
  return `¥${(amount || 0).toLocaleString()}`;
}

export default function PersonnelQuarterSummaryPage() {
  const [quarter, setQuarter] = useState<string>(currentQuarter());
  const [rows, setRows] = useState<PersonnelQuarterSummaryVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const quarterOptions = useMemo(() => generateQuarterOptions(8), []);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getPersonnelQuarterSummary(quarter);
      if (res.code === ResponseCode.SUCCESS) {
        setRows(res.data ?? []);
      }
    } finally {
      setLoading(false);
    }
  }, [quarter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const filtered = rows.filter((r) =>
    !keyword ? true : r.realName?.includes(keyword),
  );

  const totalAlloc = filtered.reduce((s, r) => s + (r.allocAmount || 0), 0);
  const totalActual = filtered.reduce((s, r) => s + (r.completionAmount || 0), 0);
  const totalDownGrade = totalAlloc - totalActual;

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            人员分配汇总表（季度）
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            汇总该季度所有已确认产值分配单，按用户维度展示应得 / 实得金额。
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
          <input
            type="text"
            placeholder="按姓名筛选..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white w-56"
          />
        </div>
      </div>

      {loading ? (
        <TablePageSkeleton columns={6} rows={6} />
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <Summary
              icon={<Users className="w-5 h-5" />}
              label="参与人数"
              value={filtered.length}
              tone="blue"
            />
            <Summary
              icon={<TrendingUp className="w-5 h-5" />}
              label="应得合计"
              value={formatAmount(totalAlloc)}
              tone="emerald"
            />
            <Summary
              icon={<Wallet className="w-5 h-5" />}
              label="实得合计"
              value={formatAmount(totalActual)}
              tone="amber"
            />
            <Summary
              icon={<TrendingDown className="w-5 h-5" />}
              label="降档差额"
              value={formatAmount(totalDownGrade)}
              tone="rose"
            />
          </div>

          <div className="glass-card rounded-2xl overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
                <tr>
                  <th className="text-left py-3 px-4 font-semibold w-12">#</th>
                  <th className="text-left py-3 px-4 font-semibold">姓名</th>
                  <th className="text-center py-3 px-4 font-semibold">参与项目</th>
                  <th className="text-right py-3 px-4 font-semibold">应得金额</th>
                  <th className="text-right py-3 px-4 font-semibold">实得金额</th>
                  <th className="text-right py-3 px-4 font-semibold">降档差额</th>
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-16 text-center text-slate-400">
                      {loading ? (
                        <Loader2 className="w-4 h-4 animate-spin inline" />
                      ) : (
                        "暂无数据"
                      )}
                    </td>
                  </tr>
                ) : (
                  filtered.map((r, i) => {
                    const diff = (r.allocAmount || 0) - (r.completionAmount || 0);
                    return (
                      <tr
                        key={r.userId}
                        className="border-t border-slate-100 hover:bg-slate-50"
                      >
                        <td className="py-3 px-4 text-slate-500">{i + 1}</td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-2">
                            <div className="w-7 h-7 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                              {(r.realName || "?")[0]}
                            </div>
                            <span className="text-slate-700 font-medium">
                              {r.realName || "-"}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-4 text-center text-slate-600">
                          {r.projectCount}
                        </td>
                        <td className="py-3 px-4 text-right text-slate-700">
                          {formatAmount(r.allocAmount)}
                        </td>
                        <td className="py-3 px-4 text-right font-semibold text-emerald-600">
                          {formatAmount(r.completionAmount)}
                        </td>
                        <td
                          className={cn(
                            "py-3 px-4 text-right",
                            diff > 0.01 ? "text-amber-600" : "text-slate-400",
                          )}
                        >
                          {formatAmount(diff)}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          <p className="text-xs text-slate-400">
            说明：v0.4 起所有非员工实得（降档差额 / 离职兜底 / 公司主体 60%）统一归公司账，不再单独区分。
          </p>
        </>
      )}
    </div>
  );
}

function Summary({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  tone: "blue" | "emerald" | "amber" | "rose";
}) {
  const toneMap: Record<string, string> = {
    blue: "bg-blue-100 text-blue-600",
    emerald: "bg-emerald-100 text-emerald-600",
    amber: "bg-amber-100 text-amber-600",
    rose: "bg-rose-100 text-rose-600",
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
