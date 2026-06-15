"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  Layers,
  TrendingUp,
  ClipboardCheck,
  Banknote,
  ArrowRight,
  Megaphone,
  Plus,
  Loader2,
  X,
  Trash2,
  ExternalLink,
  Workflow,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { CardPageSkeleton } from "@/components/ui/skeleton";
import { getDashboard } from "@/services/report";
import { getOaSsoToken } from "@/services/oa";
import type { DashboardData } from "@/services/report";
import {
  getRecentAnnouncements,
  createAnnouncement,
  deleteAnnouncement,
} from "@/services/announcement";
import {
  ANNOUNCEMENT_PRIORITY_MAP,
  type AnnouncementVo,
} from "@/types/announcement";
import { ResponseCode } from "@/types/api";
import { PROJECT_STATUS_MAP } from "@/types/project";
import { useAuth } from "@/store/auth-context";

const statusStyles: Record<string, string> = {
  未开始: "bg-slate-100 text-slate-500",
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
};

const categoryColors: Record<string, string> = {
  "A类": "bg-blue-500", "B类": "bg-emerald-500", "C类": "bg-amber-500",
  "D类": "bg-purple-500", "E类": "bg-rose-500",
};

function formatAmount(v: number) {
  return v >= 10000 ? `¥${(v / 10000).toFixed(1)}万` : `¥${v.toLocaleString()}`;
}

function formatTime(t: string | null) {
  if (!t) return "";
  const diff = Date.now() - new Date(t).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  return `${days}天前`;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<DashboardData | null>(null);

  const [announcements, setAnnouncements] = useState<AnnouncementVo[]>([]);
  const [announcementLoading, setAnnouncementLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getDashboard();
      if (res.code === ResponseCode.SUCCESS) {
        setData(res.data);
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setLoading(false); }
  }, []);

  const fetchAnnouncements = useCallback(async () => {
    setAnnouncementLoading(true);
    try {
      const res = await getRecentAnnouncements(5);
      if (res.code === ResponseCode.SUCCESS) {
        setAnnouncements(res.data ?? []);
      }
      setAnnouncementLoading(false);
    } catch {
      setAnnouncementLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);
  useEffect(() => { fetchAnnouncements(); }, [fetchAnnouncements]);

  const handleDeleteAnnouncement = async (id: string) => {
    try {
      const res = await deleteAnnouncement(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("公告已删除");
        fetchAnnouncements();
      }
    } catch { /* 由 request.ts 提示 */ }
  };

  const handleOpenOa = async () => {
    try {
      const res = await getOaSsoToken();
      if (res.code === ResponseCode.SUCCESS && res.data?.token && res.data?.oaUrl) {
        const oaBaseUrl = res.data.oaUrl.replace(/\/$/, "");
        window.location.href = oaBaseUrl + "/home/sso/login?ssoToken=" + encodeURIComponent(res.data.token);
      }
    } catch { /* 由 request.ts 提示 */ }
  };

  const totalCategoryCount = data?.categoryDistribution?.reduce((sum, c) => sum + c.count, 0) ?? 0;

  return (
    <div className="p-8 space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
          全局数据仪表盘
        </h1>
        <p className="text-slate-500 text-sm mt-1">
          欢迎回来{user?.realName ? `，${user.realName}` : ""}，这是截至目前的产值分配与核算概览信息。
        </p>
      </div>

      {/* 公告 */}
      <AnnouncementCard
        loading={announcementLoading}
        items={announcements}
        onCreate={() => setCreateOpen(true)}
        onDelete={handleDeleteAnnouncement}
      />

      <div className="glass-card rounded-2xl p-6 shadow-sm flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-emerald-50 text-emerald-600 rounded-xl">
            <Workflow className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-800">OA 办公系统</h3>
            <p className="text-sm text-slate-500 mt-0.5">进入审批、人事、行政、财务与项目协同工作台</p>
          </div>
        </div>
        <Button
          onClick={handleOpenOa}
          className="bg-emerald-600 hover:bg-emerald-700 text-white flex items-center gap-2 md:w-auto w-full justify-center"
        >
          <ExternalLink className="w-4 h-4" />
          进入 OA
        </Button>
      </div>

      {loading && <CardPageSkeleton cards={4} />}

      {!loading && data && (
        <>
          {/* Stats Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {[
              { label: "在研项目总数", value: String(data.stats.projectCount), icon: Layers, color: "blue" },
              { label: "产值总额", value: formatAmount(data.stats.totalOutputValue), icon: TrendingUp, color: "emerald" },
              { label: "已发放产值", value: formatAmount(data.stats.paidOutputValue), icon: Banknote, color: "amber" },
              { label: "待审批验工单", value: String(data.stats.pendingInspections), icon: ClipboardCheck, color: "rose" },
            ].map((stat) => {
              const colorMap: Record<string, { bg: string; text: string }> = {
                blue: { bg: "bg-blue-100", text: "text-blue-600" },
                emerald: { bg: "bg-emerald-100", text: "text-emerald-600" },
                amber: { bg: "bg-amber-100", text: "text-amber-600" },
                rose: { bg: "bg-rose-100", text: "text-rose-600" },
              };
              const c = colorMap[stat.color];
              return (
                <div key={stat.label} className="glass-card p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow">
                  <div className={cn("p-3 rounded-xl w-fit", c.bg, c.text)}>
                    <stat.icon className="w-5 h-5" />
                  </div>
                  <div className="mt-4">
                    <p className="text-sm font-medium text-slate-500">{stat.label}</p>
                    <h3 className="text-2xl font-bold text-slate-900 mt-1">{stat.value}</h3>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Row 2: Top Projects + Todos */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Top Projects */}
            <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-lg font-bold text-slate-800">关键项目概览</h3>
                <button onClick={() => router.push("/all-projects")} className="text-sm text-blue-600 font-medium hover:underline flex items-center gap-1">
                  查看全部 <ArrowRight className="w-3 h-3" />
                </button>
              </div>
              <div className="space-y-4">
                {data.topProjects.map((p) => (
                  <div key={p.projectId} className="flex flex-wrap items-center gap-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-slate-800 truncate">{p.projectName}</span>
                        {p.type && <span className="text-xs text-slate-400">{p.type}</span>}
                      </div>
                      <div className="w-full bg-slate-100 rounded-full h-2">
                        <div className="bg-blue-500 h-2 rounded-full transition-all" style={{ width: `${Math.min(p.progress, 100)}%` }} />
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-semibold text-slate-800">{formatAmount(p.contractAmount)}</p>
                      <p className="text-xs text-slate-400">{p.progress}% 完成</p>
                    </div>
                  </div>
                ))}
                {data.topProjects.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无项目数据</p>
                )}
              </div>
            </div>

            {/* Todos */}
            <div className="glass-card rounded-2xl p-6 shadow-sm">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-lg font-bold text-slate-800">待办事项</h3>
                <span className="text-xs px-2 py-0.5 bg-rose-100 text-rose-600 rounded-full font-medium">
                  {data.todos.length}
                </span>
              </div>
              <div className="space-y-3">
                {data.todos.map((todo) => (
                  <div key={todo.id} className="flex items-start gap-3 p-3 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer">
                    <div className={cn("w-2 h-2 rounded-full mt-1.5 shrink-0",
                      todo.type === "验工审批" ? "bg-amber-500" : "bg-blue-500")} />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-slate-800 truncate">{todo.title}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-xs text-slate-400">{todo.from}</span>
                        <span className="text-xs text-slate-300">·</span>
                        <span className="text-xs text-slate-400">{formatTime(todo.time)}</span>
                      </div>
                    </div>
                    <Badge variant="secondary" className={cn("text-xs shrink-0",
                      todo.type === "验工审批" ? "bg-amber-100 text-amber-600" : "bg-blue-100 text-blue-600")}>
                      {todo.type}
                    </Badge>
                  </div>
                ))}
                {data.todos.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无待办</p>
                )}
              </div>
            </div>
          </div>

          {/* Row 3: My Projects + Category */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* My Projects */}
            <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-lg font-bold text-slate-800">我的项目进度</h3>
                <button onClick={() => router.push("/my-projects")} className="text-sm text-blue-600 font-medium hover:underline flex items-center gap-1">
                  查看全部 <ArrowRight className="w-3 h-3" />
                </button>
              </div>
              <div className="space-y-4">
                {data.myProjects.map((p) => {
                  const statusLabel = PROJECT_STATUS_MAP[p.projectStatus] ?? "未知";
                  return (
                    <div key={p.projectId} className="flex items-center gap-4 p-3 rounded-xl hover:bg-slate-50 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <span className="text-sm font-medium text-slate-800">{p.projectName}</span>
                          {p.category && <span className="text-xs text-slate-400">{p.category}</span>}
                        </div>
                        <div className="flex gap-1">
                          {Array.from({ length: p.phases }).map((_, i) => (
                            <div key={i} className={cn("h-1.5 flex-1 rounded-full",
                              i < p.currentPhase ? "bg-blue-500" : "bg-slate-200")} />
                          ))}
                        </div>
                      </div>
                      <div className="flex items-center gap-3 shrink-0">
                        <span className="text-xs text-slate-400">{p.currentPhase}/{p.phases}</span>
                        <Badge variant="secondary" className={cn("text-xs", statusStyles[statusLabel] ?? "")}>
                          {statusLabel}
                        </Badge>
                      </div>
                    </div>
                  );
                })}
                {data.myProjects.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无参与项目</p>
                )}
              </div>
            </div>

            {/* Category Distribution */}
            <div className="glass-card rounded-2xl p-6 shadow-sm">
              <h3 className="text-lg font-bold text-slate-800 mb-5">项目分类分布</h3>
              <div className="space-y-4">
                {data.categoryDistribution.map((cat) => {
                  const pct = totalCategoryCount > 0 ? ((cat.count / totalCategoryCount) * 100).toFixed(1) : "0";
                  return (
                    <div key={cat.category} className="flex items-center gap-3">
                      <div className={cn("w-3 h-3 rounded-full shrink-0", categoryColors[cat.category] ?? "bg-slate-400")} />
                      <div className="flex-1">
                        <div className="flex justify-between text-sm mb-1">
                          <span className="font-medium text-slate-700">{cat.category} · {cat.name}</span>
                          <span className="text-slate-500">{cat.count} 个</span>
                        </div>
                        <div className="w-full bg-slate-100 rounded-full h-1.5">
                          <div className={cn("h-1.5 rounded-full", categoryColors[cat.category] ?? "bg-slate-400")}
                            style={{ width: `${pct}%` }} />
                        </div>
                      </div>
                    </div>
                  );
                })}
                {data.categoryDistribution.length === 0 && (
                  <p className="text-sm text-slate-400 text-center py-4">暂无数据</p>
                )}
              </div>
            </div>
          </div>
        </>
      )}

      {/* 发布公告弹窗 */}
      <CreateAnnouncementDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={fetchAnnouncements}
      />
    </div>
  );
}

// ==================== 公告卡片 ====================

const priorityStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-amber-100 text-amber-700",
  2: "bg-rose-100 text-rose-700",
};

function formatPublishTime(t: string | null): string {
  if (!t) return "";
  return t.replace("T", " ").slice(0, 16);
}

function AnnouncementCard({
  loading,
  items,
  onCreate,
  onDelete,
}: {
  loading: boolean;
  items: AnnouncementVo[];
  onCreate: () => void;
  onDelete: (id: string) => void;
}) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <div className="glass-card rounded-2xl p-6 shadow-sm">
      <div className="flex justify-between items-center mb-4">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
            <Megaphone className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-800">系统公告</h3>
            <p className="text-xs text-slate-400">{items.length} 条最新公告</p>
          </div>
        </div>
        <Button
          onClick={onCreate}
          size="sm"
          className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1"
        >
          <Plus className="w-4 h-4" /> 发布公告
        </Button>
      </div>

      {loading && (
        <div className="py-8 text-center">
          <Loader2 className="w-5 h-5 animate-spin text-blue-500 mx-auto" />
        </div>
      )}

      {!loading && items.length === 0 && (
        <div className="py-8 text-center">
          <Megaphone className="w-10 h-10 text-slate-300 mx-auto mb-2" />
          <p className="text-sm text-slate-400">暂无公告</p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="space-y-2">
          {items.map((a) => {
            const isExpanded = expandedId === a.announcementId;
            return (
              <div
                key={a.announcementId}
                className="p-3 rounded-xl bg-slate-50/50 hover:bg-slate-100/60 transition-colors group"
              >
                <div className="flex items-start gap-3">
                  <Badge
                    variant="secondary"
                    className={cn("text-xs shrink-0 mt-0.5", priorityStyles[a.priority] ?? priorityStyles[0])}
                  >
                    {ANNOUNCEMENT_PRIORITY_MAP[a.priority] ?? "普通"}
                  </Badge>
                  <button
                    onClick={() => setExpandedId(isExpanded ? null : a.announcementId)}
                    className="flex-1 min-w-0 text-left"
                  >
                    <p className="text-sm font-semibold text-slate-800 truncate">{a.title}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-xs text-slate-400">
                        {a.publishUserName ?? "-"}
                      </span>
                      <span className="text-xs text-slate-300">·</span>
                      <span className="text-xs text-slate-400">{formatPublishTime(a.publishTime)}</span>
                    </div>
                  </button>
                  <button
                    onClick={() => onDelete(a.announcementId)}
                    title="删除"
                    className="opacity-0 group-hover:opacity-100 transition-opacity text-slate-400 hover:text-rose-500 shrink-0"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
                {isExpanded && (
                  <div className="mt-3 pl-16 pr-2 text-sm text-slate-600 whitespace-pre-wrap leading-relaxed">
                    {a.content}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ==================== 发布公告弹窗 ====================

function CreateAnnouncementDialog({
  open,
  onOpenChange,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onSuccess: () => void;
}) {
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [priority, setPriority] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setTitle("");
      setContent("");
      setPriority(0);
    }
  }, [open]);

  const handleSubmit = async (status: number) => {
    if (!title.trim()) { toast.error("请输入标题"); return; }
    if (!content.trim()) { toast.error("请输入内容"); return; }

    setSubmitting(true);
    try {
      const res = await createAnnouncement({
        title: title.trim(),
        content,
        priority,
        status,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(status === 1 ? "公告已发布" : "已保存为草稿");
        onOpenChange(false);
        onSuccess();
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setSubmitting(false); }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-xl overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-xl font-bold text-slate-800">发布公告</h2>
          <button
            onClick={() => onOpenChange(false)}
            className="text-slate-400 hover:text-slate-600"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 space-y-4">
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
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
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
              内容 <span className="text-rose-500">*</span>
            </label>
            <textarea
              rows={6}
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
          <Button
            variant="outline"
            onClick={() => handleSubmit(0)}
            disabled={submitting}
          >
            保存草稿
          </Button>
          <Button
            onClick={() => handleSubmit(1)}
            disabled={submitting}
            className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-2"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            立即发布
          </Button>
        </div>
      </div>
    </div>
  );
}
