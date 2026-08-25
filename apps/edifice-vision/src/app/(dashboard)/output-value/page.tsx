"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Clock,
  CheckCircle,
  Banknote,
  FileText,
  ChevronDown,
  ChevronUp,
  Download,
  Loader2,
  Plus,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { getUserList } from "@/services/project";
import {
  getOutputValueList,
  getOutputValueStats,
  confirmOutputValue,
  approveOutputValue,
  payOutputValue,
  terminateOutputValue,
  exportOutputValueExcel,
} from "@/services/output-value";
import { getEnabledFlowConfig } from "@/services/config-center";
import { ResponseCode } from "@/types/api";
import type { OutputValueVo, OutputValueStats } from "@/types/output-value";
import type { UserListItem } from "@/types/project";
import type { ApprovalFlowConfigVo } from "@/types/config-center";
import {
  OUTPUT_VALUE_STATUS_MAP,
  WORK_TYPE_LABELS,
  DIST_TYPE_LABELS,
} from "@/types/output-value";
import { CreateOutputValueDialog } from "@/components/output-value/create-output-value-dialog";
import { useDetailLink } from "@/hooks/use-detail-link";

type TabKey = "all" | "pending" | "review" | "approved" | "paid";
type ActionKind = "confirm" | "approve" | "pay" | "terminate";

const statusFilterMap: Record<TabKey, number | undefined> = {
  all: undefined,
  pending: 0,
  review: 1,
  approved: 2,
  paid: 3,
};

const statusStyles: Record<string, string> = {
  待确认: "bg-slate-100 text-slate-600",
  待审核: "bg-amber-100 text-amber-600",
  已审批: "bg-blue-100 text-blue-600",
  已发放: "bg-emerald-100 text-emerald-600",
};

const workTypeStyles: Record<number, string> = {
  0: "bg-blue-100 text-blue-600",
  1: "bg-emerald-100 text-emerald-600",
  2: "bg-amber-100 text-amber-600",
};

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.replace("T", " ").slice(0, 16);
}

function formatAmount(amount: number): string {
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(2)}万`;
  return `¥${amount.toLocaleString()}`;
}

export default function OutputValuePage() {
  const [activeTab, setActiveTab] = useState<TabKey>("all");
  const [searchText, setSearchText] = useState("");
  const [items, setItems] = useState<OutputValueVo[]>([]);
  const [stats, setStats] = useState<OutputValueStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [actionTarget, setActionTarget] = useState<OutputValueVo | null>(null);
  const [actionKind, setActionKind] = useState<ActionKind | null>(null);
  const [nextUserId, setNextUserId] = useState("");
  // 产值分配的流程配置：用于决定「确认/审批」节点是否允许终审
  const [flowConfig, setFlowConfig] = useState<ApprovalFlowConfigVo | null>(null);
  useDetailLink(setExpandedId);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [listRes, statsRes, usersRes, flowRes] = await Promise.all([
        getOutputValueList(statusFilterMap[activeTab]),
        getOutputValueStats(),
        getUserList(),
        getEnabledFlowConfig("output"),
      ]);
      if (listRes.code === ResponseCode.SUCCESS) {
        setItems(listRes.data ?? []);
      }
      if (statsRes.code === ResponseCode.SUCCESS) {
        setStats(statsRes.data ?? null);
      }
      if (usersRes.code === ResponseCode.SUCCESS && usersRes.data) {
        setUsers(usersRes.data.records ?? []);
      }
      if (flowRes.code === ResponseCode.SUCCESS && flowRes.data) {
        setFlowConfig(flowRes.data);
      }
    } catch { /* 静默 */ }
    finally { setLoading(false); }
  }, [activeTab]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const filtered = items.filter((item) => {
    if (!searchText) return true;
    return (item.projectName ?? "").includes(searchText) || (item.projectCode ?? "").includes(searchText);
  });

  const openAction = (item: OutputValueVo, action: ActionKind) => {
    setActionTarget(item);
    setActionKind(action);
    setNextUserId("");
  };

  const closeAction = () => {
    setActionTarget(null);
    setActionKind(null);
    setNextUserId("");
  };

  /** 当前节点是否允许终审：产值分配 status 0=L1（确认），1=L2（审批） */
  const canTerminate = (status: number | undefined | null): boolean => {
    if (!flowConfig) return false;
    const nodeOrder = status === 0 ? 1 : status === 1 ? 2 : null;
    if (nodeOrder == null) return false;
    const node = flowConfig.nodes.find((n) => n.nodeOrder === nodeOrder);
    return node?.allowTerminate === 1;
  };

  const handleAction = async () => {
    if (!actionTarget || !actionKind) return;
    if ((actionKind === "confirm" || actionKind === "approve") && !nextUserId) {
      toast.error(actionKind === "confirm" ? "请选择审批人" : "请选择发放人");
      return;
    }

    const id = actionTarget.outputValueId;
    setActionLoading(id);
    try {
      const labelMap: Record<ActionKind, string> = {
        confirm: "确认成功",
        approve: "审批通过",
        pay: "发放成功",
        terminate: "终审通过，分配单已结束",
      };
      const res =
        actionKind === "confirm"
          ? await confirmOutputValue(id, nextUserId)
          : actionKind === "approve"
            ? await approveOutputValue(id, nextUserId)
            : actionKind === "pay"
              ? await payOutputValue(id)
              : await terminateOutputValue(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(labelMap[actionKind]);
        closeAction();
        fetchData();
      }
      // 业务错误由 request.ts 统一提示
    } catch {
      /* 网络错误由 request.ts 提示 */
    }
    finally { setActionLoading(null); }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportOutputValueExcel(statusFilterMap[activeTab], searchText);
      toast.success("导出成功");
    } catch {
      toast.error("导出失败，请稍后重试");
    } finally {
      setExporting(false);
    }
  };

  // tab 角标来自统计接口，不受当前 tab 列表过滤影响
  const tabs: { key: TabKey; label: string; count: number }[] = [
    { key: "all", label: "全部", count: stats?.totalCount ?? items.length },
    { key: "pending", label: "待确认", count: stats?.confirmCount ?? items.filter((i) => i.status === 0).length },
    { key: "review", label: "待审核", count: stats?.reviewCount ?? items.filter((i) => i.status === 1).length },
    { key: "approved", label: "已审批", count: stats?.approvedCount ?? items.filter((i) => i.status === 2).length },
    { key: "paid", label: "已发放", count: stats?.paidCount ?? items.filter((i) => i.status === 3).length },
  ];
  const actionTitle =
    actionKind === "confirm"
      ? "确认分配单"
      : actionKind === "approve"
        ? "审批分配单"
        : actionKind === "pay"
          ? "发放产值"
          : "终审分配单";
  const nextUserLabel = actionKind === "confirm" ? "审批人" : actionKind === "approve" ? "发放人" : "";

  return (
    <div className="p-4 md:p-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">产值分配</h1>
          <p className="text-slate-500 text-sm mt-1">
            按项目类型和阶段规则拆分管理、基础、智励工作资金池，并按兑现比例核算。
          </p>
        </div>
        <div className="flex flex-col sm:flex-row gap-2">
          <Button
            variant="outline"
            className="bg-white"
            onClick={handleExport}
            disabled={exporting}
          >
            {exporting ? (
              <Loader2 className="w-4 h-4 mr-1 animate-spin" />
            ) : (
              <Download className="w-4 h-4 mr-1" />
            )}
            导出 Excel
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            onClick={() => setCreateOpen(true)}
          >
            <Plus className="w-4 h-4 mr-1" /> 新建分配单
          </Button>
        </div>
      </div>

      <CreateOutputValueDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={fetchData}
      />

      <Dialog open={!!actionTarget} onOpenChange={(open) => !open && closeAction()}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{actionTitle}</DialogTitle>
            <DialogDescription>
              {actionTarget?.projectName} · {actionTarget?.stageName}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 mt-2">
            <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
              当前办理人：{actionTarget?.currentHandlerName || "-"}
            </div>
            {(actionKind === "confirm" || actionKind === "approve") && (
              <div>
                <label className="text-xs font-medium text-slate-600 mb-1 block">
                  指定{nextUserLabel} <span className="text-rose-500">*</span>
                </label>
                <select
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                  value={nextUserId}
                  onChange={(e) => setNextUserId(e.target.value)}
                >
                  <option value="">请选择{nextUserLabel}</option>
                  {users.map((user) => (
                    <option key={user.userId} value={user.userId}>
                      {user.realName || user.username}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {actionKind === "pay" && (
              <div className="rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                确认后该分配单将进入已发放状态。
              </div>
            )}
            {actionKind === "terminate" && (
              <div className="rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                终审通过后，分配单将跳过后续环节，直接进入已发放状态。此操作不可撤销。
              </div>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="outline" onClick={closeAction} disabled={!!actionLoading}>
              取消
            </Button>
            <Button
              className={
                actionKind === "terminate"
                  ? "bg-rose-600 hover:bg-rose-700 text-white"
                  : "bg-blue-600 hover:bg-blue-700 text-white"
              }
              onClick={handleAction}
              disabled={!!actionLoading}
            >
              {actionLoading && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
              {actionKind === "terminate" ? "终审通过" : "确认"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {loading && <TablePageSkeleton columns={4} rows={4} />}

      {!loading && (
        <>
          {/* Stats */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard icon={<Clock className="w-5 h-5" />} label="待处理" value={stats?.pendingCount ?? 0} color="amber" />
            <StatCard icon={<CheckCircle className="w-5 h-5" />} label="已审批待发放" value={stats?.approvedCount ?? 0} color="blue" />
            <StatCard icon={<Banknote className="w-5 h-5" />} label="已发放产值" value={formatAmount(stats?.paidAmount ?? 0)} color="emerald" />
            <StatCard icon={<FileText className="w-5 h-5" />} label="产值总额" value={formatAmount(stats?.totalAmount ?? 0)} color="slate" />
          </div>

          {/* Tabs + Search */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
              {tabs.map((item) => (
                <button key={item.key} onClick={() => setActiveTab(item.key)}
                  className={cn("px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2",
                    activeTab === item.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700")}>
                  {item.label}
                  <span className={cn("text-xs px-1.5 py-0.5 rounded-full",
                    activeTab === item.key ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-500")}>
                    {item.count}
                  </span>
                </button>
              ))}
            </div>
            <div className="flex-1" />
            <input type="text" placeholder="搜索项目名称或编码..." value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              className="pl-4 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-full sm:w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
          </div>

          {/* List */}
          {filtered.length > 0 ? (
            <div className="space-y-4">
              {filtered.map((item) => {
                const statusLabel = OUTPUT_VALUE_STATUS_MAP[item.status] ?? "未知";
                const isExpanded = expandedId === item.outputValueId;
                const usesWorkPools = item.allocationVersion?.startsWith("allocation_v") ?? false;

                return (
                  <div key={item.outputValueId} className="glass-card rounded-2xl shadow-sm overflow-x-auto">
                    <button onClick={() => setExpandedId(isExpanded ? null : item.outputValueId)}
                      className="w-full flex items-center gap-4 p-5 text-left hover:bg-slate-50/50 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm font-semibold text-slate-800">{item.projectName}</span>
                          <span className="text-xs text-slate-400">{item.projectCode}</span>
                          {item.quarter && (
                            <span className="text-xs px-1.5 py-0.5 rounded bg-blue-50 text-blue-600 font-medium">
                              {item.quarter}
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-xs text-slate-500">
                          {item.projectTypeName && <span>{item.projectTypeName}</span>}
                          <span>·</span>
                          <span>{item.stageName}</span>
                          {item.stageOutput > 0 && (
                            <><span>·</span><span>产值比例 {item.stageOutput}%</span></>
                          )}
                          {item.stageCompletionRatio != null && item.stageCompletionRatio < 100 && (
                            <><span>·</span><span className="text-amber-600">阶段完成 {item.stageIncrementalRatio ?? item.stageCompletionRatio}%</span></>
                          )}
                          {(item.coefficient ?? 1) !== 1 && (
                            <><span>·</span><span className="text-blue-600">系数 ×{item.coefficient}</span></>
                          )}
                        </div>
                      </div>
                      <div className="text-right mr-4">
                        <p className="text-lg font-bold text-slate-800">{formatAmount(item.totalAmount)}</p>
                      </div>
                      <Badge variant="secondary" className={cn("text-xs font-medium", statusStyles[statusLabel] ?? "")}>
                        {statusLabel}
                      </Badge>
                      {isExpanded ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
                    </button>

                    {isExpanded && (
                      <div className="px-5 pb-5 border-t border-slate-100">
                        {/* 产值与人员分配金额快照 */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-4 text-xs">
                          <BreakdownBox label="当前阶段产值" value={item.currentStageAmount ?? item.stageCumulativeAmount ?? 0} tone="blue" />
                          <BreakdownBox label="历史补差合计" value={item.adjustmentAmount ?? item.previousCumulativeAmount ?? 0} tone="amber" />
                          <BreakdownBox
                            label={usesWorkPools ? "项目人员可分配" : "员工池"}
                            value={usesWorkPools
                              ? (item.projectPoolAmount ?? 0)
                              : Math.round((item.totalAmount ?? 0) * 0.4 * 100) / 100}
                            tone="slate"
                          />
                          <BreakdownBox label="公司留存" value={item.companyReserve ?? 0} tone="rose" />
                        </div>
                        {item.stageCumulativeAmount != null && (
                          <p className="text-xs text-slate-400 mt-2">
                            当前阶段 ¥{(item.currentStageAmount ?? item.stageCumulativeAmount ?? 0).toLocaleString()}
                            · 基本部分 ¥{(item.baseAmountPart ?? 0).toLocaleString()}
                            · 效益部分 ¥{(item.benefitAmountPart ?? 0).toLocaleString()}
                            {item.benefitSnapshot != null && (
                              <> · 创建时效益值 ¥{(item.benefitSnapshot ?? 0).toLocaleString()}</>
                            )}
                          </p>
                        )}

                        {(item.workPools ?? []).length > 0 && (
                          <div className="mt-4 border border-slate-200 rounded-lg overflow-x-auto">
                            <div className="px-3 py-2 bg-slate-50 text-sm font-semibold text-slate-700">
                              项目人员分配资金池
                              <span className="ml-2 text-xs font-normal text-slate-400">
                                {item.allocationVersion} · 规则 #{item.allocationRuleVersionId}
                              </span>
                            </div>
                            <table className="w-full min-w-[620px] text-xs">
                              <thead className="text-slate-500">
                                <tr>
                                  <th className="text-left py-2 px-3 font-medium">工作类型</th>
                                  <th className="text-right py-2 px-3 font-medium">阶段工作权重</th>
                                  <th className="text-right py-2 px-3 font-medium">项目人员占本阶段</th>
                                  <th className="text-right py-2 px-3 font-medium">项目人员可分配金额</th>
                                </tr>
                              </thead>
                              <tbody>
                                {(item.workPools ?? []).map((pool) => (
                                  <tr key={pool.workPoolId ?? pool.workType} className="border-t border-slate-100">
                                    <td className="py-2 px-3 text-slate-700">{pool.workTypeName}</td>
                                    <td className="py-2 px-3 text-right">{pool.workWeight}%</td>
                                    <td className="py-2 px-3 text-right text-blue-700">{pool.projectRate}%</td>
                                    <td className="py-2 px-3 text-right text-blue-700">{formatAmount(pool.projectAmount)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}

                        {(item.adjustmentDetails ?? []).length > 0 && (
                          <div className="mt-4 rounded-xl border border-amber-100 bg-amber-50/40 overflow-x-auto">
                            <div className="px-3 py-2 text-sm font-semibold text-slate-700">
                              历史补差明细
                            </div>
                            <table className="w-full text-xs bg-white">
                              <thead className="bg-amber-50 text-slate-500">
                                <tr>
                                  <th className="text-left py-2 px-3 font-medium">历史阶段</th>
                                  <th className="text-right py-2 px-3 font-medium">原阶段金额</th>
                                  <th className="text-right py-2 px-3 font-medium">重算金额</th>
                                  <th className="text-right py-2 px-3 font-medium">已补/扣</th>
                                  <th className="text-right py-2 px-3 font-medium">本次补/扣</th>
                                </tr>
                              </thead>
                              <tbody>
                                {item.adjustmentDetails.map((detail) => (
                                  <tr key={detail.adjustmentDetailId ?? detail.sourceOutputValueId} className="border-t border-amber-50">
                                    <td className="py-2 px-3 text-slate-700">
                                      {detail.sourceStageName || "-"}
                                      <span className="ml-1 text-slate-400">
                                        {detail.sourceBaseRatio}%
                                      </span>
                                    </td>
                                    <td className="py-2 px-3 text-right text-slate-600">
                                      {formatAmount(detail.oldStageAmount)}
                                    </td>
                                    <td className="py-2 px-3 text-right text-slate-600">
                                      {formatAmount(detail.newStageAmount)}
                                    </td>
                                    <td className="py-2 px-3 text-right text-slate-600">
                                      {formatAmount(detail.alreadyAdjustedAmount)}
                                    </td>
                                    <td
                                      className={cn(
                                        "py-2 px-3 text-right font-semibold",
                                        detail.adjustmentAmount < 0 ? "text-rose-600" : "text-emerald-700",
                                      )}
                                    >
                                      {formatAmount(detail.adjustmentAmount)}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}

                        <table className="w-full mt-4">
                          <thead>
                            <tr className="text-xs text-slate-400 uppercase tracking-wider">
                              <th className="text-left py-2 font-semibold">分配人员</th>
                              <th className="text-left py-2 font-semibold">角色</th>
                              <th className="text-left py-2 font-semibold">工作类型</th>
                              <th className="text-center py-2 font-semibold">角色内分配%</th>
                              <th className="text-right py-2 font-semibold">计划金额</th>
                              <th className="text-center py-2 font-semibold">兑现%</th>
                              <th className="text-center py-2 font-semibold">类型</th>
                              <th className="text-right py-2 font-semibold">实得金额</th>
                            </tr>
                          </thead>
                          <tbody className="text-sm">
                            {item.distributions.map((d) => (
                              <tr key={d.distributionId} className="border-t border-slate-50">
                                <td className="py-3">
                                  <div className="flex items-center gap-2">
                                    <div className="w-6 h-6 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                                      {(d.userName ?? "?")[0]}
                                    </div>
                                    <span className="text-slate-700">{d.userName || "-"}</span>
                                    {d.isActive === 0 && (
                                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-50 text-rose-500">离职</span>
                                    )}
                                  </div>
                                </td>
                                <td className="py-3 text-slate-500">{d.userRole}</td>
                                <td className="py-3">
                                  <span className={cn("text-xs px-2 py-0.5 rounded-full font-medium", workTypeStyles[d.workType] ?? "")}>
                                    {WORK_TYPE_LABELS[d.workType] ?? "-"}
                                  </span>
                                </td>
                                <td className="py-3 text-center text-slate-600">
                                  {d.roleAllocRatio ?? d.allocRatio ?? d.ratio ?? 0}%
                                </td>
                                <td className="py-3 text-right text-slate-600">
                                  {d.plannedAmount == null ? "-" : formatAmount(d.plannedAmount)}
                                </td>
                                <td className="py-3 text-center text-slate-600">
                                  {d.completionRatio ?? 100}%
                                </td>
                                <td className="py-3 text-center text-slate-500 text-xs">
                                  {DIST_TYPE_LABELS[d.distType ?? 0] ?? "-"}
                                </td>
                                <td className="py-3 text-right font-semibold text-slate-800">{formatAmount(d.amount)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>

                        <div className="flex gap-6 mt-4 pt-4 border-t border-slate-100 text-xs text-slate-400">
                          <span>提交时间：{formatDate(item.submitTime)}</span>
                          {item.confirmUserName && <span>确认人：{item.confirmUserName}</span>}
                          {item.approveUserName && <span>审批人：{item.approveUserName}</span>}
                          {item.payUserName && <span>发放人：{item.payUserName}</span>}
                          {item.currentHandlerName && <span>当前办理：{item.currentHandlerName}</span>}
                          {item.approvedTime && <span>审批时间：{formatDate(item.approvedTime)}</span>}
                          {item.paidTime && <span>发放时间：{formatDate(item.paidTime)}</span>}
                        </div>

                        <div className="flex justify-end gap-3 mt-4">
                          {item.status === 0 && (
                            <Button className="bg-emerald-600 hover:bg-emerald-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => openAction(item, "confirm")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              确认分配
                            </Button>
                          )}
                          {item.status === 0 && canTerminate(item.status) && (
                            <Button className="bg-rose-600 hover:bg-rose-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => openAction(item, "terminate")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              终审通过
                            </Button>
                          )}
                          {item.status === 1 && (
                            <Button className="bg-emerald-600 hover:bg-emerald-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => openAction(item, "approve")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              审批通过
                            </Button>
                          )}
                          {item.status === 1 && canTerminate(item.status) && (
                            <Button className="bg-rose-600 hover:bg-rose-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => openAction(item, "terminate")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              终审通过
                            </Button>
                          )}
                          {item.status === 2 && (
                            <Button className="bg-blue-600 hover:bg-blue-700 text-white" disabled={actionLoading === item.outputValueId}
                              onClick={() => openAction(item, "pay")}>
                              {actionLoading === item.outputValueId ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                              发放产值
                            </Button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="glass-card rounded-2xl py-16 text-center">
              <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <Banknote className="w-8 h-8 text-slate-400" />
              </div>
              <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无产值分配记录</h3>
              <p className="text-sm text-slate-500">当前筛选条件下没有找到数据</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatCard({ icon, label, value, color }: {
  icon: React.ReactNode; label: string; value: string | number; color: string;
}) {
  const colorMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-600", amber: "bg-amber-100 text-amber-600",
    blue: "bg-blue-100 text-blue-600", emerald: "bg-emerald-100 text-emerald-600",
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

function BreakdownBox({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: "slate" | "amber" | "rose" | "blue";
}) {
  const toneMap: Record<string, string> = {
    slate: "bg-slate-50 text-slate-700",
    amber: "bg-amber-50 text-amber-700",
    rose: "bg-rose-50 text-rose-700",
    blue: "bg-blue-50 text-blue-700",
  };
  return (
    <div className={cn("rounded-lg px-3 py-2", toneMap[tone])}>
      <p className="text-[11px] opacity-75">{label}</p>
      <p className="text-sm font-semibold">{formatAmount(value ?? 0)}</p>
    </div>
  );
}
