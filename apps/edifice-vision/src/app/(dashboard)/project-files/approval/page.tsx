"use client";

import { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  Clock,
  ExternalLink,
  FileText,
  Inbox,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  getMyPendingProjectFiles,
  getProjectFileDetail,
  getProjectFileList,
} from "@/services/project-file";
import type { ProjectFileVo } from "@/types/project-file";
import { PROJECT_FILE_STATUS_MAP } from "@/types/project-file";
import { ApproveProjectFileDialog } from "@/components/project-file/approve-project-file-dialog";
import { ProjectFileDetailDialog } from "@/components/project-file/project-file-detail-dialog";
import { useDetailLink } from "@/hooks/use-detail-link";

type Tab = "pending" | "all" | "mine";

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.replace("T", " ").slice(0, 16);
}

function formatSize(bytes: string | null): string {
  if (!bytes) return "-";
  const n = Number(bytes);
  if (isNaN(n)) return "-";
  if (n < 1024) return `${n}B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`;
  return `${(n / 1024 / 1024).toFixed(1)}MB`;
}

const statusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-amber-100 text-amber-600",
  2: "bg-emerald-100 text-emerald-600",
  3: "bg-rose-100 text-rose-600",
};

export default function ProjectFilesApprovalPage() {
  const [tab, setTab] = useState<Tab>("pending");
  const [items, setItems] = useState<ProjectFileVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [approveOpen, setApproveOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [current, setCurrent] = useState<ProjectFileVo | null>(null);
  const [currentDetail, setCurrentDetail] = useState<ProjectFileVo | null>(null);
  const [keyword, setKeyword] = useState("");

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      if (tab === "pending") {
        const res = await getMyPendingProjectFiles();
        if (res.code === ResponseCode.SUCCESS) setItems(res.data ?? []);
      } else if (tab === "all") {
        const res = await getProjectFileList({ keyword: keyword || undefined });
        if (res.code === ResponseCode.SUCCESS) setItems(res.data ?? []);
      } else {
        // "mine" 暂用全部 + 前端过滤上传人；后端可后续加 my-uploads
        const res = await getProjectFileList({ keyword: keyword || undefined });
        if (res.code === ResponseCode.SUCCESS) setItems(res.data ?? []);
      }
    } finally {
      setLoading(false);
    }
  }, [tab, keyword]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleOpenApprove = async (id: string) => {
    const res = await getProjectFileDetail(id);
    if (res.code === ResponseCode.SUCCESS && res.data) {
      setCurrent(res.data);
      setApproveOpen(true);
    }
  };
  const handleOpenDetail = async (id: string) => {
    const res = await getProjectFileDetail(id);
    if (res.code === ResponseCode.SUCCESS && res.data) {
      setCurrentDetail(res.data);
      setDetailOpen(true);
    }
  };
  // 详情链接（外部 URL ?id=xxx）走只读详情弹窗
  useDetailLink(handleOpenDetail);

  const stats = {
    total: items.length,
    pending: items.filter((f) => f.approvalStatus === 1).length,
    approved: items.filter((f) => f.approvalStatus === 2).length,
    rejected: items.filter((f) => f.approvalStatus === 3).length,
  };

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            项目文件审批
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            三级审批：项目负责人 → 专业主管 → 总工。新增上传请到“项目详情 → 项目文件”。
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatBox
          icon={<Inbox className="w-5 h-5" />}
          label="当前共"
          value={stats.total}
          tone="slate"
        />
        <StatBox
          icon={<Clock className="w-5 h-5" />}
          label="审批中"
          value={stats.pending}
          tone="amber"
        />
        <StatBox
          icon={<CheckCircle2 className="w-5 h-5" />}
          label="已通过"
          value={stats.approved}
          tone="emerald"
        />
        <StatBox
          icon={<XCircle className="w-5 h-5" />}
          label="已驳回"
          value={stats.rejected}
          tone="rose"
        />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {(
            [
              { key: "pending", label: "我的待审" },
              { key: "all", label: "全部" },
              { key: "mine", label: "我上传的" },
            ] as const
          ).map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                tab === t.key
                  ? "bg-blue-600 text-white shadow-sm"
                  : "text-slate-500 hover:text-slate-700",
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
        {tab !== "pending" && (
          <input
            type="text"
            placeholder="按分类 / 说明搜索..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="ml-auto px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white w-full sm:w-72"
          />
        )}
      </div>

      {loading ? (
        <TablePageSkeleton columns={6} rows={6} />
      ) : items.length === 0 ? (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <FileText className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-1">暂无文件</h3>
          <p className="text-sm text-slate-500">当前筛选条件下没有数据</p>
        </div>
      ) : (
        <div className="glass-card rounded-2xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
              <tr>
                <th className="text-left py-3 px-4 font-semibold">文件</th>
                <th className="text-left py-3 px-4 font-semibold">项目 / 阶段</th>
                <th className="text-left py-3 px-4 font-semibold">分类</th>
                <th className="text-left py-3 px-4 font-semibold">上传人</th>
                <th className="text-center py-3 px-4 font-semibold">状态</th>
                <th className="text-left py-3 px-4 font-semibold">当前审批人</th>
                <th className="text-right py-3 px-4 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((f) => (
                <tr key={f.projectFileId} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-2">
                      <FileText className="w-4 h-4 text-blue-500 shrink-0" />
                      <div className="min-w-0">
                        <p className="text-slate-700 font-medium truncate">
                          {f.fileName ?? "-"}
                        </p>
                        <p className="text-xs text-slate-400">
                          {f.fileExtension ?? ""} · {formatSize(f.fileSize)}
                        </p>
                      </div>
                      {f.fileUrl && (
                        <a
                          href={f.fileUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-slate-400 hover:text-blue-500"
                        >
                          <ExternalLink className="w-4 h-4" />
                        </a>
                      )}
                    </div>
                  </td>
                  <td className="py-3 px-4 text-slate-600">
                    <p className="truncate">{f.projectName ?? "-"}</p>
                    <p className="text-xs text-slate-400">{f.stageName ?? "-"}</p>
                  </td>
                  <td className="py-3 px-4 text-slate-500">{f.fileCategory ?? "-"}</td>
                  <td className="py-3 px-4 text-slate-500">
                    {f.uploadUserName ?? "-"}
                    <p className="text-xs text-slate-400">{formatDate(f.createdTime)}</p>
                  </td>
                  <td className="py-3 px-4 text-center">
                    <Badge
                      variant="secondary"
                      className={cn("text-xs", statusStyles[f.approvalStatus] ?? "")}
                    >
                      {PROJECT_FILE_STATUS_MAP[f.approvalStatus] ?? "-"}
                    </Badge>
                  </td>
                  <td className="py-3 px-4 text-slate-600">
                    {f.currentApproverName ?? (f.approvalStatus === 2 || f.approvalStatus === 3 ? "—" : "-")}
                  </td>
                  <td className="py-3 px-4 text-right">
                    {f.approvalStatus === 1 && f.currentRecordId ? (
                      <Button
                        size="sm"
                        className="bg-blue-600 hover:bg-blue-700 text-white"
                        onClick={() => handleOpenApprove(f.projectFileId)}
                      >
                        审批
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleOpenDetail(f.projectFileId)}
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

      <ApproveProjectFileDialog
        open={approveOpen}
        onOpenChange={setApproveOpen}
        file={current}
        onSuccess={fetchData}
      />

      <ProjectFileDetailDialog
        open={detailOpen}
        onOpenChange={setDetailOpen}
        file={currentDetail}
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
