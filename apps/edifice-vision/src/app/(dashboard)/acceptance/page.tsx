"use client";

import { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Plus,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  getAcceptanceDetail,
  getAcceptanceList,
  getMyPendingAcceptance,
} from "@/services/acceptance";
import type { AcceptanceVo } from "@/types/acceptance";
import {
  ACCEPTANCE_STATUS_MAP,
  ACCEPTANCE_TYPE_OPTIONS,
} from "@/types/acceptance";
import { CreateAcceptanceDialog } from "@/components/acceptance/create-acceptance-dialog";
import { ApproveAcceptanceDialog } from "@/components/acceptance/approve-acceptance-dialog";
import { useDetailLink } from "@/hooks/use-detail-link";

type TypeTab = "all" | 0 | 1;
type Panel = "list" | "my-pending";

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.replace("T", " ").slice(0, 16);
}

const statusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-amber-100 text-amber-600",
  2: "bg-emerald-100 text-emerald-600",
  3: "bg-rose-100 text-rose-600",
};

const typeStyles: Record<number, string> = {
  0: "bg-sky-100 text-sky-600",
  1: "bg-violet-100 text-violet-600",
  2: "bg-amber-100 text-amber-600",
};

export default function AcceptancePage() {
  const [panel, setPanel] = useState<Panel>("my-pending");
  const [typeTab, setTypeTab] = useState<TypeTab>("all");
  const [items, setItems] = useState<AcceptanceVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [current, setCurrent] = useState<AcceptanceVo | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      if (panel === "my-pending") {
        const res = await getMyPendingAcceptance();
        if (res.code === ResponseCode.SUCCESS) setItems(res.data ?? []);
      } else {
        const res = await getAcceptanceList({
          acceptanceType: typeTab === "all" ? undefined : typeTab,
          keyword: keyword || undefined,
        });
        if (res.code === ResponseCode.SUCCESS) setItems(res.data ?? []);
      }
    } finally {
      setLoading(false);
    }
  }, [panel, typeTab, keyword]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleOpen = async (id: string) => {
    const res = await getAcceptanceDetail(id);
    if (res.code === ResponseCode.SUCCESS && res.data) {
      setCurrent(res.data);
      setApproveOpen(true);
    }
  };
  useDetailLink(handleOpen);

  const stats = {
    total: items.length,
    inProgress: items.filter((i) => i.status === 1).length,
    approved: items.filter((i) => i.status === 2).length,
    rejected: items.filter((i) => i.status === 3).length,
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">验收管理</h1>
          <p className="text-slate-500 text-sm mt-1">
            过程验收 / 成果验收共用审批链。
          </p>
        </div>
        <Button
          className="bg-blue-600 hover:bg-blue-700 text-white"
          onClick={() => setCreateOpen(true)}
        >
          <Plus className="w-4 h-4 mr-1" /> 发起验收
        </Button>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatBox icon={<ClipboardCheck className="w-5 h-5" />} label="当前共" value={stats.total} tone="slate" />
        <StatBox icon={<Clock className="w-5 h-5" />} label="审批中" value={stats.inProgress} tone="amber" />
        <StatBox icon={<CheckCircle2 className="w-5 h-5" />} label="已通过" value={stats.approved} tone="emerald" />
        <StatBox icon={<XCircle className="w-5 h-5" />} label="已驳回" value={stats.rejected} tone="rose" />
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {(
            [
              { key: "my-pending", label: "我的待审" },
              { key: "list", label: "全部验收单" },
            ] as const
          ).map((t) => (
            <button
              key={t.key}
              onClick={() => setPanel(t.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                panel === t.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700",
              )}
            >
              {t.label}
            </button>
          ))}
        </div>

        {panel === "list" && (
          <>
            <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
              {(
                [
                  { key: "all" as const, label: "全部类型" },
                  ...ACCEPTANCE_TYPE_OPTIONS.map((o) => ({
                    key: o.value as 0 | 1,
                    label: o.label,
                  })),
                ]
              ).map((t) => (
                <button
                  key={String(t.key)}
                  onClick={() => setTypeTab(t.key)}
                  className={cn(
                    "px-3 py-1.5 rounded-lg text-xs font-medium transition-all",
                    typeTab === t.key
                      ? "bg-slate-100 text-slate-800"
                      : "text-slate-500 hover:text-slate-700",
                  )}
                >
                  {t.label}
                </button>
              ))}
            </div>
            <input
              type="text"
              placeholder="按标题 / 内容搜索..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className="ml-auto px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white w-full sm:w-72"
            />
          </>
        )}
      </div>

      {loading ? (
        <TablePageSkeleton columns={6} rows={6} />
      ) : items.length === 0 ? (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <ClipboardCheck className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-1">暂无验收单</h3>
          <p className="text-sm text-slate-500">
            {panel === "my-pending" ? "当前没有待你审批的验收单" : "当前筛选条件下没有数据"}
          </p>
        </div>
      ) : (
        <div className="glass-card rounded-2xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
              <tr>
                <th className="text-left py-3 px-4 font-semibold">标题</th>
                <th className="text-left py-3 px-4 font-semibold">项目 / 阶段</th>
                <th className="text-center py-3 px-4 font-semibold">类型</th>
                <th className="text-left py-3 px-4 font-semibold">申请人</th>
                <th className="text-center py-3 px-4 font-semibold">状态</th>
                <th className="text-left py-3 px-4 font-semibold">当前审批人</th>
                <th className="text-right py-3 px-4 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.acceptanceId} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="py-3 px-4">
                    <p className="text-slate-700 font-medium truncate max-w-sm">{a.title}</p>
                    {a.content && (
                      <p className="text-xs text-slate-400 truncate max-w-sm">{a.content}</p>
                    )}
                  </td>
                  <td className="py-3 px-4 text-slate-600">
                    <p className="truncate">{a.projectName ?? "-"}</p>
                    <p className="text-xs text-slate-400">{a.stageName ?? "整体"}</p>
                  </td>
                  <td className="py-3 px-4 text-center">
                    <span
                      className={cn(
                        "text-xs px-2 py-0.5 rounded-full font-medium",
                        typeStyles[a.acceptanceType] ?? "",
                      )}
                    >
                      {a.acceptanceTypeLabel}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-slate-500">
                    {a.applyUserName ?? "-"}
                    <p className="text-xs text-slate-400">{formatDate(a.createdTime)}</p>
                  </td>
                  <td className="py-3 px-4 text-center">
                    <Badge
                      variant="secondary"
                      className={cn("text-xs", statusStyles[a.status] ?? "")}
                    >
                      {ACCEPTANCE_STATUS_MAP[a.status] ?? "-"}
                    </Badge>
                  </td>
                  <td className="py-3 px-4 text-slate-600">
                    {a.currentApproverName ??
                      (a.status === 2 || a.status === 3 ? "—" : "-")}
                  </td>
                  <td className="py-3 px-4 text-right">
                    {a.status === 1 && a.currentRecordId ? (
                      <Button
                        size="sm"
                        className="bg-blue-600 hover:bg-blue-700 text-white"
                        onClick={() => handleOpen(a.acceptanceId)}
                      >
                        审批
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleOpen(a.acceptanceId)}
                      >
                        详情
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CreateAcceptanceDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        defaultType={typeof typeTab === "number" ? typeTab : undefined}
        onSuccess={fetchData}
      />
      <ApproveAcceptanceDialog
        open={approveOpen}
        onOpenChange={setApproveOpen}
        acceptance={current}
        onSuccess={fetchData}
      />
    </div>
  );
}

function StatBox({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  tone: "slate" | "amber" | "emerald" | "rose";
}) {
  const toneMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-600",
    amber: "bg-amber-100 text-amber-600",
    emerald: "bg-emerald-100 text-emerald-600",
    rose: "bg-rose-100 text-rose-600",
  };
  return (
    <div className="glass-card p-4 rounded-xl">
      <div className="flex items-center gap-3">
        <div className={cn("p-2 rounded-lg", toneMap[tone])}>{icon}</div>
        <div>
          <p className="text-xs text-slate-500">{label}</p>
          <p className="text-xl font-bold text-slate-800">{value}</p>
        </div>
      </div>
    </div>
  );
}
