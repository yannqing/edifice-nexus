"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  ChevronLeft,
  ChevronRight,
  Clock,
  RefreshCcw,
  Search,
  ShieldCheck,
  Eye,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import type { OperationAuditLogVo } from "@/types/audit-log";
import { getOperationAuditLogList } from "@/services/audit-log";

const PAGE_SIZE = 10;

const methodOptions = ["ALL", "POST", "PUT", "DELETE", "PATCH"];

const statusOptions = [
  { value: "all", label: "全部状态" },
  { value: "1", label: "成功" },
  { value: "0", label: "失败" },
];

const methodStyles: Record<string, string> = {
  POST: "bg-blue-100 text-blue-700",
  PUT: "bg-amber-100 text-amber-700",
  DELETE: "bg-rose-100 text-rose-700",
  PATCH: "bg-purple-100 text-purple-700",
};

function formatDate(value?: string | null) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 19);
}

function formatSummary(value?: string | null) {
  if (!value || value === "{}") return "-";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export default function AuditLogsPage() {
  const [items, setItems] = useState<OperationAuditLogVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);

  const [operatorName, setOperatorName] = useState("");
  const [moduleName, setModuleName] = useState("");
  const [httpMethod, setHttpMethod] = useState("ALL");
  const [status, setStatus] = useState("all");
  const [debouncedOperatorName, setDebouncedOperatorName] = useState("");
  const [debouncedModuleName, setDebouncedModuleName] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedOperatorName(operatorName.trim());
      setDebouncedModuleName(moduleName.trim());
      setCurrentPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [operatorName, moduleName]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getOperationAuditLogList(
        {
          operatorName: debouncedOperatorName || undefined,
          moduleName: debouncedModuleName || undefined,
          httpMethod: httpMethod === "ALL" ? undefined : httpMethod,
          status: status === "all" ? undefined : Number(status),
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
  }, [currentPage, debouncedModuleName, debouncedOperatorName, httpMethod, status]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      fetchList(controller.signal);
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            操作审计
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            查看系统写操作记录，包含操作人、接口、结果、耗时和请求摘要
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => fetchList()}
          className="flex items-center gap-2"
        >
          <RefreshCcw className="w-4 h-4" /> 刷新
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={operatorName}
            onChange={(e) => setOperatorName(e.target.value)}
            placeholder="搜索操作人"
            className="w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <input
          value={moduleName}
          onChange={(e) => setModuleName(e.target.value)}
          placeholder="搜索模块"
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={httpMethod}
          onChange={(e) => {
            setHttpMethod(e.target.value);
            setCurrentPage(1);
          }}
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {methodOptions.map((item) => (
            <option key={item} value={item}>
              {item === "ALL" ? "全部方法" : item}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setCurrentPage(1);
          }}
          className="w-full px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {statusOptions.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
      </div>

      {loading && <TablePageSkeleton columns={6} rows={6} />}

      {!loading && items.length === 0 && (
        <div className="glass-card rounded-2xl p-12 text-center">
          <ShieldCheck className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 text-sm">暂无操作审计日志</p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
          <table className="w-full min-w-[1080px] text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-left text-slate-500">
                <th className="px-5 py-4 font-medium">时间</th>
                <th className="px-5 py-4 font-medium">操作人</th>
                <th className="px-5 py-4 font-medium">模块 / 操作</th>
                <th className="px-5 py-4 font-medium">接口</th>
                <th className="px-5 py-4 font-medium">结果</th>
                <th className="px-5 py-4 font-medium">请求摘要</th>
                <th className="px-5 py-4 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.auditLogId} className="border-b border-slate-50 align-top">
                  <td className="px-5 py-4 text-slate-600 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <Clock className="w-4 h-4 text-slate-400" />
                      {formatDate(item.createdTime)}
                    </div>
                    <div className="text-xs text-slate-400 mt-1">
                      IP：{item.clientIp || "-"}
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <div className="font-medium text-slate-800">
                      {item.operatorName || "-"}
                    </div>
                    <div className="text-xs text-slate-400 mt-1">
                      {item.operatorId || "-"}
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <div className="text-slate-900 font-medium">{item.moduleName || "-"}</div>
                    <div className="text-slate-500 mt-1">{item.operationName || "-"}</div>
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-2">
                      <Badge className={cn(methodStyles[item.httpMethod] ?? "bg-slate-100 text-slate-600")}>
                        {item.httpMethod}
                      </Badge>
                      <span className="text-slate-600 break-all">{item.requestPath}</span>
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <Badge
                      className={cn(
                        item.status === 1
                          ? "bg-emerald-100 text-emerald-700 hover:bg-emerald-100"
                          : "bg-rose-100 text-rose-700 hover:bg-rose-100"
                      )}
                    >
                      {item.status === 1 ? "成功" : "失败"}
                    </Badge>
                    <div className="text-xs text-slate-400 mt-2">
                      {item.costMs ?? 0} ms
                    </div>
                    {item.errorMessage && (
                      <div className="text-xs text-rose-600 mt-2 max-w-48 break-words">
                        {item.errorMessage}
                      </div>
                    )}
                  </td>
                  <td className="px-5 py-4">
                    <pre className="max-h-28 max-w-96 overflow-auto rounded-lg bg-slate-50 p-3 text-xs text-slate-600 whitespace-pre-wrap">
                      {formatSummary(item.requestSummary)}
                    </pre>
                  </td>
                  <td className="px-5 py-4">
                    <Button asChild variant="outline" size="sm">
                      <Link href={`/audit-logs/${item.auditLogId}`} className="flex items-center gap-2">
                        <Eye className="w-4 h-4" /> 查看详情
                      </Link>
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
          <Button
            variant="outline"
            size="sm"
            disabled={currentPage <= 1}
            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
          >
            <ChevronLeft className="w-4 h-4" />
          </Button>
          <span>
            {currentPage} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={currentPage >= totalPages}
            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
          >
            <ChevronRight className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
