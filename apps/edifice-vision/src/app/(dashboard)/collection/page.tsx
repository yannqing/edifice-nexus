"use client";

import { useState } from "react";
import {
  Plus,
  Search,
  Banknote,
  CheckCircle,
  PieChart,
  Clock,
  X,
  AlertCircle,
  UploadCloud,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { collectionData } from "@/data/mock-data";

type CollectionStatus = "已回款" | "部分回款" | "未回款";
type TabKey = "all" | "collected" | "partial" | "uncollected";

const statusStyles: Record<CollectionStatus, string> = {
  已回款: "bg-emerald-100 text-emerald-700",
  部分回款: "bg-amber-100 text-amber-700",
  未回款: "bg-rose-100 text-rose-700",
};

export default function CollectionPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedProject, setSelectedProject] = useState<(typeof collectionData)[0] | null>(null);

  const totalExpected = collectionData.reduce((sum, c) => sum + c.expectedAmount, 0);
  const totalCollected = collectionData.reduce((sum, c) => sum + c.collectedAmount, 0);
  const overallRate = totalExpected > 0 ? ((totalCollected / totalExpected) * 100).toFixed(1) : "0";

  const stats = [
    {
      label: "应收款总额",
      value: `¥${(totalExpected / 10000).toFixed(1)}万`,
      icon: Banknote,
      color: "text-blue-600",
      bgColor: "bg-blue-50",
    },
    {
      label: "已回款金额",
      value: `¥${(totalCollected / 10000).toFixed(1)}万`,
      icon: CheckCircle,
      color: "text-emerald-600",
      bgColor: "bg-emerald-50",
    },
    {
      label: "整体回款率",
      value: `${overallRate}%`,
      icon: PieChart,
      color: "text-amber-600",
      bgColor: "bg-amber-50",
    },
    {
      label: "待回款金额",
      value: `¥${((totalExpected - totalCollected) / 10000).toFixed(1)}万`,
      icon: Clock,
      color: "text-rose-600",
      bgColor: "bg-rose-50",
    },
  ];

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: collectionData.length },
    { key: "collected", label: "已回款", count: collectionData.filter((c) => c.status === "已回款").length },
    { key: "partial", label: "部分回款", count: collectionData.filter((c) => c.status === "部分回款").length },
    { key: "uncollected", label: "未回款", count: collectionData.filter((c) => c.status === "未回款").length },
  ];

  const filteredCollections = collectionData.filter((c) => {
    const matchTab =
      activeTab === "all" ||
      (activeTab === "collected" && c.status === "已回款") ||
      (activeTab === "partial" && c.status === "部分回款") ||
      (activeTab === "uncollected" && c.status === "未回款");
    const matchSearch = c.projectName.includes(searchText) || c.projectCode.includes(searchText);
    return matchTab && matchSearch;
  });

  const getRateColor = (rate: number) => {
    if (rate >= 100) return "text-emerald-600";
    if (rate >= 50) return "text-amber-600";
    return "text-rose-600";
  };

  const getRateBarColor = (rate: number) => {
    if (rate >= 100) return "bg-emerald-500";
    if (rate >= 50) return "bg-amber-500";
    return "bg-rose-500";
  };

  const openDetail = (collection: (typeof collectionData)[0]) => {
    setSelectedProject(collection);
    setShowDetailModal(true);
  };

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">回款管理</h1>
          <p className="text-slate-500 text-sm mt-1">管理项目回款计划与实际回款记录</p>
        </div>
        <Button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
        >
          <Plus className="w-4 h-4" /> 录入回款
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-4 gap-6">
        {stats.map((stat, idx) => (
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
                  activeTab === tab.key ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-100"
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

      {/* Collection Table */}
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
            {filteredCollections.map((item) => (
              <tr key={item.id} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                <td className="py-4 px-6">
                  <div className="font-medium text-slate-800">{item.projectName}</div>
                  <div className="text-xs text-slate-400 mt-0.5">
                    {item.projectCode} · {item.category} · {item.manager}
                  </div>
                </td>
                <td className="py-4 px-4">
                  <span className="text-slate-700">¥{item.contractAmount.toLocaleString()}</span>
                </td>
                <td className="py-4 px-4">
                  <span className="text-slate-600">{item.completedPhases}</span>
                </td>
                <td className="py-4 px-4">
                  <span className="font-medium text-slate-800">¥{item.expectedAmount.toLocaleString()}</span>
                </td>
                <td className="py-4 px-4">
                  <span className="font-medium text-emerald-600">¥{item.collectedAmount.toLocaleString()}</span>
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
                  <Badge variant="secondary" className={cn("text-xs", statusStyles[item.status])}>
                    {item.status}
                  </Badge>
                </td>
                <td className="py-4 px-4 text-center">
                  <button
                    onClick={() => openDetail(item)}
                    className="text-blue-600 hover:text-blue-700 text-sm font-medium"
                  >
                    查看详情
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Detail Modal */}
      {showDetailModal && selectedProject && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold text-slate-800">回款详情</h2>
                <p className="text-slate-500 text-sm mt-1">{selectedProject.projectName}</p>
              </div>
              <button onClick={() => setShowDetailModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-6 h-6" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto flex-1">
              {/* Project Stats */}
              <div className="grid grid-cols-4 gap-4 mb-6">
                <div className="bg-slate-50 rounded-xl p-4">
                  <p className="text-slate-500 text-sm">合同金额</p>
                  <p className="text-xl font-bold text-slate-800 mt-1">
                    ¥{selectedProject.contractAmount.toLocaleString()}
                  </p>
                </div>
                <div className="bg-blue-50 rounded-xl p-4">
                  <p className="text-blue-600 text-sm">应收金额</p>
                  <p className="text-xl font-bold text-blue-700 mt-1">
                    ¥{selectedProject.expectedAmount.toLocaleString()}
                  </p>
                </div>
                <div className="bg-emerald-50 rounded-xl p-4">
                  <p className="text-emerald-600 text-sm">已回款金额</p>
                  <p className="text-xl font-bold text-emerald-700 mt-1">
                    ¥{selectedProject.collectedAmount.toLocaleString()}
                  </p>
                </div>
                <div className="bg-amber-50 rounded-xl p-4">
                  <p className="text-amber-600 text-sm">回款率</p>
                  <p className="text-xl font-bold text-amber-700 mt-1">{selectedProject.collectionRate}%</p>
                </div>
              </div>

              {/* Progress Bar */}
              <div className="mb-6">
                <h3 className="text-sm font-semibold text-slate-700 mb-3">回款进度</h3>
                <div className="h-4 bg-slate-100 rounded-full overflow-hidden">
                  <div
                    className={cn("h-full rounded-full transition-all", getRateBarColor(selectedProject.collectionRate))}
                    style={{ width: `${Math.min(selectedProject.collectionRate, 100)}%` }}
                  />
                </div>
                <div className="flex justify-between mt-2 text-sm text-slate-500">
                  <span>已回款 ¥{selectedProject.collectedAmount.toLocaleString()}</span>
                  <span>
                    待回款 ¥{(selectedProject.expectedAmount - selectedProject.collectedAmount).toLocaleString()}
                  </span>
                </div>
              </div>

              {/* Records Table */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-semibold text-slate-700">回款记录明细</h3>
                  <button className="text-blue-600 text-sm font-medium flex items-center gap-1 hover:text-blue-700">
                    <Plus className="w-4 h-4" /> 新增回款记录
                  </button>
                </div>

                {selectedProject.records.length > 0 ? (
                  <table className="w-full">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">阶段</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">计划回款</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">计划日期</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">实际回款</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">实际日期</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-slate-600">凭证</th>
                        <th className="text-center py-3 px-4 text-sm font-medium text-slate-600">状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedProject.records.map((record) => (
                        <tr key={record.id} className="border-b border-slate-100">
                          <td className="py-3 px-4 text-sm text-slate-700">{record.phase}</td>
                          <td className="py-3 px-4 text-sm text-slate-700">¥{record.planAmount.toLocaleString()}</td>
                          <td className="py-3 px-4 text-sm text-slate-500">{record.planDate}</td>
                          <td className="py-3 px-4 text-sm font-medium text-emerald-600">
                            ¥{record.actualAmount.toLocaleString()}
                          </td>
                          <td className="py-3 px-4 text-sm text-slate-500">{record.actualDate || "-"}</td>
                          <td className="py-3 px-4">
                            {record.voucher === "已上传" ? (
                              <span className="text-blue-600 text-sm cursor-pointer hover:underline">查看凭证</span>
                            ) : (
                              <span className="text-slate-400 text-sm">未上传</span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-center">
                            {record.actualAmount >= record.planAmount ? (
                              <span className="inline-flex items-center gap-1 text-emerald-600 text-sm">
                                <CheckCircle className="w-4 h-4" /> 已完成
                              </span>
                            ) : record.actualAmount > 0 ? (
                              <span className="inline-flex items-center gap-1 text-amber-600 text-sm">
                                <Clock className="w-4 h-4" /> 部分回款
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-rose-600 text-sm">
                                <AlertCircle className="w-4 h-4" /> 待回款
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="text-center py-8 text-slate-400">
                    <Banknote className="w-12 h-12 mx-auto mb-2" />
                    <p>暂无回款记录</p>
                  </div>
                )}
              </div>
            </div>

            <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
              <button
                onClick={() => setShowDetailModal(false)}
                className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-lg overflow-hidden">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <h2 className="text-xl font-bold text-slate-800">录入回款</h2>
              <button onClick={() => setShowAddModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-6 h-6" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  选择项目 <span className="text-rose-500">*</span>
                </label>
                <select className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="">请选择项目</option>
                  {collectionData.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.projectName} ({c.projectCode})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  回款阶段 <span className="text-rose-500">*</span>
                </label>
                <select className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="">请选择阶段</option>
                  <option value="1">阶段1 - 方案计划编制</option>
                  <option value="2">阶段2 - 初稿编制</option>
                  <option value="3">阶段3 - 终稿编制</option>
                  <option value="4">阶段4 - 核对</option>
                  <option value="5">阶段5 - 后期配合</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">
                    回款金额 <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="number"
                    placeholder="请输入金额"
                    className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">
                    回款日期 <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="date"
                    className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">上传凭证</label>
                <div className="border-2 border-dashed border-slate-200 rounded-lg p-6 text-center hover:border-blue-400 transition-colors cursor-pointer">
                  <UploadCloud className="w-10 h-10 text-slate-300 mx-auto mb-2" />
                  <p className="text-sm text-slate-500">点击或拖拽上传回款凭证</p>
                  <p className="text-xs text-slate-400 mt-1">支持 JPG、PNG、PDF 格式</p>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">备注</label>
                <textarea
                  rows={2}
                  placeholder="请输入备注信息（选填）"
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                />
              </div>
            </div>

            <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
              <button
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                取消
              </button>
              <button className="px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors">
                确认提交
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
