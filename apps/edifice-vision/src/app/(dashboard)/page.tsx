"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  Layers,
  TrendingUp,
  ClipboardCheck,
  Banknote,
  ArrowRight,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { CardPageSkeleton } from "@/components/ui/skeleton";
import { getDashboard } from "@/services/report";
import type { DashboardData } from "@/services/report";
import { ResponseCode } from "@/types/api";
import { PROJECT_STATUS_MAP } from "@/types/project";
import { useAuth } from "@/store/auth-context";

const statusStyles: Record<string, string> = {
  未开始: "bg-slate-100 text-slate-500",
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
};

const categoryColors: Record<string, string> = {
  "A类": "bg-blue-500", "B类": "bg-emerald-500", "C类": "bg-amber-500",
  "D类": "bg-purple-500", "E类": "bg-rose-500",
};

function formatAmount(v: number) {
  return v >= 10000 ? `¥${(v / 10000).toFixed(1)}万` : `¥${v.toLocaleString()}`;
}

function formatTime(t: string | null) {
  if (!t) return "";
  const diff = Date.now() - new Date(t).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  return `${days}天前`;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<DashboardData | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getDashboard();
      if (res.code === ResponseCode.SUCCESS) {
        setData(res.data);
      }
    } catch { /* 静默 */ }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const totalCategoryCount = data?.categoryDistribution?.reduce((sum, c) => sum + c.count, 0) ?? 0;

  return (
    <div className="p-8 space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
          全局数据仪表盘
        </h1>
        <p className="text-slate-500 text-sm mt-1">
          欢迎回来{user?.realName ? `，${user.realName}` : ""}，这是截至目前的产值分配与核算概览信息。
        </p>
      </div>

      {loading && <CardPageSkeleton cards={4} />}

      {!loading && data && (
        <>
          {/* Stats Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {[
              { label: "在研项目总数", value: String(data.stats.projectCount), icon: Layers, color: "blue" },
              { label: "产值总额", value: formatAmount(data.stats.totalOutputValue), icon: TrendingUp, color: "emerald" },
              { label: "已发放产值", value: formatAmount(data.stats.paidOutputValue), icon: Banknote, color: "amber" },
              { label: "待审批验工单", value: String(data.stats.pendingInspections), icon: ClipboardCheck, color: "rose" },
            ].map((stat) => {
              const colorMap: Record<string, { bg: string; text: string }> = {
                blue: { bg: "bg-blue-100", text: "text-blue-600" },
                emerald: { bg: "bg-emerald-100", text: "text-emerald-600" },
                amber: { bg: "bg-amber-100", text: "text-amber-600" },
                rose: { bg: "bg-rose-100", text: "text-rose-600" },
              };
              const c = colorMap[stat.color];
              return (
                <div key={stat.label} className="glass-card p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow">
                  <div className={cn("p-3 rounded-xl w-fit", c.bg, c.text)}>
                    <stat.icon className="w-5 h-5" />
                  </div>
                  <div className="mt-4">
                    <p className="text-sm font-medium text-slate-500">{stat.label}</p>
                    <h3 className="text-2xl font-bold text-slate-900 mt-1">{stat.value}</h3>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Row 2: Top Projects + Todos */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Top Projects */}
            <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-lg font-bold text-slate-800">关键项目概览</h3>
                <button onClick={() => router.push("/all-projects")} className="text-sm text-blue-600 font-medium hover:underline flex items-center gap-1">
                  查看全部 <ArrowRight className="w-3 h-3" />
                </button>
              </div>
              <div className="space-y-4">
                {data.topProjects.map((p) => (
                  <div key={p.projectId} className="flex items-center gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-slate-800 truncate">{p.projectName}</span>
                        {p.type && <span className="text-xs text-slate-400">{p.type}</span>}
                      </div>
                      <div className="w-full bg-slate-100 rounded-full h-2">
                        <div className="bg-blue-500 h-2 rounded-full transition-all" style={{ width: `${Math.min(p.progress, 100)}%` }} />
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-semibold text-slate-800">{formatAmount(p.contractAmount)}</p>
                      <p className="text-xs text-slate-400">{p.progress}% 完成</p>
                    </div>
                  </div>
                ))}
                {data.topProjects.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无项目数据</p>
                )}
              </div>
            </div>

            {/* Todos */}
            <div className="glass-card rounded-2xl p-6 shadow-sm">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-lg font-bold text-slate-800">待办事项</h3>
                <span className="text-xs px-2 py-0.5 bg-rose-100 text-rose-600 rounded-full font-medium">
                  {data.todos.length}
                </span>
              </div>
              <div className="space-y-3">
                {data.todos.map((todo) => (
                  <div key={todo.id} className="flex items-start gap-3 p-3 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer">
                    <div className={cn("w-2 h-2 rounded-full mt-1.5 shrink-0",
                      todo.type === "验工审批" ? "bg-amber-500" : "bg-blue-500")} />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-slate-800 truncate">{todo.title}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-xs text-slate-400">{todo.from}</span>
                        <span className="text-xs text-slate-300">·</span>
                        <span className="text-xs text-slate-400">{formatTime(todo.time)}</span>
                      </div>
                    </div>
                    <Badge variant="secondary" className={cn("text-xs shrink-0",
                      todo.type === "验工审批" ? "bg-amber-100 text-amber-600" : "bg-blue-100 text-blue-600")}>
                      {todo.type}
                    </Badge>
                  </div>
                ))}
                {data.todos.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无待办</p>
                )}
              </div>
            </div>
          </div>

          {/* Row 3: My Projects + Category */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* My Projects */}
            <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-lg font-bold text-slate-800">我的项目进度</h3>
                <button onClick={() => router.push("/my-projects")} className="text-sm text-blue-600 font-medium hover:underline flex items-center gap-1">
                  查看全部 <ArrowRight className="w-3 h-3" />
                </button>
              </div>
              <div className="space-y-4">
                {data.myProjects.map((p) => {
                  const statusLabel = PROJECT_STATUS_MAP[p.projectStatus] ?? "未知";
                  return (
                    <div key={p.projectId} className="flex items-center gap-4 p-3 rounded-xl hover:bg-slate-50 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <span className="text-sm font-medium text-slate-800">{p.projectName}</span>
                          {p.category && <span className="text-xs text-slate-400">{p.category}</span>}
                        </div>
                        <div className="flex gap-1">
                          {Array.from({ length: p.phases }).map((_, i) => (
                            <div key={i} className={cn("h-1.5 flex-1 rounded-full",
                              i < p.currentPhase ? "bg-blue-500" : "bg-slate-200")} />
                          ))}
                        </div>
                      </div>
                      <div className="flex items-center gap-3 shrink-0">
                        <span className="text-xs text-slate-400">{p.currentPhase}/{p.phases}</span>
                        <Badge variant="secondary" className={cn("text-xs", statusStyles[statusLabel] ?? "")}>
                          {statusLabel}
                        </Badge>
                      </div>
                    </div>
                  );
                })}
                {data.myProjects.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无参与项目</p>
                )}
              </div>
            </div>

            {/* Category Distribution */}
            <div className="glass-card rounded-2xl p-6 shadow-sm">
              <h3 className="text-lg font-bold text-slate-800 mb-5">项目分类分布</h3>
              <div className="space-y-4">
                {data.categoryDistribution.map((cat) => {
                  const pct = totalCategoryCount > 0 ? ((cat.count / totalCategoryCount) * 100).toFixed(1) : "0";
                  return (
                    <div key={cat.category} className="flex items-center gap-3">
                      <div className={cn("w-3 h-3 rounded-full shrink-0", categoryColors[cat.category] ?? "bg-slate-400")} />
                      <div className="flex-1">
                        <div className="flex justify-between text-sm mb-1">
                          <span className="font-medium text-slate-700">{cat.category} · {cat.name}</span>
                          <span className="text-slate-500">{cat.count} 个</span>
                        </div>
                        <div className="w-full bg-slate-100 rounded-full h-1.5">
                          <div className={cn("h-1.5 rounded-full", categoryColors[cat.category] ?? "bg-slate-400")}
                            style={{ width: `${pct}%` }} />
                        </div>
                      </div>
                    </div>
                  );
                })}
                {data.categoryDistribution.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无数据</p>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
