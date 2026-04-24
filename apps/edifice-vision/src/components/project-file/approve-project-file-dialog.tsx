"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ResponseCode } from "@/types/api";
import { getUserList } from "@/services/project";
import { approveProjectFile } from "@/services/project-file";
import type { ProjectFileVo } from "@/types/project-file";
import type { UserListItem } from "@/types/project";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  file: ProjectFileVo | null;
  onSuccess: () => void;
}

export function ApproveProjectFileDialog({ open, onOpenChange, file, onSuccess }: Props) {
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [nextApproverId, setNextApproverId] = useState<string>("");
  const [comment, setComment] = useState<string>("");
  const [terminateHere, setTerminateHere] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setNextApproverId("");
    setComment("");
    setTerminateHere(false);
    (async () => {
      const res = await getUserList();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setUsers(res.data.records ?? []);
      }
    })();
  }, [open]);

  if (!file) return null;

  const chain = file.approvalChain ?? [];
  const currentRecord = chain.find((r) => r.approvalRecordId === file.currentRecordId);
  const currentLevel = currentRecord?.approvalLevel ?? 1;
  // 三级审批：第 3 级默认终审；<3 级默认流转下一级
  const defaultTerminate = currentLevel >= 3;

  const handleApprove = async (pass: boolean) => {
    if (!file.currentRecordId) {
      toast.error("当前没有可操作的审批节点");
      return;
    }
    const shouldTerminate = pass ? terminateHere || defaultTerminate : true;
    if (pass && !shouldTerminate && !nextApproverId) {
      toast.error("请选择下一级审批人，或勾选'终审通过'");
      return;
    }

    setSubmitting(true);
    try {
      const res = await approveProjectFile({
        recordId: file.currentRecordId,
        pass,
        nextApproverId: pass && !shouldTerminate ? nextApproverId : undefined,
        comment: comment || undefined,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(pass ? "审批通过" : "已驳回");
        onOpenChange(false);
        onSuccess();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>审批项目文件</DialogTitle>
          <DialogDescription>
            项目 <span className="font-medium text-slate-700">{file.projectName}</span> ·
            阶段 <span className="font-medium text-slate-700">{file.stageName ?? "-"}</span> ·
            当前层级 <span className="font-medium text-blue-600">L{currentLevel}</span>
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          {/* 审批链 */}
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
                    L{r.approvalLevel} · {r.approverName ?? "-"} ·
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

          {/* 终审 or 流转下一级 */}
          <div className="flex items-center gap-2">
            <input
              id="terminate"
              type="checkbox"
              checked={terminateHere || defaultTerminate}
              disabled={defaultTerminate}
              onChange={(e) => setTerminateHere(e.target.checked)}
            />
            <label htmlFor="terminate" className="text-sm text-slate-600">
              终审（通过后直接归档；不再流转下一级）
              {defaultTerminate && <span className="text-slate-400 ml-1">(L3 自动终审)</span>}
            </label>
          </div>

          {!(terminateHere || defaultTerminate) && (
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                下一级审批人
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={nextApproverId}
                onChange={(e) => setNextApproverId(e.target.value)}
              >
                <option value="">请选择</option>
                {users.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.realName || u.username}
                  </option>
                ))}
              </select>
              <p className="text-[11px] text-slate-400 mt-1">
                L1 建议选专业主管 · L2 建议选总工；当前 L{currentLevel}
              </p>
            </div>
          )}

          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">
              审批意见 / 驳回原因
            </label>
            <textarea
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              rows={2}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="可选"
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
          <Button
            variant="outline"
            onClick={() => handleApprove(false)}
            disabled={submitting}
          >
            <XCircle className="w-4 h-4 mr-1 text-rose-500" /> 驳回
          </Button>
          <Button
            className="bg-emerald-600 hover:bg-emerald-700 text-white"
            onClick={() => handleApprove(true)}
            disabled={submitting}
          >
            {submitting ? (
              <Loader2 className="w-4 h-4 animate-spin mr-1" />
            ) : (
              <CheckCircle2 className="w-4 h-4 mr-1" />
            )}
            通过
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
