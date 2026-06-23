"use client";

import { ExternalLink, FileText } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { ProjectFileVo } from "@/types/project-file";
import { PROJECT_FILE_STATUS_MAP } from "@/types/project-file";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  file: ProjectFileVo | null;
}

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

/**
 * 项目文件只读详情弹窗：仅展示文件信息和审批链，不含任何审批操作按钮。
 *
 * <p>用于「项目文件审批」列表里非待审状态（已通过/已驳回/待提交）的「详情」入口。
 * 审批中的文件仍走 {@link ApproveProjectFileDialog}（带审批按钮）。
 */
export function ProjectFileDetailDialog({ open, onOpenChange, file }: Props) {
  if (!file) return null;

  const chain = file.approvalChain ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>项目文件详情</DialogTitle>
          <DialogDescription>
            项目 <span className="font-medium text-slate-700">{file.projectName ?? "-"}</span>
            {file.projectCode ? <span className="text-slate-400"> · {file.projectCode}</span> : null}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          {/* 文件信息 */}
          <div className="p-3 bg-slate-50 rounded-lg space-y-2 text-sm">
            <div className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-blue-500 shrink-0" />
              <span className="text-slate-700 font-medium truncate">
                {file.fileName ?? "-"}
              </span>
              {file.fileUrl && (
                <a
                  href={file.fileUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-slate-400 hover:text-blue-500 ml-auto"
                  title="下载/预览"
                >
                  <ExternalLink className="w-4 h-4" />
                </a>
              )}
            </div>
            <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-500">
              <div>阶段：<span className="text-slate-700">{file.stageName ?? "-"}</span></div>
              <div>分类：<span className="text-slate-700">{file.fileCategory ?? "-"}</span></div>
              <div>
                大小：<span className="text-slate-700">
                  {file.fileExtension ?? "?"} · {formatSize(file.fileSize)}
                </span>
              </div>
              <div>
                状态：
                <Badge
                  variant="secondary"
                  className={cn("ml-1 text-[11px]", statusStyles[file.approvalStatus] ?? "")}
                >
                  {PROJECT_FILE_STATUS_MAP[file.approvalStatus] ?? "-"}
                </Badge>
              </div>
              <div>
                上传人：<span className="text-slate-700">{file.uploadUserName ?? "-"}</span>
              </div>
              <div>
                上传时间：<span className="text-slate-700">{formatDate(file.createdTime)}</span>
              </div>
              {file.approvalStatus === 1 && (
                <div className="col-span-2">
                  当前审批人：<span className="text-slate-700">{file.currentApproverName ?? "-"}</span>
                </div>
              )}
            </div>
            {file.description && (
              <div className="pt-2 border-t border-slate-200 text-xs text-slate-500">
                说明：{file.description}
              </div>
            )}
          </div>

          {/* 审批链 */}
          <div>
            <p className="text-xs font-medium text-slate-600 mb-2">审批记录</p>
            <div className="p-3 bg-slate-50 rounded-lg text-xs space-y-1">
              {chain.length === 0 ? (
                <p className="text-slate-400">暂无审批记录</p>
              ) : (
                chain.map((r, idx) => (
                  <div key={r.approvalRecordId} className="flex items-center gap-2">
                    <span
                      className={cn(
                        "inline-flex w-5 h-5 items-center justify-center rounded-full text-[10px] font-semibold",
                        r.inspectionFormStatus === 1
                          ? "bg-emerald-100 text-emerald-600"
                          : r.inspectionFormStatus === 2
                            ? "bg-rose-100 text-rose-600"
                            : "bg-amber-100 text-amber-600",
                      )}
                    >
                      {idx + 1}
                    </span>
                    <span className="text-slate-600">
                      L{r.approvalLevel ?? idx + 1} · {r.approverName ?? "-"} ·
                      {r.inspectionFormStatus === 0
                        ? " 待审核"
                        : r.inspectionFormStatus === 1
                          ? " 已通过"
                          : " 已驳回"}
                    </span>
                    {r.approvalDescription && (
                      <span className="text-slate-400 truncate">· {r.approvalDescription}</span>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
