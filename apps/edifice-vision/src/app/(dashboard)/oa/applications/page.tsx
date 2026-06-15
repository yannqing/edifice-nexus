"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Check,
  Loader2,
  Plus,
  RotateCcw,
  Search,
  Send,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import type { SysUserListItem } from "@/services/user";
import { getUserList } from "@/services/user";
import {
  OA_PRIORITY_MAP,
  OA_STATUS_MAP,
  type OaApplication,
  type OaApplicationType,
} from "@/types/oa";
import {
  approveOaApplication,
  createOaApplication,
  getOaApplications,
  getOaApplicationTypes,
  getOaSsoToken,
  getPendingOaApplications,
  submitOaApplication,
  withdrawOaApplication,
} from "@/services/oa";

const PAGE_SIZE = 10;
const PHASE2_TYPES = new Set(["general", "leave", "business_trip", "makeup_card", "outgoing", "probation", "resignation"]);
const QUICK_APPLICATION_TYPES = [
  { type: "leave", label: "请假" },
  { type: "business_trip", label: "出差" },
  { type: "makeup_card", label: "补卡" },
  { type: "outgoing", label: "外出申请" },
  { type: "probation", label: "转正申请" },
  { type: "resignation", label: "离职" },
  { type: "general", label: "通用审批" },
];

type StatusTab = "all" | "draft" | "approving" | "pending" | "done";
type FieldDef = { key: string; label: string; type?: "text" | "number" | "date" | "datetime-local" | "textarea" | "select"; options?: string[] };

const statusFilterMap: Record<Exclude<StatusTab, "pending" | "done">, number | undefined> = {
  all: undefined,
  draft: 0,
  approving: 1,
};

const statusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-blue-100 text-blue-700",
  2: "bg-emerald-100 text-emerald-700",
  3: "bg-rose-100 text-rose-700",
  4: "bg-slate-200 text-slate-600",
};

const fieldDefs: Record<string, FieldDef[]> = {
  general: [
    { key: "审批内容", label: "审批内容", type: "textarea" },
    { key: "期望完成时间", label: "期望完成时间", type: "date" },
  ],
  leave: [
    { key: "请假类型", label: "请假类型", type: "select", options: ["事假", "病假", "年假", "调休", "其他"] },
    { key: "开始时间", label: "开始时间", type: "datetime-local" },
    { key: "结束时间", label: "结束时间", type: "datetime-local" },
    { key: "天数", label: "天数", type: "number" },
    { key: "事由", label: "事由", type: "textarea" },
  ],
  business_trip: [
    { key: "目的地", label: "目的地" },
    { key: "开始日期", label: "开始日期", type: "date" },
    { key: "结束日期", label: "结束日期", type: "date" },
    { key: "预算", label: "预算", type: "number" },
    { key: "出差事由", label: "出差事由", type: "textarea" },
  ],
  makeup_card: [
    { key: "补卡日期", label: "补卡日期", type: "date" },
    { key: "补卡时段", label: "补卡时段", type: "select", options: ["上班", "下班", "全天"] },
    { key: "原因", label: "原因", type: "textarea" },
  ],
  outgoing: [
    { key: "外出时间", label: "外出时间", type: "datetime-local" },
    { key: "预计返回", label: "预计返回", type: "datetime-local" },
    { key: "目的地", label: "目的地" },
    { key: "事由", label: "事由", type: "textarea" },
  ],
  probation: [
    { key: "入职日期", label: "入职日期", type: "date" },
    { key: "转正日期", label: "转正日期", type: "date" },
    { key: "自评", label: "自评", type: "textarea" },
  ],
  resignation: [
    { key: "最后工作日", label: "最后工作日", type: "date" },
    { key: "交接人", label: "交接人" },
    { key: "离职原因", label: "离职原因", type: "textarea" },
  ],
};

function formatDate(value?: string | null) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

function summarizeFormData(data: Record<string, unknown>) {
  const entries = Object.entries(data ?? {}).filter(([, value]) => value !== undefined && value !== "");
  if (entries.length === 0) return "-";
  return entries.slice(0, 3).map(([key, value]) => `${key}: ${String(value)}`).join(" / ");
}

export default function OaApplicationsPage() {
  const [types, setTypes] = useState<OaApplicationType[]>([]);
  const [users, setUsers] = useState<SysUserListItem[]>([]);
  const [items, setItems] = useState<OaApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<StatusTab>("all");
  const [selectedType, setSelectedType] = useState("");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [formOpen, setFormOpen] = useState(false);
  const [submitTarget, setSubmitTarget] = useState<OaApplication | null>(null);
  const [approveTarget, setApproveTarget] = useState<OaApplication | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [applicationType, setApplicationType] = useState("general");
  const [title, setTitle] = useState("");
  const [priority, setPriority] = useState(0);
  const [firstApproverId, setFirstApproverId] = useState("");
  const [submitDescription, setSubmitDescription] = useState("");
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});

  const [approvePass, setApprovePass] = useState(true);
  const [nextApproverId, setNextApproverId] = useState("");
  const [approveComment, setApproveComment] = useState("");

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
    getUserList({ current: 1, pageSize: 100 }).then((res) => {
      if (res.code === ResponseCode.SUCCESS) setUsers(res.data?.records ?? []);
    });
  }, []);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, selectedType, debouncedSearch]);

  useEffect(() => {
    setFieldValues({});
  }, [applicationType]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const params = {
        keywords: debouncedSearch || undefined,
        applicationType: selectedType || undefined,
        current: currentPage,
        pageSize: PAGE_SIZE,
      };
      const res = activeTab === "pending"
        ? await getPendingOaApplications(params, signal)
        : await getOaApplications({
            ...params,
            status: activeTab === "done" ? undefined : statusFilterMap[activeTab],
            mine: true,
          }, signal);
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
    { key: "all" as const, label: "我的申请" },
    { key: "draft" as const, label: "草稿" },
    { key: "approving" as const, label: "审批中" },
    { key: "pending" as const, label: "待我审批" },
    { key: "done" as const, label: "已结束" },
  ], []);
  const visibleTypes = types.filter((type) => PHASE2_TYPES.has(type.type));
  const currentFields = fieldDefs[applicationType] ?? fieldDefs.general;

  const handleOpenOa = async () => {
    setActionLoading("open-oa");
    try {
      const res = await getOaSsoToken();
      if (res.code === ResponseCode.SUCCESS && res.data?.token) {
        const url = `${res.data.oaUrl.replace(/\/$/, "")}/home/sso/login?ssoToken=${encodeURIComponent(res.data.token)}`;
        window.open(url, "_blank", "noopener,noreferrer");
      }
    } finally {
      setActionLoading(null);
    }
  };

  const resetForm = (nextType = visibleTypes[0]?.type ?? "general") => {
    setTitle("");
    setPriority(0);
    setFirstApproverId("");
    setSubmitDescription("");
    setFieldValues({});
    setApplicationType(nextType);
  };

  const openCreateDialog = (nextType = visibleTypes[0]?.type ?? "general") => {
    resetForm(nextType);
    setFormOpen(true);
  };

  const buildFormData = () => Object.fromEntries(
    Object.entries(fieldValues).filter(([, value]) => value !== "")
  );

  const handleCreate = async (submitNow: boolean) => {
    if (!title.trim()) {
      toast.error("申请标题不能为空");
      return;
    }
    if (submitNow && !firstApproverId) {
      toast.error("请选择首审人");
      return;
    }
    setActionLoading(submitNow ? "create-submit" : "create-draft");
    try {
      const res = await createOaApplication({
        applicationType,
        title: title.trim(),
        priority,
        status: submitNow ? 1 : 0,
        firstApproverId: submitNow ? firstApproverId : undefined,
        submitDescription,
        formData: buildFormData(),
        attachmentIds: [],
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(submitNow ? "已提交申请" : "已保存草稿");
        setFormOpen(false);
        resetForm();
        fetchList();
      }
    } finally {
      setActionLoading(null);
    }
  };

  const handleSubmitDraft = async () => {
    if (!submitTarget || !firstApproverId) {
      toast.error("请选择首审人");
      return;
    }
    setActionLoading(submitTarget.applicationId);
    try {
      const res = await submitOaApplication(submitTarget.applicationId, {
        firstApproverId,
        description: submitDescription,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已提交");
        setSubmitTarget(null);
        setFirstApproverId("");
        setSubmitDescription("");
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

  const handleApprove = async () => {
    if (!approveTarget?.currentRecordId) return;
    setActionLoading(approveTarget.applicationId);
    try {
      const res = await approveOaApplication({
        recordId: approveTarget.currentRecordId,
        pass: approvePass,
        nextApproverId: approvePass && nextApproverId ? nextApproverId : undefined,
        comment: approveComment,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(approvePass ? "已通过" : "已驳回");
        setApproveTarget(null);
        setApprovePass(true);
        setNextApproverId("");
        setApproveComment("");
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
          <Button onClick={() => openCreateDialog()} className="bg-blue-600 hover:bg-blue-700">
            <Plus className="w-4 h-4" />
            新建申请
          </Button>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold text-slate-800">快速发起</h2>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
          {QUICK_APPLICATION_TYPES.map((item) => (
            <button
              key={item.type}
              type="button"
              onClick={() => openCreateDialog(item.type)}
              className="flex h-12 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-medium text-slate-700 transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"
            >
              <Plus className="h-4 w-4" />
              {item.label}
            </button>
          ))}
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
                activeTab === item.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700"
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
          {visibleTypes.map((type) => <option key={type.type} value={type.type}>{type.label}</option>)}
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

      {loading && <TablePageSkeleton columns={6} rows={5} />}
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
                <th className="text-left py-4 px-4 font-semibold">当前审批人</th>
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
                    <td className="py-4 px-4 text-sm text-slate-600">{item.currentApproverName ?? "-"}</td>
                    <td className="py-4 px-4 text-sm text-slate-500">{formatDate(item.submittedTime ?? item.createdTime)}</td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex justify-end gap-1">
                        {item.status === 0 && (
                          <Button variant="ghost" size="sm" disabled={isBusy} onClick={() => setSubmitTarget(item)}>
                            <Send className="w-4 h-4" />
                            提交
                          </Button>
                        )}
                        {item.status === 1 && activeTab !== "pending" && (
                          <Button variant="ghost" size="sm" disabled={isBusy} onClick={() => handleWithdraw(item.applicationId)}>
                            {isBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <RotateCcw className="w-4 h-4" />}
                            撤回
                          </Button>
                        )}
                        {activeTab === "pending" && item.currentRecordId && (
                          <Button variant="ghost" size="sm" disabled={isBusy} onClick={() => setApproveTarget(item)}>
                            <Check className="w-4 h-4" />
                            审批
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
            <Button variant="outline" size="sm" disabled={currentPage <= 1} onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}>
              <ChevronLeft className="w-4 h-4" />
              上一页
            </Button>
            <span className="text-sm text-slate-500">{currentPage} / {totalPages}</span>
            <Button variant="outline" size="sm" disabled={currentPage >= totalPages} onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}>
              下一页
              <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      )}

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>新建 OA 申请</DialogTitle></DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectField label="申请类型" value={applicationType} onChange={setApplicationType} options={visibleTypes.map((t) => ({ value: t.type, label: t.label }))} />
              <SelectField label="优先级" value={String(priority)} onChange={(v) => setPriority(Number(v))} options={[0, 1, 2].map((v) => ({ value: String(v), label: OA_PRIORITY_MAP[v] }))} />
            </div>
            <TextField label="标题" value={title} onChange={setTitle} />
            <div className="grid gap-4 sm:grid-cols-2">
              {currentFields.map((field) => (
                <DynamicField
                  key={field.key}
                  field={field}
                  value={fieldValues[field.key] ?? ""}
                  onChange={(value) => setFieldValues((prev) => ({ ...prev, [field.key]: value }))}
                />
              ))}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectField label="首审人" value={firstApproverId} onChange={setFirstApproverId} options={users.map(userOption)} placeholder="保存草稿可不选" />
              <TextField label="提交说明" value={submitDescription} onChange={setSubmitDescription} />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" onClick={() => setFormOpen(false)}>取消</Button>
              <Button variant="secondary" disabled={actionLoading === "create-draft"} onClick={() => handleCreate(false)}>
                {actionLoading === "create-draft" && <Loader2 className="w-4 h-4 animate-spin" />}
                保存草稿
              </Button>
              <Button className="bg-blue-600 hover:bg-blue-700" disabled={actionLoading === "create-submit"} onClick={() => handleCreate(true)}>
                {actionLoading === "create-submit" && <Loader2 className="w-4 h-4 animate-spin" />}
                提交申请
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={!!submitTarget} onOpenChange={(open) => !open && setSubmitTarget(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>提交申请</DialogTitle></DialogHeader>
          <div className="space-y-4 pt-2">
            <SelectField label="首审人" value={firstApproverId} onChange={setFirstApproverId} options={users.map(userOption)} />
            <TextField label="提交说明" value={submitDescription} onChange={setSubmitDescription} />
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setSubmitTarget(null)}>取消</Button>
              <Button className="bg-blue-600 hover:bg-blue-700" onClick={handleSubmitDraft}>提交</Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={!!approveTarget} onOpenChange={(open) => !open && setApproveTarget(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>审批申请</DialogTitle></DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="flex gap-2">
              <Button variant={approvePass ? "default" : "outline"} onClick={() => setApprovePass(true)}>
                <Check className="w-4 h-4" /> 通过
              </Button>
              <Button variant={!approvePass ? "destructive" : "outline"} onClick={() => setApprovePass(false)}>
                <X className="w-4 h-4" /> 驳回
              </Button>
            </div>
            {approvePass && (
              <SelectField label="下一级审批人" value={nextApproverId} onChange={setNextApproverId} options={users.map(userOption)} placeholder="不选则终审通过" />
            )}
            <label className="space-y-1.5 block">
              <span className="text-sm font-medium text-slate-700">审批意见</span>
              <textarea
                value={approveComment}
                onChange={(e) => setApproveComment(e.target.value)}
                rows={4}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setApproveTarget(null)}>取消</Button>
              <Button className="bg-blue-600 hover:bg-blue-700" onClick={handleApprove}>确认</Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function TextField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="space-y-1.5 block">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full h-10 rounded-lg border border-slate-200 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
    </label>
  );
}

function SelectField({
  label,
  value,
  onChange,
  options,
  placeholder = "请选择",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
}) {
  return (
    <label className="space-y-1.5 block">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option value="">{placeholder}</option>
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    </label>
  );
}

function DynamicField({ field, value, onChange }: { field: FieldDef; value: string; onChange: (value: string) => void }) {
  if (field.type === "textarea") {
    return (
      <label className="space-y-1.5 block sm:col-span-2">
        <span className="text-sm font-medium text-slate-700">{field.label}</span>
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          rows={4}
          className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </label>
    );
  }
  if (field.type === "select") {
    return <SelectField label={field.label} value={value} onChange={onChange} options={(field.options ?? []).map((option) => ({ value: option, label: option }))} />;
  }
  return (
    <label className="space-y-1.5 block">
      <span className="text-sm font-medium text-slate-700">{field.label}</span>
      <input
        type={field.type ?? "text"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full h-10 rounded-lg border border-slate-200 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
    </label>
  );
}

function userOption(user: SysUserListItem) {
  return {
    value: user.userId,
    label: user.realName || user.username,
  };
}
