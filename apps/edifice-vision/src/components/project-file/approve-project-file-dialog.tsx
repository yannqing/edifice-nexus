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
import { getUserCandidates } from "@/services/user";
import { getEnabledFlowConfig } from "@/services/config-center";
import { approveProjectFile } from "@/services/project-file";
import type { ProjectFileVo } from "@/types/project-file";
import type { UserListItem } from "@/types/project";
import type { ApprovalFlowConfigVo, ApprovalFlowNodeVo } from "@/types/config-center";

/** 在流程配置里按 nodeOrder 找节点 */
function nodeAt(config: ApprovalFlowConfigVo | null, order: number): ApprovalFlowNodeVo | null {
  return config?.nodes.find((node) => node.nodeOrder === order) ?? null;
}

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
  // 项目文件的审批流配置（bizType = file）。null 表示未配置流程，回退到旧逻辑
  const [flowConfig, setFlowConfig] = useState<ApprovalFlowConfigVo | null>(null);

  useEffect(() => {
    if (!open) return;
    setNextApproverId("");
    setComment("");
    setTerminateHere(false);
    setFlowConfig(null);
    (async () => {
      const [usersRes, flowRes] = await Promise.all([
        getUserCandidates({ pageSize: 500 }),
        getEnabledFlowConfig("file"),
      ]);
      if (usersRes.code === ResponseCode.SUCCESS && usersRes.data) {
        setUsers((usersRes.data.records ?? []) as unknown as UserListItem[]);
      }
      if (flowRes.code === ResponseCode.SUCCESS && flowRes.data) {
        setFlowConfig(flowRes.data);
      }
    })();
  }, [open]);

  if (!file) return null;

  const chain = file.approvalChain ?? [];
  const currentRecord = chain.find((r) => r.approvalRecordId === file.currentRecordId);
  const currentLevel = currentRecord?.approvalLevel ?? 1;

  // 按流程配置驱动：当前节点、下一级节点
  const currentNode = nodeAt(flowConfig, currentLevel);
  const nextNode = nodeAt(flowConfig, currentLevel + 1);
  const flowHasNoNextNode = Boolean(flowConfig && !nextNode);
  // 当前节点是否允许终审：未配置流程时按旧逻辑（>=3 终审）；配置了流程则看 allowTerminate 或没有下一级
  const flowCanTerminateHere = !flowConfig
    || flowHasNoNextNode
    || currentNode?.allowTerminate === 1;
  // 未配置流程时回退到写死的「L3 终审」逻辑，保持兼容
  const legacyDefaultTerminate = !flowConfig && currentLevel >= 3;
  const terminateChecked = terminateHere || legacyDefaultTerminate || flowHasNoNextNode;
  // 关键修复：当前节点不允许终审时，复选框禁用，且强制不勾选
  const terminateDisabled = legacyDefaultTerminate || flowHasNoNextNode || !flowCanTerminateHere;
  // 下一级是否需要选人：未配置流程默认需要；配置了流程看下一级节点类型
  const nextApproverRequiresSelection = !flowConfig || nextNode?.approverSourceType === "starter_select";

  const handleApprove = async (pass: boolean) => {
    if (!file.currentRecordId) {
      toast.error("当前没有可操作的审批节点");
      return;
    }
    // 实际是否终审：通过才看勾选；驳回一律视为终审（链终止）
    const shouldTerminate = pass ? terminateChecked : true;
    if (pass && !shouldTerminate && nextApproverRequiresSelection && !nextApproverId) {
      toast.error("请选择下一级审批人，或勾选'终审通过'");
      return;
    }

    setSubmitting(true);
    try {
      const res = await approveProjectFile({
        recordId: file.currentRecordId,
        pass,
        nextApproverId: pass && !shouldTerminate && nextApproverRequiresSelection ? nextApproverId : undefined,
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
            {flowConfig && currentNode && (
              <span className="text-slate-400"> · {currentNode.nodeName}</span>
            )}
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
              checked={terminateChecked}
              disabled={terminateDisabled}
              onChange={(e) => setTerminateHere(e.target.checked)}
            />
            <label htmlFor="terminate" className="text-sm text-slate-600">
              终审（通过后直接归档；不再流转下一级）
              {flowHasNoNextNode && <span className="text-slate-400 ml-1">(已是最后一级)</span>}
              {legacyDefaultTerminate && <span className="text-slate-400 ml-1">(L3 自动终审)</span>}
              {!flowCanTerminateHere && !terminateDisabled && (
                <span className="text-slate-400 ml-1">(当前节点不允许终审)</span>
              )}
            </label>
          </div>

          {!terminateChecked && nextApproverRequiresSelection && (
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
                {nextNode
                  ? `下一级：${nextNode.nodeName}（L${currentLevel + 1}）`
                  : `L${currentLevel} 建议选专业主管 · L${currentLevel + 1} 建议选总工；当前 L${currentLevel}`}
              </p>
            </div>
          )}

          {!terminateChecked && !nextApproverRequiresSelection && nextNode && (
            <div className="rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-700">
              下一级节点「{nextNode.nodeName}」由系统按流程配置自动确定审批人，审批通过后将自动流转。
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
