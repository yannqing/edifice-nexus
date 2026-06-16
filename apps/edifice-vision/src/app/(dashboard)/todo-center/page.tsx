"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  BellRing,
  CheckCircle2,
  Check,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock3,
  Eye,
  Inbox,
  Loader2,
  RotateCcw,
  Search,
  Send,
  UserRoundCheck,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import { approvalInspection } from "@/services/inspection";
import { approveAcceptance } from "@/services/acceptance";
import { approveBid } from "@/services/bid";
import { approveOaApplication } from "@/services/oa";
import { approveProjectFile } from "@/services/project-file";
import { getUserList } from "@/services/project";
import {
  createApprovalCc,
  getTodoCenterDetail,
  getTodoCenterList,
  getTodoCenterStats,
  urgeApproval,
  withdrawApproval,
} from "@/services/todo-center";
import { ResponseCode } from "@/types/api";
import type { ApprovalRecordVo } from "@/types/approval";
import type { UserListItem } from "@/types/project";
import type { TodoCenterDetail, TodoCenterItem, TodoCenterStats, TodoCenterTab } from "@/types/todo-center";

const PAGE_SIZE = 10;

const tabs: Array<{ key: TodoCenterTab; label: string; icon: typeof Inbox }> = [
  { key: "pending", label: "待我处理", icon: Inbox },
  { key: "initiated", label: "我发起的", icon: Send },
  { key: "processed", label: "我已处理", icon: UserRoundCheck },
  { key: "cc", label: "抄送我的", icon: Eye },
];

const bizTypeOptions = [
  { value: "all", label: "全部类型" },
  { value: "inspection", label: "验工单" },
  { value: "file", label: "项目文件" },
  { value: "output", label: "产值分配" },
  { value: "timesheet", label: "工时" },
  { value: "bid", label: "投标" },
  { value: "acceptance", label: "验收" },
  { value: "oa_application", label: "OA申请" },
];

const statusOptions = [
  { value: "all", label: "全部状态" },
  { value: "0", label: "待处理/审批中" },
  { value: "1", label: "已通过" },
  { value: "2", label: "已驳回" },
];

const statusStyles: Record<number, string> = {
  0: "bg-amber-100 text-amber-700",
  1: "bg-emerald-100 text-emerald-700",
  2: "bg-rose-100 text-rose-700",
};

const directApprovalBizTypes = new Set(["inspection", "file", "bid", "acceptance", "oa_application"]);
const withdrawSupportedBizTypes = new Set(["inspection", "file", "bid", "acceptance", "oa_application"]);

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

function emptyStats(): TodoCenterStats {
  return {
    pendingCount: 0,
    initiatedCount: 0,
    processedCount: 0,
    ccCount: 0,
    todayPendingCount: 0,
  };
}

export default function TodoCenterPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TodoCenterTab>("pending");
  const [items, setItems] = useState<TodoCenterItem[]>([]);
  const [stats, setStats] = useState<TodoCenterStats>(emptyStats());
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [bizType, setBizType] = useState("all");
  const [status, setStatus] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<TodoCenterDetail | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);
  const [approveItem, setApproveItem] = useState<TodoCenterItem | null>(null);
  const [nextApproverId, setNextApproverId] = useState("");
  const [ccUserIds, setCcUserIds] = useState<string[]>([]);
  const [comment, setComment] = useState("");
  const [terminateHere, setTerminateHere] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [withdrawItem, setWithdrawItem] = useState<TodoCenterItem | null>(null);
  const [withdrawReason, setWithdrawReason] = useState("");
  const [withdrawing, setWithdrawing] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const fetchStats = useCallback(async (signal?: AbortSignal) => {
    try {
      const res = await getTodoCenterStats(signal);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setStats(res.data);
      }
    } catch {
      setStats(emptyStats());
    }
  }, []);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getTodoCenterList(
        activeTab,
        {
          bizType: bizType === "all" ? undefined : bizType,
          status: status === "all" ? undefined : Number(status),
          keyword: debouncedKeyword || undefined,
          current: currentPage,
          pageSize: PAGE_SIZE,
        },
        signal
      );
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setItems(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return;
      setItems([]);
      setTotal(0);
      setLoading(false);
    }
  }, [activeTab, bizType, currentPage, debouncedKeyword, status]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      fetchStats(controller.signal);
      fetchList(controller.signal);
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [fetchList, fetchStats]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const refreshAll = useCallback(() => {
    fetchStats();
    fetchList();
    window.dispatchEvent(new Event("message-center:updated"));
  }, [fetchList, fetchStats]);

  const openOriginalPage = (item: TodoCenterItem) => {
    router.push(item.link || "/");
  };

  const openDetail = async (item: TodoCenterItem) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      const res = await getTodoCenterDetail(item.todoId);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
      }
    } finally {
      setDetailLoading(false);
    }
  };

  const openApproval = async (item: TodoCenterItem) => {
    if (!item.bizType || !directApprovalBizTypes.has(item.bizType)) {
      openOriginalPage(item);
      return;
    }
    setApproveItem(item);
    setNextApproverId("");
    setCcUserIds([]);
    setComment("");
    setTerminateHere(false);
    setApproveOpen(true);
    if (users.length === 0) {
      const res = await getUserList();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setUsers(res.data.records ?? []);
      }
    }
  };

  const closeApproval = () => {
    if (submitting) return;
    setApproveOpen(false);
    setApproveItem(null);
    setCcUserIds([]);
  };

  const handleUrge = async (item: TodoCenterItem) => {
    try {
      const res = await urgeApproval({ recordId: item.todoId });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已发送催办提醒");
        window.dispatchEvent(new Event("message-center:updated"));
      }
    } catch {
      // request interceptor shows the business error.
    }
  };

  const openWithdraw = (item: TodoCenterItem) => {
    setWithdrawItem(item);
    setWithdrawReason("");
  };

  const handleWithdraw = async () => {
    if (!withdrawItem) return;
    setWithdrawing(true);
    try {
      const res = await withdrawApproval({
        recordId: withdrawItem.todoId,
        reason: withdrawReason.trim() || undefined,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已撤回审批");
        setWithdrawItem(null);
        setWithdrawReason("");
        refreshAll();
        if (detailOpen && detail?.item.todoId === withdrawItem.todoId) {
          setDetailOpen(false);
          setDetail(null);
        }
      }
    } finally {
      setWithdrawing(false);
    }
  };

  const approveDefaultTerminate = Boolean(
    (approveItem?.bizType === "file" || approveItem?.bizType === "inspection")
      && (approveItem.approvalLevel ?? 1) >= 3
  );

  const handleApprove = async (pass: boolean) => {
    if (!approveItem || !approveItem.bizType) return;
    if (!comment.trim()) {
      toast.error(pass ? "请输入审批意见" : "请输入驳回原因");
      return;
    }
    const shouldTerminate = pass ? terminateHere || approveDefaultTerminate : true;
    if (pass && !shouldTerminate && !nextApproverId) {
      toast.error("请选择下一级审批人，或勾选终审通过");
      return;
    }

    setSubmitting(true);
    try {
      const common = {
        recordId: approveItem.todoId,
        pass,
        nextApproverId: pass && !shouldTerminate ? nextApproverId : undefined,
        comment: comment.trim() || undefined,
      };
      const res = approveItem.bizType === "inspection"
        ? await approvalInspection({
          inspectionFormId: approveItem.bizId,
          result: pass ? 1 : 2,
          approvalDescription: comment.trim() || undefined,
          nextApproverId: pass && !shouldTerminate ? nextApproverId : undefined,
        })
        : approveItem.bizType === "file"
          ? await approveProjectFile(common)
          : approveItem.bizType === "bid"
            ? await approveBid(common)
            : approveItem.bizType === "acceptance"
              ? await approveAcceptance(common)
              : await approveOaApplication(common);

      if (res.code === ResponseCode.SUCCESS) {
        if (ccUserIds.length > 0) {
          try {
            const ccRes = await createApprovalCc({
              recordId: approveItem.todoId,
              ccUserIds,
              comment: comment.trim() || undefined,
            });
            if (ccRes.code !== ResponseCode.SUCCESS) {
              toast.error("审批已提交，但抄送写入失败");
            }
          } catch {
            toast.error("审批已提交，但抄送写入失败");
          }
        }
        toast.success(pass ? "审批通过" : "已驳回");
        setApproveOpen(false);
        setApproveItem(null);
        setCcUserIds([]);
        refreshAll();
        if (detailOpen && detail?.item.todoId === approveItem.todoId) {
          setDetailOpen(false);
          setDetail(null);
        }
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">统一待办</h1>
          <p className="text-sm text-slate-500 mt-1">集中处理审批待办、查看发起记录和处理记录</p>
        </div>
        <Button variant="outline" onClick={() => fetchList()}>
          <Clock3 className="w-4 h-4 mr-2" /> 刷新
        </Button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-500">待我处理</span>
            <Inbox className="w-5 h-5 text-amber-500" />
          </div>
          <div className="text-2xl font-semibold text-slate-900 mt-2">{stats.pendingCount}</div>
          <div className="text-xs text-slate-400 mt-1">今日新增 {stats.todayPendingCount}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-500">我发起的</span>
            <Send className="w-5 h-5 text-blue-500" />
          </div>
          <div className="text-2xl font-semibold text-slate-900 mt-2">{stats.initiatedCount}</div>
          <div className="text-xs text-slate-400 mt-1">按业务单据去重</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-500">我已处理</span>
            <CheckCircle2 className="w-5 h-5 text-emerald-500" />
          </div>
          <div className="text-2xl font-semibold text-slate-900 mt-2">{stats.processedCount}</div>
          <div className="text-xs text-slate-400 mt-1">审批节点记录</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-500">抄送我的</span>
            <Eye className="w-5 h-5 text-slate-500" />
          </div>
          <div className="text-2xl font-semibold text-slate-900 mt-2">{stats.ccCount}</div>
          <div className="text-xs text-slate-400 mt-1">审批流转抄送</div>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex bg-white rounded-lg p-1 border border-slate-200 overflow-x-auto">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.key}
                onClick={() => {
                  setActiveTab(tab.key);
                  setCurrentPage(1);
                }}
                className={cn(
                  "inline-flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors whitespace-nowrap",
                  activeTab === tab.key ? "bg-blue-600 text-white" : "text-slate-500 hover:bg-slate-50"
                )}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[1fr_180px_180px] gap-3">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索标题、申请人或审批人"
            className="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <select
          value={bizType}
          onChange={(event) => {
            setBizType(event.target.value);
            setCurrentPage(1);
          }}
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {bizTypeOptions.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setCurrentPage(1);
          }}
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {statusOptions.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </div>

      {loading && <TablePageSkeleton columns={6} rows={6} />}

      {!loading && items.length === 0 && (
        <div className="border border-slate-200 bg-white rounded-lg p-12 text-center">
          <ClipboardList className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-sm text-slate-500">
            {activeTab === "cc" ? "暂无抄送记录" : "暂无待办记录"}
          </p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="w-full min-w-[920px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-left text-slate-500">
                <th className="px-5 py-4 font-medium">事项</th>
                <th className="px-5 py-4 font-medium">类型</th>
                <th className="px-5 py-4 font-medium">申请人</th>
                <th className="px-5 py-4 font-medium">当前审批人</th>
                <th className="px-5 py-4 font-medium">状态</th>
                <th className="px-5 py-4 font-medium">更新时间</th>
                <th className="px-5 py-4 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={`${activeTab}-${item.todoId}`} className="border-b border-slate-50 hover:bg-slate-50">
                  <td className="px-5 py-4">
                    <div className="font-medium text-slate-900">{item.bizName || item.title}</div>
                    <div className="text-xs text-slate-400 mt-1">
                      第 {item.approvalLevel ?? 1} 级审批 · 创建于 {formatTime(item.createdTime)}
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <Badge variant="secondary">{item.bizTypeLabel}</Badge>
                  </td>
                  <td className="px-5 py-4 text-slate-600">{item.applyUserName || "-"}</td>
                  <td className="px-5 py-4 text-slate-600">{item.currentApproverName || "-"}</td>
                  <td className="px-5 py-4">
                    <span className={cn(
                      "inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium",
                      statusStyles[item.status] ?? "bg-slate-100 text-slate-600"
                    )}>
                      {item.statusLabel}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-slate-500 whitespace-nowrap">{formatTime(item.updatedTime)}</td>
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-2">
                      <Button size="sm" variant="outline" onClick={() => openDetail(item)}>
                        详情
                      </Button>
                      {activeTab === "pending" && item.status === 0 && directApprovalBizTypes.has(item.bizType ?? "") ? (
                        <Button size="sm" onClick={() => openApproval(item)}>
                          审批
                        </Button>
                      ) : (
                        <Button size="sm" onClick={() => openOriginalPage(item)}>
                          查看
                        </Button>
                      )}
                      {activeTab === "initiated" && item.status === 0 && (
                        <>
                          <Button size="sm" variant="outline" onClick={() => handleUrge(item)}>
                            <BellRing className="w-4 h-4 mr-1" />
                            催办
                          </Button>
                          {withdrawSupportedBizTypes.has(item.bizType ?? "") && (
                            <Button
                              size="sm"
                              variant="outline"
                              className="text-rose-600 hover:text-rose-700"
                              onClick={() => openWithdraw(item)}
                            >
                              <RotateCcw className="w-4 h-4 mr-1" />
                              撤回
                            </Button>
                          )}
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between text-sm text-slate-500">
        <span>共 {total} 条记录</span>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={currentPage <= 1} onClick={() => setCurrentPage((page) => page - 1)}>
            <ChevronLeft className="w-4 h-4" />
          </Button>
          <span>{currentPage} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={currentPage >= totalPages} onClick={() => setCurrentPage((page) => page + 1)}>
            <ChevronRight className="w-4 h-4" />
          </Button>
        </div>
      </div>

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>待办详情</DialogTitle>
            <DialogDescription>
              {detail?.item.bizTypeLabel ?? "审批事项"} · {detail?.item.statusLabel ?? "加载中"}
            </DialogDescription>
          </DialogHeader>
          {detailLoading && <TablePageSkeleton columns={2} rows={3} />}
          {!detailLoading && detail && (
            <div className="space-y-5">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <DetailField label="事项" value={detail.item.bizName || detail.item.title} />
                <DetailField label="类型" value={detail.item.bizTypeLabel} />
                <DetailField label="申请人" value={detail.item.applyUserName || "-"} />
                <DetailField label="当前审批人" value={detail.item.currentApproverName || "-"} />
                <DetailField label="审批层级" value={`第 ${detail.item.approvalLevel ?? 1} 级`} />
                <DetailField label="更新时间" value={formatTime(detail.item.updatedTime)} />
              </div>

              <section>
                <p className="text-xs text-slate-400 mb-2">审批链路</p>
                <ApprovalChain records={detail.approvalRecords ?? []} />
              </section>

              <div className="flex flex-wrap justify-end gap-2 pt-4 border-t border-slate-100">
                <Button variant="outline" onClick={() => openOriginalPage(detail.item)}>
                  进入原业务页面
                </Button>
                {detail.item.status === 0 && directApprovalBizTypes.has(detail.item.bizType ?? "") && (
                  <Button onClick={() => openApproval(detail.item)}>
                    审批
                  </Button>
                )}
                {activeTab === "initiated" && detail.item.status === 0 && (
                  <>
                    <Button variant="outline" onClick={() => handleUrge(detail.item)}>
                      <BellRing className="w-4 h-4 mr-1" />
                      催办
                    </Button>
                    {withdrawSupportedBizTypes.has(detail.item.bizType ?? "") && (
                      <Button
                        variant="outline"
                        className="text-rose-600 hover:text-rose-700"
                        onClick={() => openWithdraw(detail.item)}
                      >
                        <RotateCcw className="w-4 h-4 mr-1" />
                        撤回
                      </Button>
                    )}
                  </>
                )}
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={approveOpen} onOpenChange={(open) => !open && closeApproval()}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>审批待办</DialogTitle>
            <DialogDescription>
              {approveItem?.bizName ?? "审批事项"} · 第 {approveItem?.approvalLevel ?? 1} 级
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="p-3 bg-slate-50 rounded-lg text-sm">
              <div className="font-medium text-slate-800">{approveItem?.title}</div>
              <div className="text-xs text-slate-400 mt-1">
                申请人：{approveItem?.applyUserName || "-"} · 当前审批人：{approveItem?.currentApproverName || "-"}
              </div>
            </div>

            <div className="flex items-center gap-2">
              <input
                id="todo-terminate"
                type="checkbox"
                checked={terminateHere || approveDefaultTerminate}
                disabled={approveDefaultTerminate}
                onChange={(event) => setTerminateHere(event.target.checked)}
              />
              <label htmlFor="todo-terminate" className="text-sm text-slate-600">
                终审通过，不再流转下一级
                {approveDefaultTerminate && <span className="text-slate-400 ml-1">(L3 自动终审)</span>}
              </label>
            </div>

            {!(terminateHere || approveDefaultTerminate) && (
              <div>
                <label className="text-xs font-medium text-slate-600 mb-1 block">
                  下一级审批人 <span className="text-rose-500">*</span>
                </label>
                <select
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                  value={nextApproverId}
                  onChange={(event) => setNextApproverId(event.target.value)}
                >
                  <option value="">请选择下一级审批人</option>
                  {users.map((user) => (
                    <option key={user.userId} value={user.userId}>
                      {user.realName || user.username}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                审批意见 / 驳回原因 <span className="text-rose-500">*</span>
              </label>
              <textarea
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                rows={3}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="请输入审批意见或驳回原因"
              />
            </div>

            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                抄送人
              </label>
              <select
                multiple
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-24"
                value={ccUserIds}
                onChange={(event) => {
                  const selected = Array.from(event.target.selectedOptions).map((option) => option.value);
                  setCcUserIds(selected);
                }}
              >
                {users
                  .filter((user) => String(user.userId) !== approveItem?.applyUserId)
                  .map((user) => (
                    <option key={user.userId} value={user.userId}>
                      {user.realName || user.username}
                    </option>
                  ))}
              </select>
              <p className="text-xs text-slate-400 mt-1">可按住 Command / Ctrl 多选</p>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
            <Button variant="outline" className="text-rose-600" onClick={() => handleApprove(false)} disabled={submitting}>
              {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <X className="w-4 h-4 mr-1" />}
              驳回
            </Button>
            <Button className="bg-emerald-600 hover:bg-emerald-700" onClick={() => handleApprove(true)} disabled={submitting}>
              {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <Check className="w-4 h-4 mr-1" />}
              通过
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(withdrawItem)} onOpenChange={(open) => !open && !withdrawing && setWithdrawItem(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>撤回审批</DialogTitle>
            <DialogDescription>
              撤回后当前审批节点会结束，业务单据会回到可重新提交或已撤回状态。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="p-3 bg-rose-50 border border-rose-100 rounded-lg text-sm text-rose-700">
              此操作会影响当前审批流程，请确认是否撤回：{withdrawItem?.bizName || withdrawItem?.title}
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">撤回原因</label>
              <textarea
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                rows={3}
                value={withdrawReason}
                onChange={(event) => setWithdrawReason(event.target.value)}
                placeholder="可选，默认记录为申请人撤回"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
            <Button variant="outline" disabled={withdrawing} onClick={() => setWithdrawItem(null)}>
              取消
            </Button>
            <Button className="bg-rose-600 hover:bg-rose-700" disabled={withdrawing} onClick={handleWithdraw}>
              {withdrawing && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
              确认撤回
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function DetailField({ label, value }: { label: string; value: string }) {
  return (
    <div className="py-2 px-3 bg-slate-50 rounded-lg">
      <p className="text-xs text-slate-400 mb-0.5">{label}</p>
      <p className="text-sm font-medium text-slate-800">{value}</p>
    </div>
  );
}

function ApprovalChain({ records }: { records: ApprovalRecordVo[] }) {
  if (records.length === 0) {
    return <div className="p-4 bg-slate-50 rounded-lg text-sm text-slate-400">暂无审批记录</div>;
  }
  return (
    <div className="space-y-2">
      {records.map((record, index) => (
        <div key={record.approvalRecordId} className="flex gap-3 p-3 bg-slate-50 rounded-lg">
          <span className={cn(
            "inline-flex w-6 h-6 items-center justify-center rounded-full text-xs font-semibold shrink-0",
            record.inspectionFormStatus === 1
              ? "bg-emerald-100 text-emerald-700"
              : record.inspectionFormStatus === 2
                ? "bg-rose-100 text-rose-700"
                : "bg-amber-100 text-amber-700"
          )}>
            {index + 1}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium text-slate-800">
                L{record.approvalLevel ?? index + 1} · {record.approverName || "审批人"}
              </span>
              <Badge variant="secondary" className={cn("text-xs", statusStyles[record.inspectionFormStatus] ?? "")}>
                {record.inspectionFormStatus === 0 ? "待审核" : record.inspectionFormStatus === 1 ? "已通过" : "已驳回"}
              </Badge>
            </div>
            {record.approvalDescription && (
              <p className="text-sm text-slate-600 mt-1">{record.approvalDescription}</p>
            )}
            <p className="text-xs text-slate-400 mt-1">{formatTime(record.updatedTime || record.createdTime)}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
