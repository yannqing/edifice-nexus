"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  Check,
  Clock,
  Copy,
  Network,
  RefreshCcw,
  ShieldCheck,
  UserRound,
  XCircle,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import type { OperationAuditLogVo } from "@/types/audit-log";
import { getOperationAuditLogDetail } from "@/services/audit-log";

const methodStyles: Record<string, string> = {
  GET: "bg-emerald-100 text-emerald-700",
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
  if (!value || value === "{}") return "无请求参数";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function DetailItem({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="min-w-0 border-b border-slate-100 py-4 last:border-b-0">
      <div className="text-xs text-slate-400 mb-1.5">{label}</div>
      <div className={cn("text-sm text-slate-800 break-all", mono && "font-mono")}>
        {value ?? "-"}
      </div>
    </div>
  );
}

export default function AuditLogDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const auditLogId = params.id;
  const [item, setItem] = useState<OperationAuditLogVo | null>(null);
  const [loading, setLoading] = useState(true);

  const summary = useMemo(() => formatSummary(item?.requestSummary), [item?.requestSummary]);

  const fetchDetail = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getOperationAuditLogDetail(auditLogId, signal);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setItem(res.data);
      } else {
        setItem(null);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return;
      setItem(null);
      setLoading(false);
    }
  }, [auditLogId]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => fetchDetail(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [fetchDetail]);

  const copyText = async (text: string, label: string) => {
    await navigator.clipboard.writeText(text);
    toast.success(`${label}已复制`);
  };

  if (loading) {
    return <div className="p-4 md:p-8"><TablePageSkeleton columns={2} rows={6} /></div>;
  }

  if (!item) {
    return (
      <div className="p-4 md:p-8">
        <div className="border border-slate-200 bg-white rounded-lg p-12 text-center">
          <ShieldCheck className="w-10 h-10 text-slate-300 mx-auto mb-3" />
          <p className="text-slate-700 font-medium">审计记录不存在或已无法访问</p>
          <Button variant="outline" className="mt-5" onClick={() => router.push("/audit-logs")}>
            <ArrowLeft className="w-4 h-4 mr-2" /> 返回审计列表
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex items-start gap-3">
          <Button variant="outline" size="sm" onClick={() => router.push("/audit-logs")} title="返回审计列表">
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                {item.operationName}
              </h1>
              <Badge className={cn(item.status === 1 ? "bg-emerald-100 text-emerald-700" : "bg-rose-100 text-rose-700")}>
                {item.status === 1 ? "执行成功" : "执行失败"}
              </Badge>
            </div>
            <p className="text-slate-500 text-sm mt-1">审计记录 ID：{item.auditLogId}</p>
          </div>
        </div>
        <Button variant="outline" onClick={() => fetchDetail()} className="flex items-center gap-2">
          <RefreshCcw className="w-4 h-4" /> 刷新
        </Button>
      </div>

      <section className="border border-slate-200 bg-white rounded-lg">
        <div className="px-5 py-4 border-b border-slate-100">
          <h2 className="font-semibold text-slate-900">基本信息</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 px-5 gap-x-8">
          <DetailItem label="模块" value={item.moduleName} />
          <DetailItem label="操作名称" value={item.operationName} />
          <DetailItem
            label="执行结果"
            value={
              <span className={cn("inline-flex items-center gap-1.5", item.status === 1 ? "text-emerald-700" : "text-rose-700")}>
                {item.status === 1 ? <Check className="w-4 h-4" /> : <XCircle className="w-4 h-4" />}
                {item.status === 1 ? "成功" : "失败"}
              </span>
            }
          />
          <DetailItem label="执行耗时" value={`${item.costMs ?? 0} ms`} />
          <DetailItem label="操作时间" value={<span className="inline-flex items-center gap-2"><Clock className="w-4 h-4 text-slate-400" />{formatDate(item.createdTime)}</span>} />
          <DetailItem label="操作人" value={<span className="inline-flex items-center gap-2"><UserRound className="w-4 h-4 text-slate-400" />{item.operatorName || "-"}</span>} />
          <DetailItem label="操作人 ID" value={item.operatorId || "-"} mono />
          <DetailItem label="客户端 IP" value={<span className="inline-flex items-center gap-2"><Network className="w-4 h-4 text-slate-400" />{item.clientIp || "-"}</span>} mono />
        </div>
      </section>

      <section className="border border-slate-200 bg-white rounded-lg">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between gap-3">
          <h2 className="font-semibold text-slate-900">请求信息</h2>
          <Button variant="outline" size="sm" onClick={() => copyText(item.requestPath, "请求路径")}>
            <Copy className="w-4 h-4 mr-2" /> 复制路径
          </Button>
        </div>
        <div className="px-5">
          <DetailItem
            label="请求方法与路径"
            value={
              <div className="flex items-center gap-2">
                <Badge className={cn(methodStyles[item.httpMethod] ?? "bg-slate-100 text-slate-600")}>{item.httpMethod}</Badge>
                <span className="font-mono break-all">{item.requestPath}</span>
              </div>
            }
          />
        </div>
        <div className="border-t border-slate-100">
          <div className="px-5 py-3 flex items-center justify-between gap-3">
            <div className="text-xs text-slate-400">已按审计规则脱敏的请求摘要</div>
            <Button variant="outline" size="sm" onClick={() => copyText(summary, "请求摘要")}>
              <Copy className="w-4 h-4 mr-2" /> 复制摘要
            </Button>
          </div>
          <pre className="mx-5 mb-5 max-h-[520px] overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-6 text-slate-100 whitespace-pre-wrap break-all">
            {summary}
          </pre>
        </div>
      </section>

      <section className="border border-slate-200 bg-white rounded-lg">
        <div className="px-5 py-4 border-b border-slate-100">
          <h2 className="font-semibold text-slate-900">执行结果</h2>
        </div>
        <div className="px-5">
          <DetailItem label="状态" value={item.status === 1 ? "成功" : "失败"} />
          <DetailItem
            label="失败原因"
            value={item.errorMessage || "无"}
          />
        </div>
      </section>

      <div className="flex justify-end">
        <Button variant="outline" onClick={() => copyText(item.auditLogId, "审计记录 ID")}>
          <Copy className="w-4 h-4 mr-2" /> 复制审计记录 ID
        </Button>
      </div>
    </div>
  );
}
