"use client";

import { useState } from "react";
import {
  Filter,
  Download,
  Search,
  Clock,
  UserCheck,
  CheckCircle,
  XCircle,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  X,
  Check,
  FileText,
  Eye,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { inspectionsData } from "@/data/mock-data";
import type { InspectionStatus } from "@/types";

type TabKey = "pending" | "passed" | "rejected" | "all";

const statusStyles: Record<InspectionStatus, string> = {
  待初审: "bg-amber-100 text-amber-600",
  待终审: "bg-blue-100 text-blue-600",
  已通过: "bg-emerald-100 text-emerald-600",
  已驳回: "bg-rose-100 text-rose-600",
};

export default function InspectionApprovalPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("pending");
  const [searchText, setSearchText] = useState("");
  const [selectedInspection, setSelectedInspection] = useState<
    (typeof inspectionsData)[0] | null
  >(null);
  const [showModal, setShowModal] = useState(false);

  const stats = {
    pending: inspectionsData.filter(
      (i) => i.status === "待初审" || i.status === "待终审"
    ).length,
    pendingFirst: inspectionsData.filter((i) => i.status === "待初审").length,
    passed: inspectionsData.filter((i) => i.status === "已通过").length,
    rejected: inspectionsData.filter((i) => i.status === "已驳回").length,
  };

  const filteredInspections = inspectionsData
    .filter((i) => {
      if (activeTab === "pending")
        return i.status === "待初审" || i.status === "待终审";
      if (activeTab === "passed") return i.status === "已通过";
      if (activeTab === "rejected") return i.status === "已驳回";
      return true;
    })
    .filter((i) => {
      if (!searchText) return true;
      return (
        i.projectName.includes(searchText) ||
        i.code.includes(searchText) ||
        i.submitter.includes(searchText)
      );
    });

  const openDetail = (inspection: (typeof inspectionsData)[0]) => {
    setSelectedInspection(inspection);
    setShowModal(true);
  };

  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "pending", label: "待审批", count: stats.pending },
    { key: "passed", label: "已通过", count: stats.passed },
    { key: "rejected", label: "已驳回", count: stats.rejected },
    { key: "all", label: "全部", count: inspectionsData.length },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            验工审批
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            审核项目阶段验工申请，确保工作质量与文档完整性。
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="flex items-center gap-2">
            <Filter className="w-4 h-4" /> 高级筛选
          </Button>
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="w-4 h-4" /> 导出
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-amber-100 text-amber-600 rounded-lg">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">待审批</p>
              <p className="text-xl font-bold text-slate-800">{stats.pending}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
              <UserCheck className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">待初审</p>
              <p className="text-xl font-bold text-slate-800">
                {stats.pendingFirst}
              </p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-emerald-100 text-emerald-600 rounded-lg">
              <CheckCircle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">已通过</p>
              <p className="text-xl font-bold text-slate-800">{stats.passed}</p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-rose-100 text-rose-600 rounded-lg">
              <XCircle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">已驳回</p>
              <p className="text-xl font-bold text-slate-800">
                {stats.rejected}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {tabs.map((item) => (
            <button
              key={item.key}
              onClick={() => setActiveTab(item.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
                activeTab === item.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
              <span
                className={cn(
                  "text-xs px-1.5 py-0.5 rounded-full",
                  activeTab === item.key
                    ? "bg-blue-500 text-white"
                    : "bg-slate-100 text-slate-500"
                )}
              >
                {item.count}
              </span>
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="搜索验工单号、项目或发起人..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Table */}
      <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
        <table className="w-full">
          <thead className="bg-slate-50/50">
            <tr className="text-slate-500 text-xs uppercase tracking-wider">
              <th className="text-left py-4 px-6 font-semibold">验工单信息</th>
              <th className="text-left py-4 px-4 font-semibold">项目阶段</th>
              <th className="text-left py-4 px-4 font-semibold">阶段产值</th>
              <th className="text-left py-4 px-4 font-semibold">发起人</th>
              <th className="text-left py-4 px-4 font-semibold">发起时间</th>
              <th className="text-center py-4 px-4 font-semibold">状态</th>
              <th className="text-right py-4 px-6 font-semibold">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filteredInspections.map((item) => (
              <tr
                key={item.id}
                className="hover:bg-slate-50/50 transition-colors"
              >
                <td className="py-4 px-6">
                  <div className="flex items-center gap-3">
                    {item.urgent && (
                      <span className="w-2 h-2 bg-rose-500 rounded-full animate-pulse" />
                    )}
                    <div>
                      <p className="text-sm font-semibold text-slate-800">
                        {item.projectName}
                      </p>
                      <p className="text-xs text-slate-400 mt-0.5">
                        {item.code}
                      </p>
                    </div>
                  </div>
                </td>
                <td className="py-4 px-4">
                  <span className="text-sm text-slate-600">
                    {item.phase} · {item.phaseName}
                  </span>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {item.category} · 产值比例 {item.phaseRatio}
                  </p>
                </td>
                <td className="py-4 px-4">
                  <span className="text-sm font-semibold text-slate-800">
                    ¥{(item.phaseAmount / 10000).toFixed(2)}万
                  </span>
                </td>
                <td className="py-4 px-4">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                      {item.submitter[0]}
                    </div>
                    <span className="text-sm text-slate-600">
                      {item.submitter}
                    </span>
                  </div>
                </td>
                <td className="py-4 px-4">
                  <span className="text-sm text-slate-500">
                    {item.submitTime}
                  </span>
                </td>
                <td className="py-4 px-4 text-center">
                  <Badge
                    variant="secondary"
                    className={cn("text-xs font-medium", statusStyles[item.status])}
                  >
                    {item.status}
                  </Badge>
                </td>
                <td className="py-4 px-6 text-right">
                  <div className="flex items-center justify-end gap-2">
                    <button
                      onClick={() => openDetail(item)}
                      className="px-3 py-1.5 text-xs text-slate-600 font-medium bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
                    >
                      查看详情
                    </button>
                    {(item.status === "待初审" || item.status === "待终审") && (
                      <button
                        onClick={() => openDetail(item)}
                        className="px-3 py-1.5 text-xs text-white font-medium bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                      >
                        审批
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* Empty */}
        {filteredInspections.length === 0 && (
          <div className="py-16 text-center">
            <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <ClipboardCheck className="w-8 h-8 text-slate-400" />
            </div>
            <h3 className="text-lg font-semibold text-slate-800 mb-2">
              暂无验工单
            </h3>
            <p className="text-sm text-slate-500">
              当前筛选条件下没有找到验工单
            </p>
          </div>
        )}
      </div>

      {/* Pagination */}
      {filteredInspections.length > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共{" "}
            <span className="font-semibold text-slate-800">
              {filteredInspections.length}
            </span>{" "}
            条记录
          </p>
          <div className="flex items-center gap-2">
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button className="px-3 py-1.5 text-sm font-medium bg-blue-600 text-white rounded-lg">
              1
            </button>
            <button className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Modal */}
      {showModal && selectedInspection && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-8">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col">
            {/* Header */}
            <div className="p-6 border-b border-slate-100 flex justify-between items-start">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <h2 className="text-xl font-bold text-slate-900">
                    验工单详情
                  </h2>
                  <Badge
                    variant="secondary"
                    className={cn(
                      "text-xs font-medium",
                      statusStyles[selectedInspection.status]
                    )}
                  >
                    {selectedInspection.status}
                  </Badge>
                  {selectedInspection.urgent && (
                    <span className="text-xs bg-rose-500 text-white px-2 py-0.5 rounded font-medium">
                      加急
                    </span>
                  )}
                </div>
                <p className="text-sm text-slate-500">
                  {selectedInspection.code}
                </p>
              </div>
              <button
                onClick={() => setShowModal(false)}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Info */}
              <div className="grid grid-cols-2 gap-6">
                <div className="space-y-4">
                  <div>
                    <p className="text-xs text-slate-400 mb-1">项目名称</p>
                    <p className="text-sm font-medium text-slate-800">
                      {selectedInspection.projectName}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-400 mb-1">项目分类</p>
                    <p className="text-sm font-medium text-slate-800">
                      {selectedInspection.category}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-400 mb-1">发起人</p>
                    <p className="text-sm font-medium text-slate-800">
                      {selectedInspection.submitter}
                    </p>
                  </div>
                </div>
                <div className="space-y-4">
                  <div>
                    <p className="text-xs text-slate-400 mb-1">验工阶段</p>
                    <p className="text-sm font-medium text-slate-800">
                      {selectedInspection.phase} · {selectedInspection.phaseName}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-400 mb-1">阶段产值</p>
                    <p className="text-sm font-medium text-slate-800">
                      ¥{selectedInspection.phaseAmount.toLocaleString()} （
                      {selectedInspection.phaseRatio}）
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-slate-400 mb-1">发起时间</p>
                    <p className="text-sm font-medium text-slate-800">
                      {selectedInspection.submitTime}
                    </p>
                  </div>
                </div>
              </div>

              {/* Description */}
              <div>
                <p className="text-xs text-slate-400 mb-2">验工说明</p>
                <div className="p-4 bg-slate-50 rounded-xl text-sm text-slate-600 leading-relaxed">
                  {selectedInspection.description}
                </div>
              </div>

              {/* Attachments */}
              <div>
                <p className="text-xs text-slate-400 mb-2">验收材料</p>
                <div className="space-y-2">
                  {selectedInspection.attachments.map((file, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-3 bg-slate-50 rounded-xl hover:bg-slate-100 transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
                          <FileText className="w-4 h-4" />
                        </div>
                        <span className="text-sm text-slate-700">{file}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <button className="p-1.5 text-slate-400 hover:text-blue-600 transition-colors">
                          <Eye className="w-4 h-4" />
                        </button>
                        <button className="p-1.5 text-slate-400 hover:text-blue-600 transition-colors">
                          <Download className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* History */}
              {selectedInspection.approvalHistory && (
                <div>
                  <p className="text-xs text-slate-400 mb-2">审批记录</p>
                  <div className="space-y-3">
                    {selectedInspection.approvalHistory.map((record, idx) => (
                      <div
                        key={idx}
                        className="flex gap-4 p-4 bg-slate-50 rounded-xl"
                      >
                        <div
                          className={cn(
                            "w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-medium",
                            record.result === "通过"
                              ? "bg-emerald-500"
                              : "bg-rose-500"
                          )}
                        >
                          {record.result === "通过" ? (
                            <Check className="w-4 h-4" />
                          ) : (
                            <X className="w-4 h-4" />
                          )}
                        </div>
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-medium text-slate-800">
                              {record.name}
                            </span>
                            <span className="text-xs text-slate-400">
                              {record.role}
                            </span>
                            <Badge
                              variant="secondary"
                              className={cn(
                                "text-xs font-medium",
                                record.result === "通过"
                                  ? "bg-emerald-100 text-emerald-600"
                                  : "bg-rose-100 text-rose-600"
                              )}
                            >
                              {record.result}
                            </Badge>
                          </div>
                          <p className="text-sm text-slate-600">
                            {record.comment}
                          </p>
                          <p className="text-xs text-slate-400 mt-1">
                            {record.time}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Form */}
              {(selectedInspection.status === "待初审" ||
                selectedInspection.status === "待终审") && (
                <div className="border-t border-slate-100 pt-6">
                  <p className="text-sm font-semibold text-slate-800 mb-4">
                    审批操作
                  </p>
                  <div className="space-y-4">
                    <div>
                      <label className="text-xs text-slate-500 mb-2 block">
                        审批意见 <span className="text-rose-500">*</span>
                      </label>
                      <textarea
                        className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                        rows={3}
                        placeholder="请输入审批意见..."
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
              <Button variant="outline" onClick={() => setShowModal(false)}>
                关闭
              </Button>
              {(selectedInspection.status === "待初审" ||
                selectedInspection.status === "待终审") && (
                <>
                  <Button
                    variant="outline"
                    className="text-rose-600 bg-rose-50 hover:bg-rose-100 border-rose-200"
                  >
                    <X className="w-4 h-4 mr-2" /> 驳回
                  </Button>
                  <Button className="bg-emerald-600 hover:bg-emerald-700">
                    <Check className="w-4 h-4 mr-2" /> 通过
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
