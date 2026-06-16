"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  CircleCheckBig,
  FolderGit2,
  Megaphone,
  XCircle,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import type { MessageCenterItem } from "@/types/message-center";
import {
  getMessageCenterList,
  markAllMessagesRead,
  markMessageRead,
} from "@/services/message-center";

const PAGE_SIZE = 12;

const tabs = [
  { key: "all", label: "全部消息" },
  { key: "approval", label: "待我审批" },
  { key: "result", label: "审批结果" },
  { key: "project", label: "项目动态" },
  { key: "announcement", label: "系统公告" },
];

function formatTime(value: string) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

export default function MessageCenterPage() {
  const router = useRouter();
  const [items, setItems] = useState<MessageCenterItem[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [activeTab, setActiveTab] = useState("all");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [markingAll, setMarkingAll] = useState(false);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getMessageCenterList({
        category: activeTab === "all" ? undefined : activeTab,
        unreadOnly,
        current: currentPage,
        pageSize: PAGE_SIZE,
      }, signal);
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
  }, [activeTab, currentPage, unreadOnly]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => fetchList(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [fetchList]);

  const openMessage = async (item: MessageCenterItem) => {
    try {
      if (!item.read) {
        await markMessageRead(item.sourceType, item.sourceId);
        window.dispatchEvent(new Event("message-center:updated"));
      }
    } finally {
      router.push(item.link || "/");
    }
  };

  const handleMarkAll = async () => {
    setMarkingAll(true);
    try {
      const res = await markAllMessagesRead();
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已全部标记为已读");
        window.dispatchEvent(new Event("message-center:updated"));
        fetchList();
      }
    } finally {
      setMarkingAll(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">消息中心</h1>
          <p className="text-sm text-slate-500 mt-1">集中查看待审批事项、审批结果与系统公告</p>
        </div>
        <Button variant="outline" onClick={handleMarkAll} disabled={markingAll}>
          <CheckCheck className="w-4 h-4 mr-2" /> 全部标记已读
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex bg-white rounded-lg p-1 border border-slate-200">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => {
                setActiveTab(tab.key);
                setCurrentPage(1);
              }}
              className={cn(
                "px-4 py-2 rounded-md text-sm font-medium transition-colors",
                activeTab === tab.key ? "bg-blue-600 text-white" : "text-slate-500 hover:bg-slate-50"
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => {
              setUnreadOnly(e.target.checked);
              setCurrentPage(1);
            }}
            className="w-4 h-4 accent-blue-600"
          />
          仅看未读
        </label>
      </div>

      {loading && <TablePageSkeleton columns={4} rows={6} />}

      {!loading && items.length === 0 && (
        <div className="border border-slate-200 bg-white rounded-lg p-12 text-center">
          <Bell className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-sm text-slate-500">暂无消息</p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="border border-slate-200 bg-white rounded-lg divide-y divide-slate-100">
          {items.map((item) => {
            const rejected = item.category === "result" && item.priority >= 2;
            const Icon = item.category === "approval"
              ? ClipboardCheck
              : rejected ? XCircle : item.category === "result" ? CircleCheckBig : item.category === "project" ? FolderGit2 : Megaphone;
            return (
              <button
                key={item.messageKey}
                onClick={() => openMessage(item)}
                className={cn(
                  "w-full text-left px-5 py-4 flex items-start gap-4 hover:bg-slate-50 transition-colors",
                  !item.read && "bg-blue-50/50"
                )}
              >
                <div className={cn(
                  "w-10 h-10 rounded-lg flex items-center justify-center shrink-0",
                  item.category === "approval"
                    ? "bg-amber-100 text-amber-700"
                    : rejected ? "bg-rose-100 text-rose-700"
                      : item.category === "result" ? "bg-emerald-100 text-emerald-700"
                        : item.category === "project" ? "bg-violet-100 text-violet-700" : "bg-blue-100 text-blue-700"
                )}>
                  <Icon className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    {!item.read && <span className="w-2 h-2 rounded-full bg-blue-600" />}
                    <span className="font-medium text-slate-900">{item.title}</span>
                    <Badge variant="secondary">{item.categoryLabel}</Badge>
                    {item.priority >= 2 && <Badge className="bg-rose-100 text-rose-700">紧急</Badge>}
                  </div>
                  <p className="text-sm text-slate-500 mt-1 line-clamp-2">{item.content || "点击查看详情"}</p>
                  <p className="text-xs text-slate-400 mt-2">{formatTime(item.createdTime)}</p>
                </div>
              </button>
            );
          })}
        </div>
      )}

      <div className="flex items-center justify-between text-sm text-slate-500">
        <span>共 {total} 条消息</span>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={currentPage <= 1} onClick={() => setCurrentPage((p) => p - 1)}>
            <ChevronLeft className="w-4 h-4" />
          </Button>
          <span>{currentPage} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={currentPage >= totalPages} onClick={() => setCurrentPage((p) => p + 1)}>
            <ChevronRight className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
