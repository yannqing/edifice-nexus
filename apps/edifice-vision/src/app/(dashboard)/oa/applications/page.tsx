"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Plus,
  RotateCcw,
  Search,
  Send,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import {
  OA_PRIORITY_MAP,
  OA_STATUS_MAP,
  type OaApplication,
  type OaApplicationType,
} from "@/types/oa";
import {
  createOaApplication,
  getOaApplications,
  getOaApplicationTypes,
  getOaSsoToken,
  submitOaApplication,
  withdrawOaApplication,
} from "@/services/oa";

const PAGE_SIZE = 10;

type StatusTab = "all" | "draft" | "approving" | "done";

const statusFilterMap: Record<StatusTab, number | undefined> = {
  all: undefined,
  draft: 0,
  approving: 1,
  done: undefined,
};

const statusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-blue-100 text-blue-700",
  2: "bg-emerald-100 text-emerald-700",
  3: "bg-rose-100 text-rose-700",
  4: "bg-slate-200 text-slate-600",
};

const priorityStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-amber-100 text-amber-700",
  2: "bg-rose-100 text-rose-700",
};

function formatDate(value?: string | null) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

function summarizeFormData(data: Record<string, unknown>) {
  const entries = Object.entries(data ?? {}).filter(([, value]) => value !== undefined && value !== "");
  if (entries.length === 0) return "-";
  return entries
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${String(value)}`)
    .join(" / ");
}

export default function OaApplicationsPage() {
  const [types, setTypes] = useState<OaApplicationType[]>([]);
  const [items, setItems] = useState<OaApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<StatusTab>("all");
  const [selectedType, setSelectedType] = useState("");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [formOpen, setFormOpen] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [applicationType, setApplicationType] = useState("general");
  const [title, setTitle] = useState("");
  const [priority, setPriority] = useState(0);
  const [formDataText, setFormDataText] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    getOaApplicationTypes().then((res) => {
      if (res.code === ResponseCode.SUCCESS && res.data?.length) {
        setTypes(res.data);
        setApplicationType(res.data[0].type);
      }
    });
  }, []);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, selectedType, debouncedSearch]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getOaApplications(
        {
          keywords: debouncedSearch || undefined,
          applicationType: selectedType || undefined,
          status: statusFilterMap[activeTab],
          mine: true,
          current: currentPage,
          pageSize: PAGE_SIZE,
        },
        signal
      );
      if (res.code === ResponseCode.SUCCESS && res.data) {
        const records = res.data.records ?? [];
        setItems(activeTab === "done" ? records.filter((item) => [2, 3, 4].includes(item.status)) : records);
        setTotal(res.data.total ?? 0);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return;
      setItems([]);
      setTotal(0);
      setLoading(false);
    }
  }, [activeTab, selectedType, debouncedSearch, currentPage]);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const tabs = useMemo(() => [
    { key: "all" as const, label: "全部" },
    { key: "draft" as const, label: "草稿" },
    { key: "approving" as const, label: "审批中" },
    { key: "done" as const, label: "已结束" },
  ], []);

  const handleOpenOa = async () => {
    setActionLoading("open-oa");
    try {
      const res = await getOaSsoToken();
      if (res.code === ResponseCode.SUCCESS && res.data?.token) {
        const url = `${res.data.oaUrl.replace(/\/$/, "")}/login/sso?ssoToken=${encodeURIComponent(res.data.token)}`;
        window.open(url, "_blank", "noopener,noreferrer");
      }
    } finally {
      setActionLoading(null);
    }
  };

  const resetForm = () => {
    setTitle("");
    setPriority(0);
    setFormDataText("");
    setApplicationType(types[0]?.type ?? "general");
  };

  const handleCreate = async (submitNow: boolean) => {
    if (!title.trim()) {
      toast.error("申请标题不能为空");
      return;
    }
    setActionLoading(submitNow ? "create-submit" : "create-draft");
    try {
      const parsedData = parseFormDataText(formDataText);
      const res = await createOaApplication({
        applicationType,
        title: title.trim(),
        priority,
        status: submitNow ? 1 : 0,
        formData: parsedData,
        attachmentIds: [],
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(submitNow ? "已提交申请" : "已保存草稿");
        setFormOpen(false);
        resetForm();
        fetchList();
      }
    } catch (err) {
      if (err instanceof SyntaxError) toast.error("表单数据格式不是有效 JSON");
    } finally {
      setActionLoading(null);
    }
  };

  const handleSubmit = async (id: string) => {
    setActionLoading(id);
    try {
      const res = await submitOaApplication(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已提交");
        fetchList();
      }
    } finally {
      setActionLoading(null);
    }
  };

  const handleWithdraw = async (id: string) => {
    setActionLoading(id);
    try {
      const res = await withdrawOaApplication(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已撤回");
        fetchList();
      }
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">OA 申请</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={handleOpenOa} disabled={actionLoading === "open-oa"}>
            {actionLoading === "open-oa" ? <Loader2 className="w-4 h-4 animate-spin" /> : <ArrowUpRight className="w-4 h-4" />}
            OA 工作台
          </Button>
          <Button
            onClick={() => setFormOpen(true)}
            className="bg-blue-600 hover:bg-blue-700"
          >
            <Plus className="w-4 h-4" />
            新建申请
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
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
        <select
          value={selectedType}
          onChange={(e) => setSelectedType(e.target.value)}
          className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">全部类型</option>
          {types.map((type) => (
            <option key={type.type} value={type.type}>{type.label}</option>
          ))}
        </select>
        <div className="flex-1" />
        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder="搜索标题或编号"
            className="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      {loading && <TablePageSkeleton columns={5} rows={5} />}

      {!loading && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white p-10 text-center text-sm text-slate-500">
          暂无申请
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
          <table className="w-full">
            <thead className="bg-slate-50/50">
              <tr className="text-slate-500 text-xs uppercase tracking-wider">
                <th className="text-left py-4 px-6 font-semibold">申请</th>
                <th className="text-left py-4 px-4 font-semibold">类型</th>
                <th className="text-left py-4 px-4 font-semibold">状态</th>
                <th className="text-left py-4 px-4 font-semibold">优先级</th>
                <th className="text-left py-4 px-4 font-semibold">时间</th>
                <th className="text-right py-4 px-6 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => {
                const isBusy = actionLoading === item.applicationId;
                return (
                  <tr key={item.applicationId} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-4 px-6 max-w-[420px]">
                      <p className="text-sm font-semibold text-slate-800 truncate">{item.title}</p>
                      <p className="text-xs text-slate-400 mt-0.5">{item.applicationNo}</p>
                      <p className="text-xs text-slate-500 mt-1 truncate">{summarizeFormData(item.formData)}</p>
                    </td>
                    <td className="py-4 px-4 text-sm text-slate-600">{item.applicationTypeLabel}</td>
                    <td className="py-4 px-4">
                      <Badge variant="secondary" className={cn("text-xs", statusStyles[item.status] ?? statusStyles[0])}>
                        {OA_STATUS_MAP[item.status] ?? "未知"}
                      </Badge>
                    </td>
                    <td className="py-4 px-4">
                      <Badge variant="secondary" className={cn("text-xs", priorityStyles[item.priority] ?? priorityStyles[0])}>
                        {OA_PRIORITY_MAP[item.priority] ?? "普通"}
                      </Badge>
                    </td>
                    <td className="py-4 px-4 text-sm text-slate-500">{formatDate(item.submittedTime ?? item.createdTime)}</td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex justify-end gap-1">
                        {item.status === 0 && (
                          <Button variant="ghost" size="sm" disabled={isBusy} onClick={() => handleSubmit(item.applicationId)}>
                            {isBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                            提交
                          </Button>
                        )}
                        {item.status === 1 && (
                          <Button variant="ghost" size="sm" disabled={isBusy} onClick={() => handleWithdraw(item.applicationId)}>
                            {isBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <RotateCcw className="w-4 h-4" />}
                            撤回
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {!loading && total > PAGE_SIZE && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-slate-500">共 {total} 条</p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
            >
              <ChevronLeft className="w-4 h-4" />
              上一页
            </Button>
            <span className="text-sm text-slate-500">{currentPage} / {totalPages}</span>
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage >= totalPages}
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
            >
              下一页
              <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      )}

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建 OA 申请</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="space-y-1.5">
                <span className="text-sm font-medium text-slate-700">申请类型</span>
                <select
                  value={applicationType}
                  onChange={(e) => setApplicationType(e.target.value)}
                  className="w-full h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {types.map((type) => (
                    <option key={type.type} value={type.type}>{type.label}</option>
                  ))}
                </select>
              </label>
              <label className="space-y-1.5">
                <span className="text-sm font-medium text-slate-700">优先级</span>
                <select
                  value={priority}
                  onChange={(e) => setPriority(Number(e.target.value))}
                  className="w-full h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value={0}>普通</option>
                  <option value={1}>重要</option>
                  <option value={2}>紧急</option>
                </select>
              </label>
            </div>
            <label className="space-y-1.5 block">
              <span className="text-sm font-medium text-slate-700">标题</span>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="w-full h-10 rounded-lg border border-slate-200 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>
            <label className="space-y-1.5 block">
              <span className="text-sm font-medium text-slate-700">表单数据 JSON</span>
              <textarea
                value={formDataText}
                onChange={(e) => setFormDataText(e.target.value)}
                placeholder='{"事由":"客户现场沟通","开始时间":"2026-05-06 09:00"}'
                rows={7}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" onClick={() => setFormOpen(false)}>取消</Button>
              <Button
                variant="secondary"
                disabled={actionLoading === "create-draft"}
                onClick={() => handleCreate(false)}
              >
                {actionLoading === "create-draft" && <Loader2 className="w-4 h-4 animate-spin" />}
                保存草稿
              </Button>
              <Button
                className="bg-blue-600 hover:bg-blue-700"
                disabled={actionLoading === "create-submit"}
                onClick={() => handleCreate(true)}
              >
                {actionLoading === "create-submit" && <Loader2 className="w-4 h-4 animate-spin" />}
                提交申请
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function parseFormDataText(value: string): Record<string, unknown> {
  if (!value.trim()) return {};
  const parsed = JSON.parse(value);
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new SyntaxError("form data must be object");
  }
  return parsed as Record<string, unknown>;
}
