"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Layers,
  FileText,
  TrendingUp,
  PieChart,
  BarChart3,
  Wallet,
  Users,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import {
  getReportOverview,
  getProjectStats,
  getCategoryStats,
  getPersonnelRanking,
  getPersonnelQuarterSummary,
} from "@/services/report";
import { ResponseCode } from "@/types/api";
import { currentQuarter, generateQuarterOptions } from "@/types/output-value";
import type { PersonnelQuarterSummaryVo } from "@/services/report";

type ReportKey = "project" | "category" | "personnel";

export default function StatisticsPage() {
  const [activeReport, setActiveReport] = useState<ReportKey>("project");
  const [loading, setLoading] = useState(true);
  const [quarter, setQuarter] = useState(currentQuarter());
  const quarterOptions = generateQuarterOptions(8);
  const [overview, setOverview] = useState<{ totalProjects: number; totalContractAmount: number; completedOutputValue: number; totalOutputValue: number } | null>(null);
  const [projectData, setProjectData] = useState<{ projectName: string; category: string; contractAmount: number; completedAmount: number; pendingValue: number }[]>([]);
  const [categoryData, setCategoryData] = useState<{ category: string; name: string; count: number; contractTotal: number; completedTotal: number; percentage: number }[]>([]);
  const [personnelData, setPersonnelData] = useState<PersonnelQuarterSummaryVo[]>([]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      // 概览和项目/分类统计不受季度影响
      const [ovRes, projRes, catRes] = await Promise.all([
        getReportOverview(),
        getProjectStats(),
        getCategoryStats(),
      ]);
      if (ovRes.code === ResponseCode.SUCCESS) setOverview(ovRes.data);
      if (projRes.code === ResponseCode.SUCCESS) setProjectData(projRes.data ?? []);
      if (catRes.code === ResponseCode.SUCCESS) setCategoryData(catRes.data ?? []);

      // 人员排名按季度查询
      const persRes = await getPersonnelQuarterSummary(quarter);
      if (persRes.code === ResponseCode.SUCCESS) {
        const sorted = (persRes.data ?? []).sort((a, b) => (b.completionAmount ?? 0) - (a.completionAmount ?? 0));
        setPersonnelData(sorted);
      }
    } catch { /* 静默 */ }
    finally { setLoading(false); }
  }, [quarter]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const formatAmount = (v: number) => v >= 10000 ? `¥${(v / 10000).toFixed(1)}万` : `¥${v.toLocaleString()}`;

  const reports: { key: ReportKey; label: string; icon: typeof BarChart3 }[] = [
    { key: "project", label: "项目产值", icon: BarChart3 },
    { key: "category", label: "分类统计", icon: PieChart },
    { key: "personnel", label: "人员排名", icon: Users },
  ];

  const categoryColors: Record<string, string> = {
    "A类": "bg-blue-500", "B类": "bg-emerald-500", "C类": "bg-amber-500", "D类": "bg-purple-500", "E类": "bg-rose-500",
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">统计报表</h1>
          <p className="text-slate-500 text-sm mt-1">全面分析项目产值、人员绩效等核心数据。</p>
        </div>
        <select
          value={quarter}
          onChange={(e) => setQuarter(e.target.value)}
          className="px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {quarterOptions.map((q) => (
            <option key={q} value={q}>{q}</option>
          ))}
        </select>
      </div>

      {loading && <TablePageSkeleton columns={4} rows={5} />}

      {!loading && (
        <>
          {/* Overview Stats */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[
              { label: "项目总数", value: String(overview?.totalProjects ?? 0), icon: Layers, color: "text-blue-600", bg: "bg-blue-50" },
              { label: "合同总额", value: formatAmount(overview?.totalContractAmount ?? 0), icon: FileText, color: "text-purple-600", bg: "bg-purple-50" },
              { label: "已完成产值", value: formatAmount(overview?.completedOutputValue ?? 0), icon: TrendingUp, color: "text-emerald-600", bg: "bg-emerald-50" },
              { label: "产值总额", value: formatAmount(overview?.totalOutputValue ?? 0), icon: Wallet, color: "text-amber-600", bg: "bg-amber-50" },
            ].map((stat) => (
              <div key={stat.label} className="glass-card p-4 rounded-xl">
                <div className="flex items-center gap-3">
                  <div className={cn("p-2 rounded-lg", stat.bg, stat.color)}>
                    <stat.icon className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs text-slate-500">{stat.label}</p>
                    <p className="text-xl font-bold text-slate-800">{stat.value}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Report Tabs */}
          <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100 w-fit">
            {reports.map((r) => (
              <button key={r.key} onClick={() => setActiveReport(r.key)}
                className={cn("px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
                  activeReport === r.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700")}>
                <r.icon className="w-4 h-4" /> {r.label}
              </button>
            ))}
          </div>

          {/* Project Stats */}
          {activeReport === "project" && (
            <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50/50">
                  <tr className="text-slate-500 text-xs uppercase tracking-wider">
                    <th className="text-left py-4 px-6 font-semibold">项目名称</th>
                    <th className="text-left py-4 px-4 font-semibold">分类</th>
                    <th className="text-right py-4 px-4 font-semibold">合同金额</th>
                    <th className="text-right py-4 px-4 font-semibold">已完成产值</th>
                    <th className="text-right py-4 px-4 font-semibold">待处理产值</th>
                    <th className="text-right py-4 px-6 font-semibold">完成率</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {projectData.map((p, idx) => {
                    const rate = p.contractAmount > 0 ? ((p.completedAmount / p.contractAmount) * 100).toFixed(1) : "0.0";
                    return (
                      <tr key={idx} className="hover:bg-slate-50/50">
                        <td className="py-3 px-6 font-medium text-slate-800">{p.projectName}</td>
                        <td className="py-3 px-4"><span className={cn("text-xs px-2 py-0.5 rounded-md font-medium",
                          p.category?.startsWith("A") ? "bg-blue-50 text-blue-600" : p.category?.startsWith("B") ? "bg-emerald-50 text-emerald-600"
                          : p.category?.startsWith("C") ? "bg-amber-50 text-amber-600" : "bg-slate-50 text-slate-600")}>{p.category}</span></td>
                        <td className="py-3 px-4 text-right text-slate-700">{formatAmount(p.contractAmount)}</td>
                        <td className="py-3 px-4 text-right text-emerald-600 font-medium">{formatAmount(p.completedAmount)}</td>
                        <td className="py-3 px-4 text-right text-amber-600">{formatAmount(p.pendingValue)}</td>
                        <td className="py-3 px-6 text-right font-semibold text-slate-800">{rate}%</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {projectData.length === 0 && (
                <div className="py-12 text-center text-sm text-slate-400">暂无数据</div>
              )}
            </div>
          )}

          {/* Category Stats */}
          {activeReport === "category" && (
            <div className="glass-card rounded-2xl p-6 shadow-sm">
              <div className="space-y-4">
                {categoryData.map((c) => (
                  <div key={c.category} className="flex flex-wrap items-center gap-3">
                    <div className="w-16 text-sm font-medium text-slate-700">{c.category}</div>
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-1">
                        <div className="flex-1 bg-slate-100 rounded-full h-4 overflow-hidden">
                          <div className={cn("h-full rounded-full", categoryColors[c.category] ?? "bg-slate-400")}
                            style={{ width: `${c.percentage}%` }} />
                        </div>
                        <span className="text-sm font-semibold text-slate-700 w-12 text-right">{c.percentage}%</span>
                      </div>
                      <div className="flex gap-4 text-xs text-slate-400">
                        <span>{c.name}</span>
                        <span>{c.count} 个项目</span>
                        <span>合同额 {formatAmount(c.contractTotal)}</span>
                        <span>已完成 {formatAmount(c.completedTotal)}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              {categoryData.length === 0 && (
                <div className="py-12 text-center text-sm text-slate-400">暂无数据</div>
              )}
            </div>
          )}

          {/* Personnel Ranking */}
          {activeReport === "personnel" && (
            <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50/50">
                  <tr className="text-slate-500 text-xs uppercase tracking-wider">
                    <th className="text-center py-4 px-4 font-semibold w-16">排名</th>
                    <th className="text-left py-4 px-4 font-semibold">姓名</th>
                    <th className="text-center py-4 px-4 font-semibold">参与项目</th>
                    <th className="text-right py-4 px-4 font-semibold">应得金额</th>
                    <th className="text-right py-4 px-6 font-semibold">实得金额</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {personnelData.map((p, idx) => (
                    <tr key={p.userId} className="hover:bg-slate-50/50">
                      <td className="py-3 px-4 text-center">
                        <span className={cn("w-6 h-6 rounded-full inline-flex items-center justify-center text-xs font-bold",
                          idx === 0 ? "bg-amber-100 text-amber-700" : idx === 1 ? "bg-slate-200 text-slate-600"
                          : idx === 2 ? "bg-orange-100 text-orange-700" : "bg-slate-50 text-slate-500")}>
                          {idx + 1}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-blue-100 flex items-center justify-center text-xs font-medium text-blue-600">
                            {p.realName?.[0] ?? "?"}
                          </div>
                          <span className="font-medium text-slate-800">{p.realName}</span>
                        </div>
                      </td>
                      <td className="py-3 px-4 text-center text-slate-600">{p.projectCount}</td>
                      <td className="py-3 px-4 text-right text-slate-600">{formatAmount(p.allocAmount ?? 0)}</td>
                      <td className="py-3 px-6 text-right font-semibold text-slate-800">{formatAmount(p.completionAmount ?? 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {personnelData.length === 0 && (
                <div className="py-12 text-center text-sm text-slate-400">暂无数据</div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
