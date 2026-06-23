"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import {
  getBusinessRuleConfigList,
  toggleBusinessRuleConfig,
} from "@/services/config-center";
import { ResponseCode } from "@/types/api";
import type {
  BusinessRuleConfigVo,
  ConfigBizType,
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

const valueTypeLabel: Record<string, string> = {
  boolean: "布尔",
  number: "数字",
  string: "文本",
  json: "JSON",
};

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

export default function BusinessRuleConfigPage() {
  const [items, setItems] = useState<BusinessRuleConfigVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [toggleLoadingId, setToggleLoadingId] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [bizType, setBizType] = useState("all");
  const [enabled, setEnabled] = useState("all");

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

  const handleToggle = async (item: BusinessRuleConfigVo) => {
    setToggleLoadingId(item.ruleConfigId);
    try {
      const res = await toggleBusinessRuleConfig(item.ruleConfigId, item.enabled === 1 ? 0 : 1);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(item.enabled === 1 ? "已停用" : "已启用");
        fetchList();
      }
    } finally {
      setToggleLoadingId(null);
    }
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">业务规则配置</h1>
          <p className="text-sm text-slate-500 mt-1">业务规则只允许启用或停用，规则内容由系统维护</p>
        </div>
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
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={toggleLoadingId === item.ruleConfigId}
                        onClick={() => handleToggle(item)}
                      >
                        {item.enabled === 1 ? "停用" : "启用"}
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
    </div>
  );
}
