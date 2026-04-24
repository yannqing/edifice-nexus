"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { CheckCircle2, Loader2, Send, XCircle } from "lucide-react";
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
import { approveBid, submitBidApproval } from "@/services/bid";
import type { BidVo } from "@/types/bid";
import type { UserListItem } from "@/types/project";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  bid: BidVo | null;
  onSuccess: () => void;
}

/**
 * 统一一个弹窗处理两种场景：
 * - approvalStatus=0 草稿 / =3 驳回：显示"提交审批"（选一级审批人）
 * - approvalStatus=1 审核中：显示审批链 + 通过 / 驳回 + 可选下一级
 * - approvalStatus=2 已通过：只读
 */
export function BidApprovalDialog({ open, onOpenChange, bid, onSuccess }: Props) {
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [firstApproverId, setFirstApproverId] = useState("");
  const [nextApproverId, setNextApproverId] = useState("");
  const [comment, setComment] = useState("");
  const [terminateHere, setTerminateHere] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFirstApproverId("");
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

  if (!bid) return null;

  const chain = bid.approvalChain ?? [];
  const current = chain.find((r) => r.approvalRecordId === bid.currentRecordId);
  const currentLevel = current?.approvalLevel ?? 1;
  const canSubmit = bid.approvalStatus === 0 || bid.approvalStatus === 3;
  const inProgress = bid.approvalStatus === 1;

  const handleSubmit = async () => {
    if (!firstApproverId) {
      toast.error("请选择一级审批人");
      return;
    }
    setSubmitting(true);
    try {
      const res = await submitBidApproval(bid.bidId, firstApproverId);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已提交审批");
        onOpenChange(false);
        onSuccess();
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleApprove = async (pass: boolean) => {
    if (!bid.currentRecordId) {
      toast.error("当前没有可操作的审批节点");
      return;
    }
    if (pass && !terminateHere && !nextApproverId) {
      toast.error("请选择下一级审批人，或勾选'终审通过'");
      return;
    }
    setSubmitting(true);
    try {
      const res = await approveBid({
        recordId: bid.currentRecordId,
        pass,
        nextApproverId: pass && !terminateHere ? nextApproverId : undefined,
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
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>投标审批 · {bid.bidName}</DialogTitle>
          <DialogDescription>
            {bid.bidCode && <>编号 {bid.bidCode} · </>}
            负责人 {bid.ownerUserName ?? "-"} · {bid.approvalStatusLabel}
            {inProgress && (
              <>
                {" "}
                · 当前 <span className="font-medium text-blue-600">L{currentLevel}</span>
              </>
            )}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          {/* 基本信息摘要 */}
          {bid.description && (
            <div className="p-3 bg-slate-50 rounded-lg text-sm text-slate-600 whitespace-pre-wrap">
              {bid.description}
            </div>
          )}

          {/* 附件速览 */}
          {bid.files && bid.files.length > 0 && (
            <div className="text-xs text-slate-500 space-y-1">
              <p className="font-medium">附件</p>
              {bid.files.map((f) => (
                <div key={f.bidFileId} className="flex items-center gap-2">
                  <span className="px-1.5 py-0.5 rounded bg-slate-100">{f.fileCategory}</span>
                  {f.fileUrl ? (
                    <a
                      href={f.fileUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-blue-600 hover:underline truncate"
                    >
                      {f.fileName}
                    </a>
                  ) : (
                    <span>{f.fileName}</span>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* 审批链（只在审核中或完成时展示） */}
          {chain.length > 0 && (
            <div className="p-3 bg-slate-50 rounded-lg text-xs space-y-1">
              {chain.map((r, idx) => (
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
              ))}
            </div>
          )}

          {/* 草稿 / 驳回：显示"提交审批" */}
          {canSubmit && (
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                一级审批人
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={firstApproverId}
                onChange={(e) => setFirstApproverId(e.target.value)}
              >
                <option value="">请选择</option>
                {users.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.realName || u.username}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* 审核中：展示审批操作区 */}
          {inProgress && (
            <>
              <div className="flex items-center gap-2">
                <input
                  id="term-bid"
                  type="checkbox"
                  checked={terminateHere}
                  onChange={(e) => setTerminateHere(e.target.checked)}
                />
                <label htmlFor="term-bid" className="text-sm text-slate-600">
                  终审（通过后直接归档；不再流转下一级）
                </label>
              </div>
              {!terminateHere && (
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
            </>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            关闭
          </Button>
          {canSubmit && (
            <Button
              className="bg-blue-600 hover:bg-blue-700 text-white"
              onClick={handleSubmit}
              disabled={submitting}
            >
              {submitting ? (
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
              ) : (
                <Send className="w-4 h-4 mr-1" />
              )}
              提交审批
            </Button>
          )}
          {inProgress && (
            <>
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
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
