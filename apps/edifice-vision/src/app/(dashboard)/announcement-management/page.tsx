"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Plus,
  Search,
  Megaphone,
  Send,
  Archive,
  Pencil,
  Trash2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  ANNOUNCEMENT_PRIORITY_MAP,
  ANNOUNCEMENT_STATUS_MAP,
  type AnnouncementVo,
} from "@/types/announcement";
import {
  createAnnouncement,
  deleteAnnouncement,
  getAnnouncementList,
  publishAnnouncement,
  unpublishAnnouncement,
  updateAnnouncement,
} from "@/services/announcement";

type StatusTab = "all" | "draft" | "published" | "offline";

const statusFilterMap: Record<StatusTab, number | undefined> = {
  all: undefined,
  draft: 0,
  published: 1,
  offline: 2,
};

const priorityStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-amber-100 text-amber-700",
  2: "bg-rose-100 text-rose-700",
};

const statusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-500",
  1: "bg-emerald-100 text-emerald-600",
  2: "bg-slate-200 text-slate-600",
};

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.replace("T", " ").slice(0, 16);
}

const PAGE_SIZE = 10;

export default function AnnouncementManagementPage() {
  const [activeTab, setActiveTab] = useState<StatusTab>("all");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [items, setItems] = useState<AnnouncementVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AnnouncementVo | null>(null);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, debouncedSearch]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getAnnouncementList(
        {
          keywords: debouncedSearch || undefined,
          status: statusFilterMap[activeTab],
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
  }, [activeTab, debouncedSearch, currentPage]);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const handlePublish = async (id: string) => {
    setActionLoading(id);
    try {
      const res = await publishAnnouncement(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已发布");
        fetchList();
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setActionLoading(null); }
  };

  const handleUnpublish = async (id: string) => {
    setActionLoading(id);
    try {
      const res = await unpublishAnnouncement(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已下线");
        fetchList();
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setActionLoading(null); }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("确定删除这条公告吗？此操作不可撤销。")) return;
    setActionLoading(id);
    try {
      const res = await deleteAnnouncement(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已删除");
        fetchList();
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setActionLoading(null); }
  };

  const tabs: { key: StatusTab; label: string }[] = [
    { key: "all", label: "全部" },
    { key: "draft", label: "草稿" },
    { key: "published", label: "已发布" },
    { key: "offline", label: "已下线" },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            公告管理
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            管理所有系统公告，支持发布 / 下线 / 编辑 / 删除
          </p>
        </div>
        <Button
          onClick={() => { setEditing(null); setFormOpen(true); }}
          className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
        >
          <Plus className="w-4 h-4" /> 新建公告
        </Button>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {tabs.map((item) => (
            <button
              key={item.key}
              onClick={() => setActiveTab(item.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                activeTab === item.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="搜索公告标题..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {loading && <TablePageSkeleton columns={4} rows={5} />}

      {!loading && items.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-slate-50/50">
              <tr className="text-slate-500 text-xs uppercase tracking-wider">
                <th className="text-left py-4 px-6 font-semibold">标题</th>
                <th className="text-left py-4 px-4 font-semibold">优先级</th>
                <th className="text-left py-4 px-4 font-semibold">状态</th>
                <th className="text-left py-4 px-4 font-semibold">发布人</th>
                <th className="text-left py-4 px-4 font-semibold">发布时间</th>
                <th className="text-left py-4 px-4 font-semibold">过期时间</th>
                <th className="text-right py-4 px-6 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => {
                const isBusy = actionLoading === item.announcementId;
                return (
                  <tr key={item.announcementId} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-4 px-6 max-w-[360px]">
                      <p className="text-sm font-semibold text-slate-800 truncate">{item.title}</p>
                      <p className="text-xs text-slate-400 mt-0.5 truncate">{item.content}</p>
                    </td>
                    <td className="py-4 px-4">
                      <Badge variant="secondary" className={cn("text-xs", priorityStyles[item.priority] ?? priorityStyles[0])}>
                        {ANNOUNCEMENT_PRIORITY_MAP[item.priority] ?? "普通"}
                      </Badge>
                    </td>
                    <td className="py-4 px-4">
                      <Badge variant="secondary" className={cn("text-xs", statusStyles[item.status] ?? statusStyles[0])}>
                        {ANNOUNCEMENT_STATUS_MAP[item.status] ?? "未知"}
                      </Badge>
                    </td>
                    <td className="py-4 px-4 text-sm text-slate-600">{item.publishUserName ?? "-"}</td>
                    <td className="py-4 px-4 text-sm text-slate-500">{formatDate(item.publishTime)}</td>
                    <td className="py-4 px-4 text-sm text-slate-500">{formatDate(item.expireTime)}</td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {item.status === 1 ? (
                          <button
                            onClick={() => handleUnpublish(item.announcementId)}
                            disabled={isBusy}
                            title="下线"
                            className="p-1.5 text-slate-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-colors disabled:opacity-50"
                          >
                            <Archive className="w-4 h-4" />
                          </button>
                        ) : (
                          <button
                            onClick={() => handlePublish(item.announcementId)}
                            disabled={isBusy}
                            title={item.status === 0 ? "发布" : "重新发布"}
                            className="p-1.5 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg transition-colors disabled:opacity-50"
                          >
                            {isBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                          </button>
                        )}
                        <button
                          onClick={() => { setEditing(item); setFormOpen(true); }}
                          disabled={isBusy}
                          title="编辑"
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors disabled:opacity-50"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDelete(item.announcementId)}
                          disabled={isBusy}
                          title="删除"
                          className="p-1.5 text-slate-400 hover:text-rose-500 hover:bg-rose-50 rounded-lg transition-colors disabled:opacity-50"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {!loading && items.length === 0 && (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Megaphone className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无公告</h3>
          <p className="text-sm text-slate-500">当前筛选条件下没有找到公告</p>
        </div>
      )}

      {!loading && total > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{total}</span> 条
          </p>
          <div className="flex items-center gap-2">
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => p - 1)}
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <button
                key={page}
                onClick={() => setCurrentPage(page)}
                className={cn(
                  "px-3 py-1.5 text-sm font-medium rounded-lg",
                  page === currentPage ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-100"
                )}
              >
                {page}
              </button>
            ))}
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled={currentPage >= totalPages}
              onClick={() => setCurrentPage((p) => p + 1)}
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* 新建 / 编辑弹窗 */}
      <AnnouncementFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        editing={editing}
        onSuccess={fetchList}
      />
    </div>
  );
}

// ==================== 新建 / 编辑弹窗 ====================

function AnnouncementFormDialog({
  open,
  onOpenChange,
  editing,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  editing: AnnouncementVo | null;
  onSuccess: () => void;
}) {
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [priority, setPriority] = useState(0);
  const [expireTime, setExpireTime] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      setTitle(editing.title ?? "");
      setContent(editing.content ?? "");
      setPriority(editing.priority ?? 0);
      setExpireTime(editing.expireTime ? editing.expireTime.slice(0, 16) : "");
    } else {
      setTitle("");
      setContent("");
      setPriority(0);
      setExpireTime("");
    }
  }, [open, editing]);

  const handleSubmit = async (action: "draft" | "publish" | "saveEdit") => {
    if (!title.trim()) { toast.error("请输入标题"); return; }
    if (!content.trim()) { toast.error("请输入内容"); return; }

    const expireTimePayload = expireTime ? `${expireTime}:00` : undefined;

    setSubmitting(true);
    try {
      if (editing) {
        // 仅更新字段，不改状态
        const res = await updateAnnouncement({
          announcementId: editing.announcementId,
          title: title.trim(),
          content,
          priority,
          expireTime: expireTimePayload,
        });
        if (res.code === ResponseCode.SUCCESS) {
          toast.success("更新成功");
          onOpenChange(false);
          onSuccess();
        }
      } else {
        const res = await createAnnouncement({
          title: title.trim(),
          content,
          priority,
          status: action === "publish" ? 1 : 0,
          expireTime: expireTimePayload,
        });
        if (res.code === ResponseCode.SUCCESS) {
          toast.success(action === "publish" ? "已发布" : "已保存为草稿");
          onOpenChange(false);
          onSuccess();
        }
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setSubmitting(false); }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-xl overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-xl font-bold text-slate-800">
            {editing ? "编辑公告" : "新建公告"}
          </h2>
          <button
            onClick={() => onOpenChange(false)}
            className="text-slate-400 hover:text-slate-600"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 space-y-4 max-h-[60vh] overflow-y-auto">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              标题 <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="请输入公告标题"
              maxLength={200}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">优先级</label>
            <div className="grid grid-cols-3 gap-3">
              {[
                { value: 0, label: "普通", color: "bg-slate-500" },
                { value: 1, label: "重要", color: "bg-amber-500" },
                { value: 2, label: "紧急", color: "bg-rose-500" },
              ].map((p) => (
                <label key={p.value} className="relative cursor-pointer">
                  <input
                    type="radio"
                    name="priority"
                    value={p.value}
                    checked={priority === p.value}
                    onChange={() => setPriority(p.value)}
                    className="peer sr-only"
                  />
                  <div className="p-3 border border-slate-200 rounded-xl text-center peer-checked:border-blue-500 peer-checked:bg-blue-50 transition-all">
                    <div className={cn("w-3 h-3 rounded-full mx-auto mb-2", p.color)} />
                    <p className="text-sm font-medium text-slate-700">{p.label}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              过期时间 <span className="text-slate-400 text-xs">（可选，到期自动不再展示）</span>
            </label>
            <input
              type="datetime-local"
              value={expireTime}
              onChange={(e) => setExpireTime(e.target.value)}
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              内容 <span className="text-rose-500">*</span>
            </label>
            <textarea
              rows={8}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="请输入公告内容..."
              className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
        </div>

        <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={submitting}
          >
            取消
          </Button>
          {editing ? (
            <Button
              onClick={() => handleSubmit("saveEdit")}
              disabled={submitting}
              className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-2"
            >
              {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
              保存
            </Button>
          ) : (
            <>
              <Button
                variant="outline"
                onClick={() => handleSubmit("draft")}
                disabled={submitting}
              >
                保存草稿
              </Button>
              <Button
                onClick={() => handleSubmit("publish")}
                disabled={submitting}
                className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-2"
              >
                {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                立即发布
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
