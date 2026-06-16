"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock3,
  Eye,
  Inbox,
  Search,
  Send,
  UserRoundCheck,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import { getTodoCenterList, getTodoCenterStats } from "@/services/todo-center";
import { ResponseCode } from "@/types/api";
import type { TodoCenterItem, TodoCenterStats, TodoCenterTab } from "@/types/todo-center";

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
    if (activeTab === "cc") {
      setItems([]);
      setTotal(0);
      setLoading(false);
      return;
    }
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

  const openItem = (item: TodoCenterItem) => {
    router.push(item.link || "/");
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
          <div className="text-xs text-slate-400 mt-1">预留抄送入口</div>
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
                    <Button size="sm" onClick={() => openItem(item)}>
                      {activeTab === "pending" ? "处理" : "查看"}
                    </Button>
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
    </div>
  );
}
