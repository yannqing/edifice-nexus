"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Download,
  FileText,
  Loader2,
  Pencil,
  Search,
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
import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import {
  exportContractExcel,
  getContractChangeLogs,
  getContractList,
  updateContractInfo,
} from "@/services/contract-management";
import { ResponseCode } from "@/types/api";
import type {
  ContractListVo,
  ContractChangeLogVo,
  UpdateContractParams,
} from "@/types/contract-management";
import type { FilesVo } from "@/types/project";

const PAGE_SIZE = 10;

const contractTypeLabel: Record<number, string> = {
  0: "基本收费",
  1: "基本 + 效益",
};

const contractTypeStyle: Record<number, string> = {
  0: "bg-blue-100 text-blue-700",
  1: "bg-emerald-100 text-emerald-700",
};

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDate(value?: string | null) {
  return value?.slice(0, 10) || "-";
}

function toDateTimeInput(value?: string | null) {
  return value ? value.slice(0, 16) : "";
}

function normalizeDateTime(value?: string) {
  return value ? `${value}:00` : undefined;
}

function buildForm(item: ContractListVo): UpdateContractParams {
  return {
    contractId: item.contractId,
    contractName: item.contractName ?? "",
    contractCode: item.contractCode ?? "",
    contractType: item.contractType ?? 0,
    contractAmount: item.contractAmount ?? 0,
    baseAmount: item.baseAmount ?? 0,
    benefitRules: item.benefitRules ?? "",
    signingDate: toDateTimeInput(item.signingDate),
    preStartDate: toDateTimeInput(item.preStartDate),
    preEndDate: toDateTimeInput(item.preEndDate),
  };
}

export default function ContractManagementPage() {
  const [items, setItems] = useState<ContractListVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [contractType, setContractType] = useState("all");
  const [editing, setEditing] = useState<ContractListVo | null>(null);
  const [form, setForm] = useState<UpdateContractParams | null>(null);
  const [changeLogs, setChangeLogs] = useState<ContractChangeLogVo[]>([]);
  const [changeLogsLoading, setChangeLogsLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [exportLoading, setExportLoading] = useState(false);

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
      const res = await getContractList(
        {
          keywords: debouncedKeyword || undefined,
          contractType: contractType === "all" ? undefined : Number(contractType),
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
  }, [contractType, currentPage, debouncedKeyword]);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const openEdit = (item: ContractListVo) => {
    setEditing(item);
    setForm(buildForm(item));
    setChangeLogs([]);
    setChangeLogsLoading(true);
    getContractChangeLogs(item.contractId)
      .then((res) => {
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setChangeLogs(res.data);
        }
      })
      .finally(() => setChangeLogsLoading(false));
  };

  const handleSave = async () => {
    if (!form) return;
    if (!form.contractName?.trim() || !form.contractCode?.trim()) {
      toast.error("请填写合同名称和合同编号");
      return;
    }
    if (!form.contractAmount || form.contractAmount <= 0) {
      toast.error("合同金额必须大于 0");
      return;
    }
    if ((form.baseAmount ?? 0) < 0) {
      toast.error("基本收费不能小于 0");
      return;
    }
    setSaving(true);
    try {
      const res = await updateContractInfo({
        ...form,
        contractName: form.contractName.trim(),
        contractCode: form.contractCode.trim(),
        signingDate: normalizeDateTime(form.signingDate),
        preStartDate: normalizeDateTime(form.preStartDate),
        preEndDate: normalizeDateTime(form.preEndDate),
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("合同已更新");
        setEditing(null);
        setForm(null);
        fetchList();
      }
    } finally {
      setSaving(false);
    }
  };

  const handleExport = async () => {
    setExportLoading(true);
    try {
      await exportContractExcel({
        keywords: debouncedKeyword || undefined,
        contractType: contractType === "all" ? undefined : Number(contractType),
      });
      toast.success("导出成功");
    } catch {
      toast.error("导出失败，请稍后重试");
    } finally {
      setExportLoading(false);
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">合同管理</h1>
          <p className="text-slate-500 text-sm mt-1">集中查看和维护项目合同基础信息</p>
        </div>
        <Button
          onClick={handleExport}
          disabled={exportLoading}
          className="bg-blue-600 hover:bg-blue-700"
        >
          {exportLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Download className="w-4 h-4 mr-2" />}
          导出 Excel
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索合同 / 项目..."
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-full sm:w-80 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <select
          value={contractType}
          onChange={(event) => {
            setContractType(event.target.value);
            setCurrentPage(1);
          }}
          className="px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="all">全部合同类型</option>
          <option value="0">基本收费</option>
          <option value="1">基本 + 效益</option>
        </select>
      </div>

      {loading && <TablePageSkeleton columns={7} rows={5} />}

      {!loading && (
        <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
          <table className="w-full text-sm min-w-[980px]">
            <thead>
              <tr className="text-left text-slate-500 border-b border-slate-100">
                <th className="px-5 py-4 font-medium">合同</th>
                <th className="px-5 py-4 font-medium">关联项目</th>
                <th className="px-5 py-4 font-medium">类型</th>
                <th className="px-5 py-4 font-medium">合同金额</th>
                <th className="px-5 py-4 font-medium">基本 / 效益</th>
                <th className="px-5 py-4 font-medium">日期</th>
                <th className="px-5 py-4 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.contractId} className="border-b border-slate-50 last:border-0 hover:bg-slate-50/70">
                  <td className="px-5 py-4">
                    <div className="flex items-start gap-3">
                      <div className="w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
                        <FileText className="w-4 h-4" />
                      </div>
                      <div className="min-w-0">
                        <div className="font-semibold text-slate-800 truncate">{item.contractName}</div>
                        <div className="text-xs text-slate-400 mt-1">{item.contractCode || "-"}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <div className="font-medium text-slate-700">{item.projectName || "-"}</div>
                    <div className="text-xs text-slate-400 mt-1">{item.projectCode || "-"}</div>
                  </td>
                  <td className="px-5 py-4">
                    <Badge className={cn("hover:bg-inherit", contractTypeStyle[item.contractType])}>
                      {contractTypeLabel[item.contractType] ?? "未知"}
                    </Badge>
                  </td>
                  <td className="px-5 py-4 font-semibold text-slate-800">{formatMoney(item.contractAmount)}</td>
                  <td className="px-5 py-4 text-slate-600">
                    <div>基本：{formatMoney(item.baseAmount)}</div>
                    <div className="text-xs text-slate-400 mt-1">效益：{formatMoney(item.benefitAmount)}</div>
                  </td>
                  <td className="px-5 py-4 text-slate-500">
                    <div>签订：{formatDate(item.signingDate)}</div>
                    <div className="text-xs mt-1">预计：{formatDate(item.preStartDate)} 至 {formatDate(item.preEndDate)}</div>
                  </td>
                  <td className="px-5 py-4 text-right">
                    <Button variant="ghost" size="sm" onClick={() => openEdit(item)}>
                      <Pencil className="w-4 h-4 mr-1" /> 编辑
                    </Button>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-5 py-16 text-center text-slate-400">暂无合同数据</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between text-sm text-slate-500">
        <span>共 {total} 条</span>
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

      <Dialog open={Boolean(editing && form)} onOpenChange={(open) => {
        if (!open) {
          setEditing(null);
          setForm(null);
        }
      }}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>编辑合同</DialogTitle>
            <DialogDescription>维护合同基础字段，合同文件请仍通过项目附件流程管理。</DialogDescription>
          </DialogHeader>
          {form && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field label="合同名称">
                  <input value={form.contractName ?? ""} onChange={(event) => setForm({ ...form, contractName: event.target.value })} className="form-input" />
                </Field>
                <Field label="合同编号">
                  <input value={form.contractCode ?? ""} onChange={(event) => setForm({ ...form, contractCode: event.target.value })} className="form-input" />
                </Field>
                <Field label="合同类型">
                  <select value={form.contractType ?? 0} onChange={(event) => setForm({ ...form, contractType: Number(event.target.value) })} className="form-input">
                    <option value={0}>基本收费</option>
                    <option value={1}>基本 + 效益</option>
                  </select>
                </Field>
                <Field label="合同金额">
                  <input type="number" min={0} value={form.contractAmount ?? 0} onChange={(event) => setForm({ ...form, contractAmount: Number(event.target.value) })} className="form-input" />
                </Field>
                <Field label="基本收费金额">
                  <input type="number" min={0} value={form.baseAmount ?? 0} onChange={(event) => setForm({ ...form, baseAmount: Number(event.target.value) })} className="form-input" />
                </Field>
                <Field label="预计效益金额">
                  <div className="form-input bg-slate-50 text-slate-500">
                    {formatMoney(editing?.benefitAmount)}
                  </div>
                </Field>
                <Field label="签订日期">
                  <input type="datetime-local" value={form.signingDate ?? ""} onChange={(event) => setForm({ ...form, signingDate: event.target.value })} className="form-input" />
                </Field>
                <Field label="预计开始">
                  <input type="datetime-local" value={form.preStartDate ?? ""} onChange={(event) => setForm({ ...form, preStartDate: event.target.value })} className="form-input" />
                </Field>
                <Field label="预计结束">
                  <input type="datetime-local" value={form.preEndDate ?? ""} onChange={(event) => setForm({ ...form, preEndDate: event.target.value })} className="form-input" />
                </Field>
              </div>
              {editing && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
                    <div className="text-sm font-semibold text-slate-800 mb-3">合同文件</div>
                    <div className="space-y-2">
                      {editing.contractFileDetail && (
                        <ContractFileRow label="主合同" file={editing.contractFileDetail} />
                      )}
                      {(editing.contractAttachmentFiles ?? []).map((file, index) => (
                        <ContractFileRow key={file.fileId} label={`附件 ${index + 1}`} file={file} />
                      ))}
                      {!editing.contractFileDetail && (!editing.contractAttachmentFiles || editing.contractAttachmentFiles.length === 0) && (
                        <div className="text-sm text-slate-400">暂无合同文件</div>
                      )}
                    </div>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
                    <div className="text-sm font-semibold text-slate-800 mb-3">最近变更</div>
                    {changeLogsLoading && <div className="text-sm text-slate-400">加载中...</div>}
                    {!changeLogsLoading && changeLogs.length === 0 && (
                      <div className="text-sm text-slate-400">暂无变更记录</div>
                    )}
                    {!changeLogsLoading && changeLogs.length > 0 && (
                      <div className="space-y-3 max-h-56 overflow-y-auto pr-1">
                        {changeLogs.slice(0, 8).map((log) => (
                          <div key={log.changeLogId} className="text-sm">
                            <div className="flex items-center justify-between gap-2">
                              <span className="font-medium text-slate-700">{log.fieldLabel}</span>
                              <span className="text-xs text-slate-400">{formatDate(log.createdTime)}</span>
                            </div>
                            <div className="mt-1 text-xs text-slate-500 break-all">
                              {log.oldValue || "-"} → {log.newValue || "-"}
                            </div>
                            <div className="mt-1 text-xs text-slate-400">{log.operatorName || "未知操作人"}</div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
              <div className="flex justify-end gap-3 pt-2">
                <Button variant="outline" onClick={() => { setEditing(null); setForm(null); }}>
                  <X className="w-4 h-4 mr-1" /> 取消
                </Button>
                <Button onClick={handleSave} disabled={saving} className="bg-blue-600 hover:bg-blue-700">
                  {saving && <Loader2 className="w-4 h-4 mr-2 animate-spin" />} 保存
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-slate-600">{label}</span>
      {children}
    </label>
  );
}

function ContractFileRow({ label, file }: { label: string; file: FilesVo }) {
  const displayName = file.displayName || `${label}.${file.fileExtension || "file"}`;
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg bg-white px-3 py-2 border border-slate-100">
      <div className="min-w-0">
        <div className="text-xs text-slate-400">{label}</div>
        <div className="text-sm font-medium text-slate-700 truncate">{displayName}</div>
      </div>
      <AttachmentFileActions fileId={String(file.fileId)} fileName={displayName} />
    </div>
  );
}
