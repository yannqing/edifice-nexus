"use client";

import { useState } from "react";
import {
  Download,
  Coins,
  Folder,
  Clock,
  Trophy,
  TrendingUp,
  LayoutDashboard,
  Wallet,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  currentUser,
  performanceMonthlyTrend,
  performanceProjectDetails,
  performanceWorkTypeStats,
  performancePaymentRecords,
} from "@/data/mock-data";
import type { ProjectCategory, ProjectStatus, WorkType } from "@/types";

type TabKey = "overview" | "projects" | "payments";
type DateRangeKey = "month" | "quarter" | "year" | "all";

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-100 text-blue-700",
  B类: "bg-emerald-100 text-emerald-700",
  C类: "bg-amber-100 text-amber-700",
  D类: "bg-purple-100 text-purple-700",
  E类: "bg-rose-100 text-rose-700",
};

const statusStyles: Record<ProjectStatus, string> = {
  进行中: "bg-blue-100 text-blue-700",
  待验收: "bg-amber-100 text-amber-700",
  已完成: "bg-emerald-100 text-emerald-700",
  未开始: "bg-slate-100 text-slate-500",
};

const workTypeStyles: Record<WorkType, string> = {
  管理工作: "bg-blue-100 text-blue-700",
  基础工作: "bg-emerald-100 text-emerald-700",
  智励工作: "bg-purple-100 text-purple-700",
};

type PaymentStatus = "已发放" | "待发放" | "待审核";
const paymentStatusStyles: Record<PaymentStatus, string> = {
  已发放: "bg-emerald-100 text-emerald-700",
  待发放: "bg-amber-100 text-amber-700",
  待审核: "bg-blue-100 text-blue-700",
};

export default function PerformancePage() {
  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [dateRange, setDateRange] = useState<DateRangeKey>("month");

  const personalStats = [
    { label: "本月产值", value: "¥28,500", change: "+12.5%", icon: Coins, color: "text-emerald-600", bgColor: "bg-emerald-50", trend: "up" },
    { label: "参与项目", value: "6个", change: "+1", icon: Folder, color: "text-blue-600", bgColor: "bg-blue-50", trend: "up" },
    { label: "本月工时", value: "168h", change: "标准", icon: Clock, color: "text-purple-600", bgColor: "bg-purple-50", trend: "stable" },
    { label: "部门排名", value: "第2名", change: "↑1", icon: Trophy, color: "text-amber-600", bgColor: "bg-amber-50", trend: "up" },
  ];

  const tabs: { key: TabKey; label: string; icon: typeof LayoutDashboard }[] = [
    { key: "overview", label: "绩效概览", icon: LayoutDashboard },
    { key: "projects", label: "项目明细", icon: Folder },
    { key: "payments", label: "产值发放", icon: Wallet },
  ];

  const dateRanges: { key: DateRangeKey; label: string }[] = [
    { key: "month", label: "本月" },
    { key: "quarter", label: "本季度" },
    { key: "year", label: "本年度" },
    { key: "all", label: "全部" },
  ];

  const maxValue = Math.max(...performanceMonthlyTrend.map((m) => m.value));
  const projectTotals = {
    hours: performanceProjectDetails.reduce((sum, p) => sum + p.totalHours, 0),
    outputValue: performanceProjectDetails.reduce((sum, p) => sum + p.outputValue, 0),
  };
  const paidAmount = performancePaymentRecords.filter((r) => r.status === "已发放").reduce((sum, r) => sum + r.amount, 0);
  const pendingAmount = performancePaymentRecords.filter((r) => r.status !== "已发放").reduce((sum, r) => sum + r.amount, 0);

  return (
    <div className="p-8 space-y-6">
      {/* User Profile Header */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex items-center gap-6">
          <div className="w-20 h-20 bg-slate-200 rounded-2xl overflow-hidden">
            <img src={currentUser.avatar} alt="avatar" className="w-full h-full object-cover" />
          </div>
          <div className="flex-1">
            <h1 className="text-2xl font-bold text-slate-800">{currentUser.name}</h1>
            <p className="text-slate-500 mt-1">造价部 · 项目经理</p>
            <div className="flex items-center gap-4 mt-3">
              <span className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm font-medium">
                工号: EMP001
              </span>
              <span className="px-3 py-1 bg-emerald-100 text-emerald-700 rounded-full text-sm font-medium">
                入职: 2020-03-15
              </span>
            </div>
          </div>
          <div className="text-right">
            <p className="text-slate-500 text-sm">累计产值</p>
            <p className="text-3xl font-bold text-slate-800 mt-1">¥154,000</p>
            <p className="text-emerald-600 text-sm mt-1 flex items-center gap-1 justify-end">
              <TrendingUp className="w-4 h-4" /> 较去年 +23.5%
            </p>
          </div>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-4 gap-6">
        {personalStats.map((stat, idx) => (
          <div key={idx} className="glass-card rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between mb-3">
              <div className={cn("w-10 h-10 rounded-xl flex items-center justify-center", stat.bgColor)}>
                <stat.icon className={cn("w-5 h-5", stat.color)} />
              </div>
              <span
                className={cn(
                  "text-sm font-medium",
                  stat.trend === "up" ? "text-emerald-600" : stat.trend === "down" ? "text-rose-600" : "text-slate-500"
                )}
              >
                {stat.change}
              </span>
            </div>
            <p className="text-2xl font-bold text-slate-800">{stat.value}</p>
            <p className="text-slate-500 text-sm mt-1">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Tab Navigation */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={cn(
                  "px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 transition-colors",
                  activeTab === tab.key ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-100"
                )}
              >
                <tab.icon className="w-4 h-4" />
                {tab.label}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-2">
            {dateRanges.map((range) => (
              <button
                key={range.key}
                onClick={() => setDateRange(range.key)}
                className={cn(
                  "px-3 py-1.5 rounded-lg text-sm font-medium transition-colors",
                  dateRange === range.key ? "bg-slate-800 text-white" : "text-slate-600 hover:bg-slate-100"
                )}
              >
                {range.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Overview Tab */}
      {activeTab === "overview" && (
        <div className="grid grid-cols-2 gap-6">
          {/* Output Value Trend */}
          <div className="glass-card rounded-2xl p-6 shadow-sm">
            <h3 className="text-lg font-semibold text-slate-800 mb-4">产值趋势</h3>
            <div className="flex items-end gap-4 h-48">
              {performanceMonthlyTrend.map((item, idx) => (
                <div key={idx} className="flex-1 flex flex-col items-center gap-2">
                  <div className="w-full flex items-end justify-center h-40">
                    <div
                      className="w-10 bg-gradient-to-t from-blue-500 to-blue-400 rounded-t hover:from-blue-600 hover:to-blue-500 transition-colors cursor-pointer"
                      style={{ height: `${(item.value / maxValue) * 100}%` }}
                      title={`¥${item.value.toLocaleString()}`}
                    />
                  </div>
                  <span className="text-xs text-slate-500">{item.month.slice(5)}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Work Type Distribution */}
          <div className="glass-card rounded-2xl p-6 shadow-sm">
            <h3 className="text-lg font-semibold text-slate-800 mb-4">工作类型分布</h3>
            <div className="flex items-center gap-6">
              <div className="relative w-36 h-36">
                <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
                  {performanceWorkTypeStats.reduce(
                    (acc, stat, idx) => {
                      const colors = ["#3B82F6", "#10B981", "#8B5CF6"];
                      acc.elements.push(
                        <circle
                          key={idx}
                          cx="50"
                          cy="50"
                          r="40"
                          fill="transparent"
                          stroke={colors[idx]}
                          strokeWidth="20"
                          strokeDasharray={`${stat.ratio * 2.51} 251`}
                          strokeDashoffset={-acc.offset * 2.51}
                        />
                      );
                      acc.offset += stat.ratio;
                      return acc;
                    },
                    { elements: [] as React.ReactNode[], offset: 0 }
                  ).elements}
                </svg>
              </div>
              <div className="flex-1 space-y-4">
                {performanceWorkTypeStats.map((stat, idx) => (
                  <div key={idx}>
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-2">
                        <div className={cn("w-3 h-3 rounded-full", stat.color)} />
                        <span className="text-sm text-slate-600">{stat.type}</span>
                      </div>
                      <span className="text-sm font-medium text-slate-700">{stat.ratio}%</span>
                    </div>
                    <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className={cn("h-full", stat.color)} style={{ width: `${stat.ratio}%` }} />
                    </div>
                    <p className="text-xs text-slate-500 mt-1">¥{stat.amount.toLocaleString()}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Recent Projects */}
          <div className="glass-card rounded-2xl p-6 shadow-sm col-span-2">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-800">最近参与项目</h3>
              <button
                onClick={() => setActiveTab("projects")}
                className="text-blue-600 text-sm font-medium hover:text-blue-700"
              >
                查看全部
              </button>
            </div>
            <div className="grid grid-cols-3 gap-4">
              {performanceProjectDetails.slice(0, 3).map((project) => (
                <div key={project.id} className="bg-slate-50 rounded-xl p-4">
                  <div className="flex items-start justify-between mb-2">
                    <span className={cn("px-2 py-0.5 rounded text-xs font-medium", categoryStyles[project.category])}>
                      {project.category}
                    </span>
                    <span
                      className={cn(
                        "text-xs font-medium",
                        project.status === "已完成"
                          ? "text-emerald-600"
                          : project.status === "待验收"
                          ? "text-amber-600"
                          : "text-blue-600"
                      )}
                    >
                      {project.status}
                    </span>
                  </div>
                  <h4 className="font-medium text-slate-800 mb-1 line-clamp-1">{project.name}</h4>
                  <p className="text-xs text-slate-500 mb-3">
                    {project.role} · {project.workType}
                  </p>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-500">{project.totalHours}h</span>
                    <span className="font-semibold text-emerald-600">¥{project.outputValue.toLocaleString()}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Projects Tab */}
      {activeTab === "projects" && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 border-b border-slate-100">
            <h2 className="text-lg font-semibold text-slate-800">项目参与明细</h2>
            <p className="text-slate-500 text-sm mt-1">您参与的所有项目及产值贡献</p>
          </div>
          <table className="w-full">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left py-4 px-6 text-sm font-semibold text-slate-600">项目名称</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">分类</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">角色</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">工作类型</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">投入工时</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">产值贡献</th>
                <th className="text-center py-4 px-4 text-sm font-semibold text-slate-600">状态</th>
              </tr>
            </thead>
            <tbody>
              {performanceProjectDetails.map((project) => (
                <tr key={project.id} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                  <td className="py-4 px-6">
                    <div className="font-medium text-slate-800">{project.name}</div>
                    <div className="text-xs text-slate-400 mt-0.5">{project.code}</div>
                  </td>
                  <td className="py-4 px-4">
                    <span className={cn("px-2 py-1 rounded text-xs font-medium", categoryStyles[project.category])}>
                      {project.category}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-slate-600">{project.role}</td>
                  <td className="py-4 px-4">
                    <span className={cn("px-2 py-1 rounded text-xs font-medium", workTypeStyles[project.workType])}>
                      {project.workType}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-right text-slate-700">{project.totalHours}h</td>
                  <td className="py-4 px-4 text-right font-semibold text-emerald-600">
                    ¥{project.outputValue.toLocaleString()}
                  </td>
                  <td className="py-4 px-4 text-center">
                    <Badge variant="secondary" className={cn("text-xs", statusStyles[project.status])}>
                      {project.status}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td className="py-4 px-6 text-slate-800">合计</td>
                <td className="py-4 px-4"></td>
                <td className="py-4 px-4"></td>
                <td className="py-4 px-4"></td>
                <td className="py-4 px-4 text-right text-slate-800">{projectTotals.hours}h</td>
                <td className="py-4 px-4 text-right text-emerald-600">¥{projectTotals.outputValue.toLocaleString()}</td>
                <td className="py-4 px-4"></td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {/* Payments Tab */}
      {activeTab === "payments" && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 border-b border-slate-100">
            <h2 className="text-lg font-semibold text-slate-800">产值发放记录</h2>
            <p className="text-slate-500 text-sm mt-1">您的产值发放历史及待发放明细</p>
          </div>
          <table className="w-full">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left py-4 px-6 text-sm font-semibold text-slate-600">项目名称</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">阶段</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">发放金额</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">发放日期</th>
                <th className="text-center py-4 px-4 text-sm font-semibold text-slate-600">状态</th>
              </tr>
            </thead>
            <tbody>
              {performancePaymentRecords.map((record) => (
                <tr key={record.id} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                  <td className="py-4 px-6 font-medium text-slate-800">{record.project}</td>
                  <td className="py-4 px-4 text-slate-600">{record.phase}</td>
                  <td className="py-4 px-4 text-right font-semibold text-emerald-600">
                    ¥{record.amount.toLocaleString()}
                  </td>
                  <td className="py-4 px-4 text-slate-600">{record.date}</td>
                  <td className="py-4 px-4 text-center">
                    <Badge variant="secondary" className={cn("text-xs", paymentStatusStyles[record.status])}>
                      {record.status}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="p-6 bg-slate-50 border-t border-slate-100">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-6">
                <div>
                  <p className="text-sm text-slate-500">已发放总额</p>
                  <p className="text-xl font-bold text-emerald-600">¥{paidAmount.toLocaleString()}</p>
                </div>
                <div className="w-px h-10 bg-slate-200" />
                <div>
                  <p className="text-sm text-slate-500">待发放总额</p>
                  <p className="text-xl font-bold text-amber-600">¥{pendingAmount.toLocaleString()}</p>
                </div>
              </div>
              <Button variant="outline" className="flex items-center gap-2">
                <Download className="w-4 h-4" /> 导出明细
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
