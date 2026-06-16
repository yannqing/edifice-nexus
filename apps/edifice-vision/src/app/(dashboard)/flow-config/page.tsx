"use client";

import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import {
  ChevronLeft,
  ChevronRight,
  GitBranch,
  Loader2,
  Pencil,
  Plus,
  Search,
  Trash2,
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
import {
  deleteFlowConfig,
  getFlowConfigList,
  saveFlowConfig,
  toggleFlowConfig,
} from "@/services/config-center";
import { ResponseCode } from "@/types/api";
import type {
  ApprovalFlowConfigVo,
  ApprovalFlowNodeVo,
  ConfigBizType,
  SaveApprovalFlowConfigParams,
} from "@/types/config-center";

const PAGE_SIZE = 10;

const bizTypeOptions: Array<{ value: ConfigBizType | "all"; label: string }> = [
  { value: "all", label: "全部业务" },
  { value: "inspection", label: "验工单" },
  { value: "file", label: "项目文件" },
  { value: "output", label: "产值分配" },
  { value: "timesheet", label: "工时" },
  { value: "bid", label: "投标" },
  { value: "acceptance", label: "验收" },
  { value: "oa_application", label: "OA申请" },
];

const approverSourceOptions = [
  { value: "starter_select", label: "发起/审批人自选" },
  { value: "user", label: "指定用户" },
  { value: "role", label: "指定角色" },
  { value: "position", label: "指定岗位" },
];

const defaultNode = (order: number): ApprovalFlowNodeVo => ({
  nodeOrder: order,
  nodeName: `第 ${order} 级审批`,
  approverSourceType: "starter_select",
  approverSourceId: "",
  allowTerminate: order === 1 ? 1 : 0,
  requiredNode: 1,
});

function emptyForm(): SaveApprovalFlowConfigParams {
  return {
    bizType: "inspection",
    flowName: "",
    enabled: 1,
    allowWithdraw: 1,
    allowUrge: 1,
    allowCc: 1,
    allowStarterSelectNext: 1,
    version: 1,
    status: 1,
    remark: "",
    nodes: [defaultNode(1)],
  };
}

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

export default function FlowConfigPage() {
  const [items, setItems] = useState<ApprovalFlowConfigVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [bizType, setBizType] = useState("all");
  const [enabled, setEnabled] = useState("all");
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<SaveApprovalFlowConfigParams>(emptyForm());

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getFlowConfigList(
        {
          bizType: bizType === "all" ? undefined : bizType,
          enabled: enabled === "all" ? undefined : Number(enabled),
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
  }, [bizType, currentPage, debouncedKeyword, enabled]);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const openCreate = () => {
    setForm(emptyForm());
    setFormOpen(true);
  };

  const openEdit = (item: ApprovalFlowConfigVo) => {
    setForm({
      flowConfigId: item.flowConfigId,
      bizType: item.bizType,
      flowName: item.flowName,
      enabled: item.enabled,
      allowWithdraw: item.allowWithdraw,
      allowUrge: item.allowUrge,
      allowCc: item.allowCc,
      allowStarterSelectNext: item.allowStarterSelectNext,
      version: item.version,
      status: item.status,
      remark: item.remark ?? "",
      nodes: item.nodes.length > 0 ? item.nodes : [defaultNode(1)],
    });
    setFormOpen(true);
  };

  const patchNode = (index: number, patch: Partial<ApprovalFlowNodeVo>) => {
    setForm((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node, idx) => idx === index ? { ...node, ...patch } : node),
    }));
  };

  const addNode = () => {
    setForm((prev) => ({
      ...prev,
      nodes: [...prev.nodes, defaultNode(prev.nodes.length + 1)],
    }));
  };

  const removeNode = (index: number) => {
    setForm((prev) => ({
      ...prev,
      nodes: prev.nodes
        .filter((_, idx) => idx !== index)
        .map((node, idx) => ({ ...node, nodeOrder: idx + 1 })),
    }));
  };

  const handleSave = async () => {
    if (!form.flowName.trim()) {
      toast.error("请输入流程名称");
      return;
    }
    if (form.nodes.some((node) => !node.nodeName.trim())) {
      toast.error("请补充审批节点名称");
      return;
    }
    if (form.nodes.some((node) => node.approverSourceType !== "starter_select" && !node.approverSourceId?.trim())) {
      toast.error("指定用户/角色/岗位时，请填写来源ID");
      return;
    }
    setSaving(true);
    try {
      const res = await saveFlowConfig({
        ...form,
        nodes: form.nodes.map((node, index) => ({
          ...node,
          nodeOrder: index + 1,
          approverSourceId: node.approverSourceType === "starter_select" ? undefined : node.approverSourceId,
        })),
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("保存成功");
        setFormOpen(false);
        fetchList();
      }
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (item: ApprovalFlowConfigVo) => {
    const res = await toggleFlowConfig(item.flowConfigId, item.enabled === 1 ? 0 : 1);
    if (res.code === ResponseCode.SUCCESS) {
      toast.success(item.enabled === 1 ? "已停用" : "已启用");
      fetchList();
    }
  };

  const handleDelete = async (item: ApprovalFlowConfigVo) => {
    if (!window.confirm(`确定删除流程配置「${item.flowName}」吗？`)) return;
    const res = await deleteFlowConfig(item.flowConfigId);
    if (res.code === ResponseCode.SUCCESS) {
      toast.success("已删除");
      fetchList();
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">流程配置中心</h1>
          <p className="text-sm text-slate-500 mt-1">配置各业务审批层级、审批人来源和流程操作能力</p>
        </div>
        <Button onClick={openCreate} className="bg-blue-600 hover:bg-blue-700">
          <Plus className="w-4 h-4 mr-2" /> 新建流程
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[1fr_180px_160px] gap-3">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索流程名称或备注"
            className="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <select
          value={bizType}
          onChange={(event) => { setBizType(event.target.value); setCurrentPage(1); }}
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {bizTypeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <select
          value={enabled}
          onChange={(event) => { setEnabled(event.target.value); setCurrentPage(1); }}
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="all">全部状态</option>
          <option value="1">已启用</option>
          <option value="0">已停用</option>
        </select>
      </div>

      {loading && <TablePageSkeleton columns={6} rows={6} />}
      {!loading && items.length === 0 && (
        <div className="border border-slate-200 bg-white rounded-lg p-12 text-center">
          <GitBranch className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-sm text-slate-500">暂无流程配置</p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="w-full min-w-[1060px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-left text-slate-500">
                <th className="px-5 py-4 font-medium">流程</th>
                <th className="px-5 py-4 font-medium">业务类型</th>
                <th className="px-5 py-4 font-medium">节点</th>
                <th className="px-5 py-4 font-medium">能力</th>
                <th className="px-5 py-4 font-medium">状态</th>
                <th className="px-5 py-4 font-medium">更新时间</th>
                <th className="px-5 py-4 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.flowConfigId} className="border-b border-slate-50 hover:bg-slate-50">
                  <td className="px-5 py-4">
                    <div className="font-medium text-slate-900">{item.flowName}</div>
                    <div className="text-xs text-slate-400 mt-1">v{item.version} · {item.remark || "暂无备注"}</div>
                  </td>
                  <td className="px-5 py-4"><Badge variant="secondary">{item.bizTypeLabel}</Badge></td>
                  <td className="px-5 py-4">
                    <div className="flex flex-wrap gap-1.5 max-w-md">
                      {item.nodes.map((node) => (
                        <span key={node.flowNodeId ?? `${item.flowConfigId}-${node.nodeOrder}`} className="px-2 py-1 rounded-md bg-slate-100 text-slate-600 text-xs">
                          {node.nodeOrder}. {node.nodeName}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex flex-wrap gap-1.5">
                      {item.allowWithdraw === 1 && <Badge className="bg-blue-50 text-blue-600 hover:bg-blue-50">撤回</Badge>}
                      {item.allowUrge === 1 && <Badge className="bg-amber-50 text-amber-600 hover:bg-amber-50">催办</Badge>}
                      {item.allowCc === 1 && <Badge className="bg-emerald-50 text-emerald-600 hover:bg-emerald-50">抄送</Badge>}
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <Badge className={cn(item.enabled === 1 ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500")}>
                      {item.enabled === 1 ? "已启用" : "已停用"}
                    </Badge>
                  </td>
                  <td className="px-5 py-4 text-slate-500 whitespace-nowrap">{formatTime(item.updatedTime)}</td>
                  <td className="px-5 py-4">
                    <div className="flex items-center justify-end gap-2">
                      <Button size="sm" variant="outline" onClick={() => openEdit(item)}>
                        <Pencil className="w-4 h-4 mr-1" /> 编辑
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => handleToggle(item)}>
                        {item.enabled === 1 ? "停用" : "启用"}
                      </Button>
                      <Button size="sm" variant="outline" className="text-rose-600" onClick={() => handleDelete(item)}>
                        <Trash2 className="w-4 h-4 mr-1" /> 删除
                      </Button>
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

      <Dialog open={formOpen} onOpenChange={(open) => !saving && setFormOpen(open)}>
        <DialogContent className="max-w-3xl max-h-[88vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{form.flowConfigId ? "编辑流程配置" : "新建流程配置"}</DialogTitle>
            <DialogDescription>第一版配置会保存为流程元数据，业务执行接入会逐个模块切换。</DialogDescription>
          </DialogHeader>

          <div className="space-y-5">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="业务类型" required>
                <select
                  value={form.bizType}
                  disabled={Boolean(form.flowConfigId)}
                  onChange={(event) => setForm((prev) => ({ ...prev, bizType: event.target.value as ConfigBizType }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                >
                  {bizTypeOptions.filter((option) => option.value !== "all").map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </Field>
              <Field label="流程名称" required>
                <input
                  value={form.flowName}
                  onChange={(event) => setForm((prev) => ({ ...prev, flowName: event.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                  placeholder="请输入流程名称"
                />
              </Field>
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
              <SwitchField label="启用" value={form.enabled} onChange={(value) => setForm((prev) => ({ ...prev, enabled: value }))} />
              <SwitchField label="允许撤回" value={form.allowWithdraw} onChange={(value) => setForm((prev) => ({ ...prev, allowWithdraw: value }))} />
              <SwitchField label="允许催办" value={form.allowUrge} onChange={(value) => setForm((prev) => ({ ...prev, allowUrge: value }))} />
              <SwitchField label="允许抄送" value={form.allowCc} onChange={(value) => setForm((prev) => ({ ...prev, allowCc: value }))} />
              <SwitchField label="自选下一级" value={form.allowStarterSelectNext} onChange={(value) => setForm((prev) => ({ ...prev, allowStarterSelectNext: value }))} />
            </div>

            <Field label="备注">
              <textarea
                rows={2}
                value={form.remark ?? ""}
                onChange={(event) => setForm((prev) => ({ ...prev, remark: event.target.value }))}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                placeholder="说明该流程适用范围或业务注意事项"
              />
            </Field>

            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-slate-800">审批节点</p>
                <Button type="button" size="sm" variant="outline" onClick={addNode}>
                  <Plus className="w-4 h-4 mr-1" /> 添加节点
                </Button>
              </div>
              <div className="space-y-3">
                {form.nodes.map((node, index) => (
                  <div key={index} className="border border-slate-200 rounded-lg p-3 space-y-3">
                    <div className="grid grid-cols-1 md:grid-cols-[80px_1fr_180px_1fr_90px] gap-3 items-end">
                      <Field label="顺序">
                        <input value={index + 1} disabled className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-slate-50" />
                      </Field>
                      <Field label="节点名称" required>
                        <input
                          value={node.nodeName}
                          onChange={(event) => patchNode(index, { nodeName: event.target.value })}
                          className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                        />
                      </Field>
                      <Field label="审批人来源" required>
                        <select
                          value={node.approverSourceType}
                          onChange={(event) => patchNode(index, {
                            approverSourceType: event.target.value,
                            approverSourceId: event.target.value === "starter_select" ? "" : node.approverSourceId,
                          })}
                          className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                        >
                          {approverSourceOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                        </select>
                      </Field>
                      <Field label="来源ID">
                        <input
                          value={node.approverSourceId ?? ""}
                          disabled={node.approverSourceType === "starter_select"}
                          onChange={(event) => patchNode(index, { approverSourceId: event.target.value })}
                          className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                          placeholder="用户/角色/岗位 ID"
                        />
                      </Field>
                      <Button
                        type="button"
                        variant="outline"
                        className="text-rose-600"
                        disabled={form.nodes.length <= 1}
                        onClick={() => removeNode(index)}
                      >
                        删除
                      </Button>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <SwitchField label="允许本节点终审" value={node.allowTerminate} onChange={(value) => patchNode(index, { allowTerminate: value })} />
                      <SwitchField label="必经节点" value={node.requiredNode} onChange={(value) => patchNode(index, { requiredNode: value })} />
                    </div>
                  </div>
                ))}
              </div>
            </section>
          </div>

          <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
            <Button variant="outline" disabled={saving} onClick={() => setFormOpen(false)}>取消</Button>
            <Button disabled={saving} onClick={handleSave}>
              {saving && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
              保存
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-600 mb-1 block">
        {label} {required && <span className="text-rose-500">*</span>}
      </span>
      {children}
    </label>
  );
}

function SwitchField({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <button
      type="button"
      onClick={() => onChange(value === 1 ? 0 : 1)}
      className={cn(
        "flex items-center justify-between gap-3 px-3 py-2 rounded-lg border text-sm transition-colors",
        value === 1 ? "border-blue-200 bg-blue-50 text-blue-700" : "border-slate-200 bg-white text-slate-500"
      )}
    >
      <span>{label}</span>
      <span className={cn("w-9 h-5 rounded-full p-0.5 transition-colors", value === 1 ? "bg-blue-600" : "bg-slate-300")}>
        <span className={cn("block w-4 h-4 rounded-full bg-white transition-transform", value === 1 ? "translate-x-4" : "translate-x-0")} />
      </span>
    </button>
  );
}
