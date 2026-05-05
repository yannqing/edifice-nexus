"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Briefcase,
  Clock,
  Banknote,
  TrendingUp,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { CardPageSkeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/store/auth-context";
import { getMyPerformance, getMyProjectDetails, getMyPayments } from "@/services/report";
import { ResponseCode } from "@/types/api";
import { PROJECT_STATUS_MAP } from "@/types/project";
import { OUTPUT_VALUE_STATUS_MAP } from "@/types/output-value";

type TabKey = "overview" | "projects" | "payments";

const statusStyles: Record<string, string> = {
  未开始: "bg-slate-100 text-slate-500",
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
};

const paymentStatusStyles: Record<string, string> = {
  待确认: "bg-slate-100 text-slate-600",
  待审核: "bg-amber-100 text-amber-600",
  已审批: "bg-blue-100 text-blue-600",
  已发放: "bg-emerald-100 text-emerald-600",
};

function formatAmount(v: number) {
  return v >= 10000 ? `¥${(v / 10000).toFixed(1)}万` : `¥${v.toLocaleString()}`;
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.replace("T", " ").slice(0, 10);
}

interface PerformanceData {
  projectCount: number;
  totalHours: number;
  managementHours: number;
  basicHours: number;
  intellectualHours: number;
  paidOutputValue: number;
  totalOutputValue: number;
}

interface ProjectDetail {
  projectId: string;
  projectName: string;
  projectCode: string;
  projectStatus: number;
  category: string;
  role: string;
  totalHours: number;
  outputValue: number;
}

interface PaymentRecord {
  distributionId: string;
  projectName: string;
  stageName: string;
  amount: number;
  status: number;
  paidTime: string | null;
}

export default function PerformancePage() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [loading, setLoading] = useState(true);
  const [perf, setPerf] = useState<PerformanceData | null>(null);
  const [projects, setProjects] = useState<ProjectDetail[]>([]);
  const [payments, setPayments] = useState<PaymentRecord[]>([]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [perfRes, projRes, payRes] = await Promise.all([
        getMyPerformance(),
        getMyProjectDetails(),
        getMyPayments(),
      ]);
      if (perfRes.code === ResponseCode.SUCCESS) setPerf(perfRes.data);
      if (projRes.code === ResponseCode.SUCCESS) setProjects(projRes.data ?? []);
      if (payRes.code === ResponseCode.SUCCESS) setPayments(payRes.data ?? []);
    } catch { /* 静默 */ }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const tabs: { key: TabKey; label: string }[] = [
    { key: "overview", label: "总览" },
    { key: "projects", label: "参与项目" },
    { key: "payments", label: "产值发放" },
  ];

  // 工作类型分布
  const workTypeData = perf ? [
    { type: "管理工作", hours: perf.managementHours, color: "bg-blue-500", ratio: perf.totalHours > 0 ? ((perf.managementHours / perf.totalHours) * 100).toFixed(1) : "0" },
    { type: "基础工作", hours: perf.basicHours, color: "bg-emerald-500", ratio: perf.totalHours > 0 ? ((perf.basicHours / perf.totalHours) * 100).toFixed(1) : "0" },
    { type: "智励工作", hours: perf.intellectualHours, color: "bg-amber-500", ratio: perf.totalHours > 0 ? ((perf.intellectualHours / perf.totalHours) * 100).toFixed(1) : "0" },
  ] : [];

  return (
    <div className="p-4 md:p-8 space-y-6">
      {/* Header with user info */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex flex-wrap items-center gap-3">
          <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center text-2xl font-bold text-blue-600">
            {(user?.realName ?? user?.username ?? "U").charAt(0)}
          </div>
          <div className="flex-1">
            <h1 className="text-2xl font-bold text-slate-900">{user?.realName || user?.username || "用户"}</h1>
            <p className="text-slate-500 text-sm mt-0.5">个人绩效总览</p>
          </div>
          <div className="text-right">
            <p className="text-xs text-slate-400">累计产值</p>
            <p className="text-2xl font-bold text-blue-600">{formatAmount(perf?.totalOutputValue ?? 0)}</p>
          </div>
        </div>
      </div>

      {loading && <CardPageSkeleton cards={4} />}

      {!loading && (
        <>
          {/* Stats Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard icon={<Banknote className="w-5 h-5" />} label="已发放产值" value={formatAmount(perf?.paidOutputValue ?? 0)} color="emerald" />
            <StatCard icon={<Briefcase className="w-5 h-5" />} label="参与项目" value={`${perf?.projectCount ?? 0} 个`} color="blue" />
            <StatCard icon={<Clock className="w-5 h-5" />} label="累计工时" value={`${perf?.totalHours ?? 0}h`} color="amber" />
            <StatCard icon={<TrendingUp className="w-5 h-5" />} label="待发放产值" value={formatAmount((perf?.totalOutputValue ?? 0) - (perf?.paidOutputValue ?? 0))} color="slate" />
          </div>

          {/* Tabs */}
          <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100 w-fit">
            {tabs.map((t) => (
              <button key={t.key} onClick={() => setActiveTab(t.key)}
                className={cn("px-4 py-2 rounded-lg text-sm font-medium transition-all",
                  activeTab === t.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700")}>
                {t.label}
              </button>
            ))}
          </div>

          {/* Overview */}
          {activeTab === "overview" && (
            <div className="grid grid-cols-2 gap-6">
              {/* 工作类型分布 */}
              <div className="glass-card rounded-2xl p-6 shadow-sm">
                <h3 className="text-sm font-semibold text-slate-700 mb-4">工作类型分布</h3>
                {/* 条形图 */}
                <div className="flex h-6 rounded-full overflow-hidden mb-4">
                  {workTypeData.map((w) => (
                    <div key={w.type} className={cn("h-full", w.color)} style={{ width: `${w.ratio}%` }} />
                  ))}
                </div>
                <div className="space-y-3">
                  {workTypeData.map((w) => (
                    <div key={w.type} className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <div className={cn("w-3 h-3 rounded-full", w.color)} />
                        <span className="text-sm text-slate-600">{w.type}</span>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="text-sm font-medium text-slate-800">{w.hours}h</span>
                        <span className="text-xs text-slate-400">{w.ratio}%</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* 参与项目概要 */}
              <div className="glass-card rounded-2xl p-6 shadow-sm">
                <h3 className="text-sm font-semibold text-slate-700 mb-4">近期项目</h3>
                <div className="space-y-3">
                  {projects.slice(0, 5).map((p) => {
                    const statusLabel = PROJECT_STATUS_MAP[p.projectStatus] ?? "未知";
                    return (
                      <div key={p.projectId} className="flex items-center justify-between py-2">
                        <div>
                          <p className="text-sm font-medium text-slate-800">{p.projectName}</p>
                          <p className="text-xs text-slate-400">{p.role} · {p.totalHours}h</p>
                        </div>
                        <Badge variant="secondary" className={cn("text-xs", statusStyles[statusLabel] ?? "")}>
                          {statusLabel}
                        </Badge>
                      </div>
                    );
                  })}
                  {projects.length === 0 && <p className="text-sm text-slate-400 text-center py-4">暂无项目</p>}
                </div>
              </div>
            </div>
          )}

          {/* Projects Tab */}
          {activeTab === "projects" && (
            <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50/50">
                  <tr className="text-slate-500 text-xs uppercase tracking-wider">
                    <th className="text-left py-4 px-6 font-semibold">项目信息</th>
                    <th className="text-left py-4 px-4 font-semibold">分类</th>
                    <th className="text-left py-4 px-4 font-semibold">角色</th>
                    <th className="text-right py-4 px-4 font-semibold">投入工时</th>
                    <th className="text-right py-4 px-4 font-semibold">产值</th>
                    <th className="text-center py-4 px-6 font-semibold">状态</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {projects.map((p) => {
                    const statusLabel = PROJECT_STATUS_MAP[p.projectStatus] ?? "未知";
                    return (
                      <tr key={p.projectId} className="hover:bg-slate-50/50">
                        <td className="py-3 px-6">
                          <p className="font-medium text-slate-800">{p.projectName}</p>
                          <p className="text-xs text-slate-400">{p.projectCode}</p>
                        </td>
                        <td className="py-3 px-4 text-slate-600">{p.category}</td>
                        <td className="py-3 px-4 text-slate-600">{p.role}</td>
                        <td className="py-3 px-4 text-right text-slate-700">{p.totalHours}h</td>
                        <td className="py-3 px-4 text-right font-medium text-slate-800">{formatAmount(p.outputValue)}</td>
                        <td className="py-3 px-6 text-center">
                          <Badge variant="secondary" className={cn("text-xs", statusStyles[statusLabel] ?? "")}>
                            {statusLabel}
                          </Badge>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {projects.length === 0 && <div className="py-12 text-center text-sm text-slate-400">暂无数据</div>}
            </div>
          )}

          {/* Payments Tab */}
          {activeTab === "payments" && (
            <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50/50">
                  <tr className="text-slate-500 text-xs uppercase tracking-wider">
                    <th className="text-left py-4 px-6 font-semibold">项目</th>
                    <th className="text-left py-4 px-4 font-semibold">阶段</th>
                    <th className="text-right py-4 px-4 font-semibold">金额</th>
                    <th className="text-left py-4 px-4 font-semibold">发放日期</th>
                    <th className="text-center py-4 px-6 font-semibold">状态</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {payments.map((p) => {
                    const statusLabel = OUTPUT_VALUE_STATUS_MAP[p.status] ?? "未知";
                    return (
                      <tr key={p.distributionId} className="hover:bg-slate-50/50">
                        <td className="py-3 px-6 font-medium text-slate-800">{p.projectName}</td>
                        <td className="py-3 px-4 text-slate-600">{p.stageName}</td>
                        <td className="py-3 px-4 text-right font-semibold text-slate-800">{formatAmount(p.amount)}</td>
                        <td className="py-3 px-4 text-slate-500">{formatDate(p.paidTime)}</td>
                        <td className="py-3 px-6 text-center">
                          <Badge variant="secondary" className={cn("text-xs", paymentStatusStyles[statusLabel] ?? "")}>
                            {statusLabel}
                          </Badge>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {payments.length === 0 && <div className="py-12 text-center text-sm text-slate-400">暂无数据</div>}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatCard({ icon, label, value, color }: {
  icon: React.ReactNode; label: string; value: string; color: string;
}) {
  const colorMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-600", blue: "bg-blue-100 text-blue-600",
    emerald: "bg-emerald-100 text-emerald-600", amber: "bg-amber-100 text-amber-600",
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
