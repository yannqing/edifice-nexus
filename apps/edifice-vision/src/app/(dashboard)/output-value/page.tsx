"use client";

import { useState } from "react";
import {
  Download,
  Search,
  Eye,
  CheckCircle,
  Clock,
  FileCheck,
  Wallet,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { outputValueData } from "@/data/mock-data";
import type { ProjectCategory } from "@/types";

type OutputValueStatus = "待确认" | "待审核" | "已审批" | "已发放";
type TabKey = "all" | OutputValueStatus;

const statusStyles: Record<OutputValueStatus, string> = {
  待确认: "bg-amber-100 text-amber-600",
  待审核: "bg-blue-100 text-blue-600",
  已审批: "bg-emerald-100 text-emerald-600",
  已发放: "bg-purple-100 text-purple-600",
};

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-50 text-blue-600 border-blue-200",
  B类: "bg-emerald-50 text-emerald-600 border-emerald-200",
  C类: "bg-amber-50 text-amber-600 border-amber-200",
  D类: "bg-purple-50 text-purple-600 border-purple-200",
  E类: "bg-rose-50 text-rose-600 border-rose-200",
};

export default function OutputValuePage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const stats = {
    pending: outputValueData.filter((i) => i.status === "待确认").length,
    reviewing: outputValueData.filter((i) => i.status === "待审核").length,
    approved: outputValueData.filter((i) => i.status === "已审批").length,
    paid: outputValueData.filter((i) => i.status === "已发放").length,
    totalAmount: outputValueData.reduce((sum, i) => sum + i.totalAmount, 0),
    paidAmount: outputValueData
      .filter((i) => i.status === "已发放")
      .reduce((sum, i) => sum + i.totalAmount, 0),
  };

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: outputValueData.length },
    { key: "待确认", label: "待确认", count: stats.pending },
    { key: "待审核", label: "待审核", count: stats.reviewing },
    { key: "已审批", label: "已审批", count: stats.approved },
    { key: "已发放", label: "已发放", count: stats.paid },
  ];

  const filteredData = outputValueData.filter((i) => {
    if (activeTab !== "all" && i.status !== activeTab) return false;
    if (searchText && !i.projectName.includes(searchText) && !i.projectCode.includes(searchText))
      return false;
    return true;
  });

  const toggleExpand = (id: number) => {
    setExpandedId(expandedId === id ? null : id);
  };

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">产值分配</h1>
          <p className="text-slate-500 text-sm mt-1">管理项目阶段产值分配方案，确认与审批产值发放。</p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="w-4 h-4" /> 导出
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-4 gap-6">
        <div className="glass-card rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 bg-amber-50 rounded-xl flex items-center justify-center">
              <Clock className="w-5 h-5 text-amber-600" />
            </div>
          </div>
          <p className="text-2xl font-bold text-slate-800">{stats.pending + stats.reviewing}</p>
          <p className="text-slate-500 text-sm mt-1">待处理</p>
        </div>
        <div className="glass-card rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 bg-emerald-50 rounded-xl flex items-center justify-center">
              <FileCheck className="w-5 h-5 text-emerald-600" />
            </div>
          </div>
          <p className="text-2xl font-bold text-slate-800">{stats.approved}</p>
          <p className="text-slate-500 text-sm mt-1">已审批待发放</p>
        </div>
        <div className="glass-card rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 bg-purple-50 rounded-xl flex items-center justify-center">
              <Wallet className="w-5 h-5 text-purple-600" />
            </div>
          </div>
          <p className="text-2xl font-bold text-emerald-600">
            ¥{(stats.paidAmount / 10000).toFixed(1)}万
          </p>
          <p className="text-slate-500 text-sm mt-1">已发放产值</p>
        </div>
        <div className="glass-card rounded-2xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center">
              <CheckCircle className="w-5 h-5 text-blue-600" />
            </div>
          </div>
          <p className="text-2xl font-bold text-slate-800">
            ¥{(stats.totalAmount / 10000).toFixed(1)}万
          </p>
          <p className="text-slate-500 text-sm mt-1">产值总额</p>
        </div>
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
                <span
                  className={cn(
                    "ml-1.5 px-1.5 py-0.5 rounded text-xs",
                    activeTab === tab.key ? "bg-blue-500" : "bg-slate-200"
                  )}
                >
                  {tab.count}
                </span>
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

      {/* Output Value List */}
      <div className="space-y-4">
        {filteredData.map((item) => (
          <div key={item.id} className="glass-card rounded-2xl shadow-sm overflow-hidden">
            {/* Header Row */}
            <div
              className="p-6 cursor-pointer hover:bg-slate-50/50 transition-colors"
              onClick={() => toggleExpand(item.id)}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <span
                    className={cn(
                      "text-xs px-2 py-1 rounded-md border font-medium",
                      categoryStyles[item.category]
                    )}
                  >
                    {item.category}
                  </span>
                  <div>
                    <h3 className="text-base font-semibold text-slate-800">{item.projectName}</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      {item.projectCode} · {item.phase} · {item.phaseName}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <p className="text-xs text-slate-400">阶段产值</p>
                    <p className="text-lg font-bold text-slate-800">
                      ¥{(item.totalAmount / 10000).toFixed(2)}万
                    </p>
                  </div>
                  <Badge variant="secondary" className={cn("text-xs", statusStyles[item.status])}>
                    {item.status}
                  </Badge>
                  {expandedId === item.id ? (
                    <ChevronUp className="w-5 h-5 text-slate-400" />
                  ) : (
                    <ChevronDown className="w-5 h-5 text-slate-400" />
                  )}
                </div>
              </div>
            </div>

            {/* Expanded Content */}
            {expandedId === item.id && (
              <div className="border-t border-slate-100">
                <div className="p-6">
                  <h4 className="text-sm font-semibold text-slate-700 mb-4">产值分配明细</h4>
                  <table className="w-full">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">
                          人员
                        </th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">
                          角色
                        </th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">
                          工作类型
                        </th>
                        <th className="text-right py-3 px-4 text-sm font-medium text-slate-600">
                          分配比例
                        </th>
                        <th className="text-right py-3 px-4 text-sm font-medium text-slate-600">
                          分配金额
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {item.distributions.map((dist, idx) => (
                        <tr key={idx} className="border-b border-slate-50">
                          <td className="py-3 px-4">
                            <div className="flex items-center gap-2">
                              <div className="w-7 h-7 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                                {dist.name[0]}
                              </div>
                              <span className="text-sm font-medium text-slate-700">{dist.name}</span>
                            </div>
                          </td>
                          <td className="py-3 px-4 text-sm text-slate-600">{dist.role}</td>
                          <td className="py-3 px-4">
                            <span
                              className={cn(
                                "px-2 py-1 rounded text-xs font-medium",
                                dist.workType === "管理工作"
                                  ? "bg-blue-100 text-blue-700"
                                  : dist.workType === "基础工作"
                                  ? "bg-emerald-100 text-emerald-700"
                                  : "bg-purple-100 text-purple-700"
                              )}
                            >
                              {dist.workType}
                            </span>
                          </td>
                          <td className="py-3 px-4 text-right text-sm text-slate-600">
                            {dist.ratio}%
                          </td>
                          <td className="py-3 px-4 text-right">
                            <span className="text-sm font-semibold text-emerald-600">
                              ¥{dist.amount.toLocaleString()}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Actions */}
                <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-between items-center">
                  <p className="text-sm text-slate-500">
                    提交时间：{item.submitTime}
                    {item.approvedTime && ` · 审批时间：${item.approvedTime}`}
                    {item.paidTime && ` · 发放时间：${item.paidTime}`}
                  </p>
                  <div className="flex items-center gap-3">
                    <button className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors flex items-center gap-2">
                      <Eye className="w-4 h-4" /> 查看详情
                    </button>
                    {item.status === "待确认" && (
                      <button className="px-4 py-2 text-sm text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors">
                        确认分配
                      </button>
                    )}
                    {item.status === "待审核" && (
                      <button className="px-4 py-2 text-sm text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg transition-colors">
                        审批通过
                      </button>
                    )}
                    {item.status === "已审批" && (
                      <button className="px-4 py-2 text-sm text-white bg-purple-600 hover:bg-purple-700 rounded-lg transition-colors">
                        发放产值
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Empty State */}
      {filteredData.length === 0 && (
        <div className="glass-card rounded-2xl p-12 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Wallet className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无数据</h3>
          <p className="text-sm text-slate-500 mb-4">当前筛选条件下没有找到产值分配记录</p>
          <button
            onClick={() => {
              setActiveTab("all");
              setSearchText("");
            }}
            className="px-4 py-2 text-sm text-blue-600 font-medium hover:bg-blue-50 rounded-lg transition-colors"
          >
            清除筛选条件
          </button>
        </div>
      )}
    </div>
  );
}
