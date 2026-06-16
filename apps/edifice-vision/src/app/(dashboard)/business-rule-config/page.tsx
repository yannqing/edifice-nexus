"use client";

import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  Pencil,
  Plus,
  Search,
  SlidersHorizontal,
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
  deleteBusinessRuleConfig,
  getBusinessRuleConfigList,
  saveBusinessRuleConfig,
  toggleBusinessRuleConfig,
} from "@/services/config-center";
import { ResponseCode } from "@/types/api";
import type {
  BusinessRuleConfigVo,
  ConfigBizType,
  SaveBusinessRuleConfigParams,
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

const valueTypeOptions = [
  { value: "boolean", label: "布尔" },
  { value: "number", label: "数字" },
  { value: "string", label: "文本" },
  { value: "json", label: "JSON" },
];

const valueTypeLabel: Record<string, string> = {
  boolean: "布尔",
  number: "数字",
  string: "文本",
  json: "JSON",
};

function emptyForm(): SaveBusinessRuleConfigParams {
  return {
    bizType: "output",
    ruleKey: "",
    ruleName: "",
    ruleValue: "true",
    valueType: "boolean",
    enabled: 1,
    description: "",
  };
}

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

export default function BusinessRuleConfigPage() {
  const [items, setItems] = useState<BusinessRuleConfigVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [bizType, setBizType] = useState("all");
  const [enabled, setEnabled] = useState("all");
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<SaveBusinessRuleConfigParams>(emptyForm());

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
      const res = await getBusinessRuleConfigList(
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

  const openEdit = (item: BusinessRuleConfigVo) => {
    setForm({
      ruleConfigId: item.ruleConfigId,
      bizType: item.bizType,
      ruleKey: item.ruleKey,
      ruleName: item.ruleName,
      ruleValue: item.ruleValue,
      valueType: item.valueType,
      enabled: item.enabled,
      description: item.description ?? "",
    });
    setFormOpen(true);
  };

  const handleSave = async () => {
    if (!form.ruleKey.trim() || !form.ruleName.trim()) {
      toast.error("请填写规则编码和规则名称");
      return;
    }
    if (form.valueType === "json") {
      try {
        JSON.parse(form.ruleValue || "{}");
      } catch {
        toast.error("规则值不是合法 JSON");
        return;
      }
    }
    if (form.valueType === "number" && Number.isNaN(Number(form.ruleValue))) {
      toast.error("规则值必须是数字");
      return;
    }
    setSaving(true);
    try {
      const res = await saveBusinessRuleConfig({
        ...form,
        ruleKey: form.ruleKey.trim(),
        ruleName: form.ruleName.trim(),
        ruleValue: form.ruleValue.trim(),
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

  const handleToggle = async (item: BusinessRuleConfigVo) => {
    const res = await toggleBusinessRuleConfig(item.ruleConfigId, item.enabled === 1 ? 0 : 1);
    if (res.code === ResponseCode.SUCCESS) {
      toast.success(item.enabled === 1 ? "已停用" : "已启用");
      fetchList();
    }
  };

  const handleDelete = async (item: BusinessRuleConfigVo) => {
    if (!window.confirm(`确定删除业务规则「${item.ruleName}」吗？`)) return;
    const res = await deleteBusinessRuleConfig(item.ruleConfigId);
    if (res.code === ResponseCode.SUCCESS) {
      toast.success("已删除");
      fetchList();
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">业务规则配置</h1>
          <p className="text-sm text-slate-500 mt-1">集中管理业务开关、校验条件和计算规则参数</p>
        </div>
        <Button onClick={openCreate} className="bg-blue-600 hover:bg-blue-700">
          <Plus className="w-4 h-4 mr-2" /> 新建规则
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[1fr_180px_160px] gap-3">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索规则名称、编码或说明"
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
          <SlidersHorizontal className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-sm text-slate-500">暂无业务规则</p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="w-full min-w-[980px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-left text-slate-500">
                <th className="px-5 py-4 font-medium">规则</th>
                <th className="px-5 py-4 font-medium">业务类型</th>
                <th className="px-5 py-4 font-medium">规则值</th>
                <th className="px-5 py-4 font-medium">值类型</th>
                <th className="px-5 py-4 font-medium">状态</th>
                <th className="px-5 py-4 font-medium">更新时间</th>
                <th className="px-5 py-4 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.ruleConfigId} className="border-b border-slate-50 hover:bg-slate-50">
                  <td className="px-5 py-4 max-w-md">
                    <div className="font-medium text-slate-900">{item.ruleName}</div>
                    <div className="text-xs text-slate-400 mt-1">{item.ruleKey}</div>
                    {item.description && <div className="text-xs text-slate-500 mt-2 line-clamp-2">{item.description}</div>}
                  </td>
                  <td className="px-5 py-4"><Badge variant="secondary">{item.bizTypeLabel}</Badge></td>
                  <td className="px-5 py-4">
                    <code className="px-2 py-1 rounded-md bg-slate-100 text-slate-700 text-xs break-all">
                      {item.ruleValue}
                    </code>
                  </td>
                  <td className="px-5 py-4 text-slate-600">{valueTypeLabel[item.valueType] ?? item.valueType}</td>
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
        <DialogContent className="max-w-2xl max-h-[86vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{form.ruleConfigId ? "编辑业务规则" : "新建业务规则"}</DialogTitle>
            <DialogDescription>规则会保存为结构化配置，后续业务服务按 ruleKey 逐步接入读取。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="业务类型" required>
                <select
                  value={form.bizType}
                  disabled={Boolean(form.ruleConfigId)}
                  onChange={(event) => setForm((prev) => ({ ...prev, bizType: event.target.value as ConfigBizType }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                >
                  {bizTypeOptions.filter((option) => option.value !== "all").map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </Field>
              <Field label="值类型" required>
                <select
                  value={form.valueType}
                  onChange={(event) => setForm((prev) => ({
                    ...prev,
                    valueType: event.target.value,
                    ruleValue: event.target.value === "boolean" ? "true" : event.target.value === "json" ? "{}" : "",
                  }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                >
                  {valueTypeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
              </Field>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="规则编码" required>
                <input
                  value={form.ruleKey}
                  disabled={Boolean(form.ruleConfigId)}
                  onChange={(event) => setForm((prev) => ({ ...prev, ruleKey: event.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                  placeholder="例如 require_materials"
                />
              </Field>
              <Field label="规则名称" required>
                <input
                  value={form.ruleName}
                  onChange={(event) => setForm((prev) => ({ ...prev, ruleName: event.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                  placeholder="请输入规则名称"
                />
              </Field>
            </div>

            <Field label="规则值" required>
              {form.valueType === "boolean" ? (
                <select
                  value={form.ruleValue}
                  onChange={(event) => setForm((prev) => ({ ...prev, ruleValue: event.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                >
                  <option value="true">true</option>
                  <option value="false">false</option>
                </select>
              ) : form.valueType === "json" ? (
                <textarea
                  rows={5}
                  value={form.ruleValue}
                  onChange={(event) => setForm((prev) => ({ ...prev, ruleValue: event.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white font-mono"
                  placeholder='{"enabled": true}'
                />
              ) : (
                <input
                  value={form.ruleValue}
                  onChange={(event) => setForm((prev) => ({ ...prev, ruleValue: event.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                  placeholder={form.valueType === "number" ? "请输入数字" : "请输入文本"}
                />
              )}
            </Field>

            <SwitchField label="启用规则" value={form.enabled} onChange={(value) => setForm((prev) => ({ ...prev, enabled: value }))} />

            <Field label="规则说明">
              <textarea
                rows={3}
                value={form.description ?? ""}
                onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                placeholder="说明规则影响范围、业务含义和注意事项"
              />
            </Field>
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
        "w-full flex items-center justify-between gap-3 px-3 py-2 rounded-lg border text-sm transition-colors",
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
