"use client";

import { useState } from "react";
import {
  Download,
  Layers,
  FileText,
  TrendingUp,
  PieChart,
  BarChart3,
  Wallet,
  Users,
  TrendingDown,
  Minus,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  statisticsProjectData,
  statisticsCollectionData,
  statisticsPersonnelData,
  statisticsCategoryData,
} from "@/data/mock-data";
import type { ProjectCategory } from "@/types";

type ReportKey = "project" | "collection" | "personnel" | "category";
type DateRangeKey = "week" | "month" | "quarter" | "year";

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-100 text-blue-700",
  B类: "bg-emerald-100 text-emerald-700",
  C类: "bg-amber-100 text-amber-700",
  D类: "bg-purple-100 text-purple-700",
  E类: "bg-rose-100 text-rose-700",
};

const categoryColors = ["bg-blue-500", "bg-emerald-500", "bg-amber-500", "bg-purple-500", "bg-rose-500"];

export default function StatisticsPage() {
  const [activeReport, setActiveReport] = useState<ReportKey>("project");
  const [dateRange, setDateRange] = useState<DateRangeKey>("month");

  const overallStats = [
    { label: "项目总数", value: "42", icon: Layers, color: "text-blue-600", bgColor: "bg-blue-50" },
    { label: "合同总额", value: "¥565.0万", icon: FileText, color: "text-purple-600", bgColor: "bg-purple-50" },
    { label: "已完成产值", value: "¥282.0万", icon: TrendingUp, color: "text-emerald-600", bgColor: "bg-emerald-50" },
    { label: "整体回款率", value: "85.3%", icon: PieChart, color: "text-amber-600", bgColor: "bg-amber-50" },
  ];

  const reports: { key: ReportKey; label: string; icon: typeof BarChart3 }[] = [
    { key: "project", label: "项目产值统计", icon: BarChart3 },
    { key: "collection", label: "回款分析报表", icon: Wallet },
    { key: "personnel", label: "人员产值排行", icon: Users },
    { key: "category", label: "项目分类统计", icon: PieChart },
  ];

  const dateRanges: { key: DateRangeKey; label: string }[] = [
    { key: "week", label: "本周" },
    { key: "month", label: "本月" },
    { key: "quarter", label: "本季度" },
    { key: "year", label: "本年度" },
  ];

  const projectTotals = {
    contractAmount: statisticsProjectData.reduce((sum, p) => sum + p.contractAmount, 0),
    completedAmount: statisticsProjectData.reduce((sum, p) => sum + p.completedAmount, 0),
    outputValue: statisticsProjectData.reduce((sum, p) => sum + p.outputValue, 0),
    pendingValue: statisticsProjectData.reduce((sum, p) => sum + p.pendingValue, 0),
  };

  const categoryTotals = {
    count: statisticsCategoryData.reduce((sum, c) => sum + c.count, 0),
    contractTotal: statisticsCategoryData.reduce((sum, c) => sum + c.contractTotal, 0),
    completedTotal: statisticsCategoryData.reduce((sum, c) => sum + c.completedTotal, 0),
  };

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">统计报表</h1>
          <p className="text-slate-500 text-sm mt-1">查看项目、回款、人员等各类统计数据</p>
        </div>
        <Button className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2">
          <Download className="w-4 h-4" /> 导出报表
        </Button>
      </div>

      {/* Overall Stats */}
      <div className="grid grid-cols-4 gap-6">
        {overallStats.map((stat, idx) => (
          <div key={idx} className="glass-card rounded-2xl p-5 shadow-sm">
            <div className="flex items-center justify-between mb-3">
              <div className={cn("w-10 h-10 rounded-xl flex items-center justify-center", stat.bgColor)}>
                <stat.icon className={cn("w-5 h-5", stat.color)} />
              </div>
            </div>
            <p className="text-2xl font-bold text-slate-800">{stat.value}</p>
            <p className="text-slate-500 text-sm mt-1">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Report Tabs & Date Range */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {reports.map((report) => (
              <button
                key={report.key}
                onClick={() => setActiveReport(report.key)}
                className={cn(
                  "px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 transition-colors",
                  activeReport === report.key
                    ? "bg-blue-600 text-white"
                    : "text-slate-600 hover:bg-slate-100"
                )}
              >
                <report.icon className="w-4 h-4" />
                {report.label}
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
                  dateRange === range.key
                    ? "bg-slate-800 text-white"
                    : "text-slate-600 hover:bg-slate-100"
                )}
              >
                {range.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Project Output Value Stats */}
      {activeReport === "project" && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 border-b border-slate-100">
            <h2 className="text-lg font-semibold text-slate-800">项目产值统计</h2>
            <p className="text-slate-500 text-sm mt-1">各项目合同金额、完成产值及利润情况</p>
          </div>
          <table className="w-full">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left py-4 px-6 text-sm font-semibold text-slate-600">项目名称</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">分类</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">合同金额</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">已完成金额</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">已发产值</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">待发产值</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">利润率</th>
              </tr>
            </thead>
            <tbody>
              {statisticsProjectData.map((project) => (
                <tr key={project.id} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                  <td className="py-4 px-6">
                    <span className="font-medium text-slate-800">{project.name}</span>
                  </td>
                  <td className="py-4 px-4">
                    <span className={cn("px-2 py-1 rounded text-xs font-medium", categoryStyles[project.category])}>
                      {project.category}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-right text-slate-700">¥{project.contractAmount.toLocaleString()}</td>
                  <td className="py-4 px-4 text-right text-slate-700">¥{project.completedAmount.toLocaleString()}</td>
                  <td className="py-4 px-4 text-right font-medium text-emerald-600">
                    ¥{project.outputValue.toLocaleString()}
                  </td>
                  <td className="py-4 px-4 text-right font-medium text-amber-600">
                    ¥{project.pendingValue.toLocaleString()}
                  </td>
                  <td className="py-4 px-4 text-right">
                    <span
                      className={cn(
                        "font-medium",
                        project.profitRate > 20
                          ? "text-emerald-600"
                          : project.profitRate > 0
                          ? "text-amber-600"
                          : "text-slate-400"
                      )}
                    >
                      {project.profitRate}%
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td className="py-4 px-6 text-slate-800">合计</td>
                <td className="py-4 px-4"></td>
                <td className="py-4 px-4 text-right text-slate-800">
                  ¥{projectTotals.contractAmount.toLocaleString()}
                </td>
                <td className="py-4 px-4 text-right text-slate-800">
                  ¥{projectTotals.completedAmount.toLocaleString()}
                </td>
                <td className="py-4 px-4 text-right text-emerald-600">
                  ¥{projectTotals.outputValue.toLocaleString()}
                </td>
                <td className="py-4 px-4 text-right text-amber-600">
                  ¥{projectTotals.pendingValue.toLocaleString()}
                </td>
                <td className="py-4 px-4"></td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {/* Collection Analysis */}
      {activeReport === "collection" && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 border-b border-slate-100">
            <h2 className="text-lg font-semibold text-slate-800">回款分析报表</h2>
            <p className="text-slate-500 text-sm mt-1">各月份应回款与实际回款对比分析</p>
          </div>
          <div className="p-6">
            {/* Bar Chart */}
            <div className="mb-6">
              <div className="flex items-end gap-4 h-48">
                {statisticsCollectionData.map((stat, idx) => (
                  <div key={idx} className="flex-1 flex flex-col items-center gap-2">
                    <div className="w-full flex gap-2 items-end justify-center h-40">
                      <div
                        className="w-8 bg-blue-200 rounded-t"
                        style={{ height: `${(stat.expected / 600000) * 100}%` }}
                        title={`应回款: ¥${stat.expected.toLocaleString()}`}
                      />
                      <div
                        className="w-8 bg-emerald-500 rounded-t"
                        style={{ height: `${(stat.actual / 600000) * 100}%` }}
                        title={`实际回款: ¥${stat.actual.toLocaleString()}`}
                      />
                    </div>
                    <span className="text-sm text-slate-600">{stat.month}</span>
                  </div>
                ))}
              </div>
              <div className="flex items-center justify-center gap-6 mt-4">
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 bg-blue-200 rounded" />
                  <span className="text-sm text-slate-600">应回款金额</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 bg-emerald-500 rounded" />
                  <span className="text-sm text-slate-600">实际回款金额</span>
                </div>
              </div>
            </div>

            {/* Detail Table */}
            <table className="w-full">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left py-3 px-4 text-sm font-semibold text-slate-600">时间</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">应回款金额</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">实回款金额</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">回款率</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">逾期金额</th>
                </tr>
              </thead>
              <tbody>
                {statisticsCollectionData.map((stat, idx) => (
                  <tr key={idx} className="border-b border-slate-100">
                    <td className="py-3 px-4 text-sm text-slate-700">{stat.month}</td>
                    <td className="py-3 px-4 text-sm text-right text-slate-700">¥{stat.expected.toLocaleString()}</td>
                    <td className="py-3 px-4 text-sm text-right font-medium text-emerald-600">
                      ¥{stat.actual.toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-sm text-right">
                      <span
                        className={cn(
                          "font-medium",
                          stat.rate >= 90 ? "text-emerald-600" : stat.rate >= 80 ? "text-amber-600" : "text-rose-600"
                        )}
                      >
                        {stat.rate}%
                      </span>
                    </td>
                    <td className="py-3 px-4 text-sm text-right text-rose-600">¥{stat.overdue.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Personnel Ranking */}
      {activeReport === "personnel" && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 border-b border-slate-100">
            <h2 className="text-lg font-semibold text-slate-800">人员产值排行</h2>
            <p className="text-slate-500 text-sm mt-1">员工产值贡献排名及趋势</p>
          </div>
          <table className="w-full">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-center py-4 px-4 text-sm font-semibold text-slate-600 w-16">排名</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">姓名</th>
                <th className="text-left py-4 px-4 text-sm font-semibold text-slate-600">部门</th>
                <th className="text-center py-4 px-4 text-sm font-semibold text-slate-600">参与项目数</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">总产值</th>
                <th className="text-right py-4 px-4 text-sm font-semibold text-slate-600">人均产值</th>
                <th className="text-center py-4 px-4 text-sm font-semibold text-slate-600">趋势</th>
              </tr>
            </thead>
            <tbody>
              {statisticsPersonnelData.map((person) => (
                <tr key={person.rank} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                  <td className="py-4 px-4 text-center">
                    {person.rank <= 3 ? (
                      <span
                        className={cn(
                          "inline-flex items-center justify-center w-8 h-8 rounded-full font-bold text-sm",
                          person.rank === 1
                            ? "bg-amber-100 text-amber-600"
                            : person.rank === 2
                            ? "bg-slate-200 text-slate-600"
                            : "bg-orange-100 text-orange-600"
                        )}
                      >
                        {person.rank}
                      </span>
                    ) : (
                      <span className="text-slate-500">{person.rank}</span>
                    )}
                  </td>
                  <td className="py-4 px-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 bg-slate-200 rounded-full flex items-center justify-center text-xs font-medium text-slate-600">
                        {person.name[0]}
                      </div>
                      <span className="font-medium text-slate-800">{person.name}</span>
                    </div>
                  </td>
                  <td className="py-4 px-4 text-slate-600">{person.department}</td>
                  <td className="py-4 px-4 text-center text-slate-700">{person.projectCount}</td>
                  <td className="py-4 px-4 text-right font-semibold text-slate-800">
                    ¥{person.outputValue.toLocaleString()}
                  </td>
                  <td className="py-4 px-4 text-right text-slate-600">¥{person.avgValue.toLocaleString()}</td>
                  <td className="py-4 px-4 text-center">
                    {person.trend === "up" && <TrendingUp className="w-5 h-5 text-emerald-500 inline" />}
                    {person.trend === "down" && <TrendingDown className="w-5 h-5 text-rose-500 inline" />}
                    {person.trend === "stable" && <Minus className="w-5 h-5 text-slate-400 inline" />}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Category Distribution */}
      {activeReport === "category" && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 border-b border-slate-100">
            <h2 className="text-lg font-semibold text-slate-800">项目分类统计</h2>
            <p className="text-slate-500 text-sm mt-1">按项目类型统计合同金额及完成情况</p>
          </div>
          <div className="p-6">
            {/* Pie Chart Placeholder */}
            <div className="flex items-center gap-8 mb-8">
              <div className="relative w-48 h-48">
                <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
                  {statisticsCategoryData.reduce(
                    (acc, cat, idx) => {
                      const colors = ["#3B82F6", "#10B981", "#F59E0B", "#8B5CF6", "#EF4444"];
                      acc.elements.push(
                        <circle
                          key={idx}
                          cx="50"
                          cy="50"
                          r="40"
                          fill="transparent"
                          stroke={colors[idx]}
                          strokeWidth="20"
                          strokeDasharray={`${cat.percentage * 2.51} 251`}
                          strokeDashoffset={-acc.offset * 2.51}
                        />
                      );
                      acc.offset += cat.percentage;
                      return acc;
                    },
                    { elements: [] as React.ReactNode[], offset: 0 }
                  ).elements}
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <div className="text-center">
                    <p className="text-2xl font-bold text-slate-800">42</p>
                    <p className="text-sm text-slate-500">项目总数</p>
                  </div>
                </div>
              </div>
              <div className="flex-1 space-y-3">
                {statisticsCategoryData.map((cat, idx) => (
                  <div key={idx} className="flex items-center gap-3">
                    <div className={cn("w-3 h-3 rounded-full", categoryColors[idx])} />
                    <span className="text-sm text-slate-600 w-24">
                      {cat.category} {cat.name}
                    </span>
                    <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className={cn("h-full", categoryColors[idx])} style={{ width: `${cat.percentage}%` }} />
                    </div>
                    <span className="text-sm font-medium text-slate-700 w-12 text-right">{cat.percentage}%</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Category Table */}
            <table className="w-full">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left py-3 px-4 text-sm font-semibold text-slate-600">分类</th>
                  <th className="text-center py-3 px-4 text-sm font-semibold text-slate-600">项目数量</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">合同总额</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">已完成金额</th>
                  <th className="text-right py-3 px-4 text-sm font-semibold text-slate-600">完成率</th>
                </tr>
              </thead>
              <tbody>
                {statisticsCategoryData.map((cat, idx) => (
                  <tr key={idx} className="border-b border-slate-100">
                    <td className="py-3 px-4">
                      <span className="font-medium text-slate-800">{cat.category}</span>
                      <span className="text-slate-500 ml-2">({cat.name})</span>
                    </td>
                    <td className="py-3 px-4 text-center text-slate-700">{cat.count}</td>
                    <td className="py-3 px-4 text-right text-slate-700">¥{cat.contractTotal.toLocaleString()}</td>
                    <td className="py-3 px-4 text-right font-medium text-emerald-600">
                      ¥{cat.completedTotal.toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <span className="font-medium text-blue-600">
                        {((cat.completedTotal / cat.contractTotal) * 100).toFixed(1)}%
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="bg-slate-50 font-semibold">
                <tr>
                  <td className="py-3 px-4 text-slate-800">合计</td>
                  <td className="py-3 px-4 text-center text-slate-800">{categoryTotals.count}</td>
                  <td className="py-3 px-4 text-right text-slate-800">
                    ¥{categoryTotals.contractTotal.toLocaleString()}
                  </td>
                  <td className="py-3 px-4 text-right text-emerald-600">
                    ¥{categoryTotals.completedTotal.toLocaleString()}
                  </td>
                  <td className="py-3 px-4"></td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
