"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ResponseCode } from "@/types/api";
import { getAllProjects, getProjectDetail } from "@/services/project";
import { createAcceptance } from "@/services/acceptance";
import type {
  ProjectDetailVo,
  ProjectListVo,
  ProjectMemberVo,
  ProjectStageVo,
} from "@/types/project";
import { ACCEPTANCE_TYPE_OPTIONS } from "@/types/acceptance";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  defaultType?: number;
  onSuccess: () => void;
}

export function CreateAcceptanceDialog({
  open,
  onOpenChange,
  defaultType,
  onSuccess,
}: Props) {
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [projectId, setProjectId] = useState<string>("");
  const [detail, setDetail] = useState<ProjectDetailVo | null>(null);
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [members, setMembers] = useState<ProjectMemberVo[]>([]);
  const [acceptanceType, setAcceptanceType] = useState<number>(defaultType ?? 2);
  const [stageId, setStageId] = useState<string>("");
  const [title, setTitle] = useState<string>("");
  const [content, setContent] = useState<string>("");
  const [firstApproverId, setFirstApproverId] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>("");

  const reset = useCallback(() => {
    setProjectId("");
    setDetail(null);
    setStages([]);
    setMembers([]);
    setAcceptanceType(defaultType ?? 2);
    setStageId("");
    setTitle("");
    setContent("");
    setFirstApproverId("");
    setError("");
  }, [defaultType]);

  useEffect(() => {
    if (!open) return;
    reset();
    (async () => {
      const res = await getAllProjects({ pageSize: 200 });
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setProjects(res.data.records ?? []);
      }
    })();
  }, [open, reset]);

  useEffect(() => {
    if (!projectId) {
      setDetail(null);
      setStages([]);
      setMembers([]);
      return;
    }
    let cancelled = false;
    (async () => {
      const res = await getProjectDetail(projectId);
      if (cancelled) return;
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
        setStages(res.data.projectStages ?? []);
        setMembers(res.data.projectMemberList ?? []);
        setStageId("");
        setFirstApproverId("");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  const memberOptions = useMemo(
    () =>
      members.map((m) => ({
        userId: m.userId,
        realName: m.realName,
      })),
    [members],
  );

  const isStageType = acceptanceType === 2;

  const handleSubmit = async () => {
    setError("");
    if (!projectId) return setError("请选择项目");
    if (!title.trim()) return setError("请填写标题");
    if (isStageType && !stageId) return setError("阶段性验收必须选择阶段");

    setSubmitting(true);
    try {
      const res = await createAcceptance({
        projectId,
        projectStageId: stageId || undefined,
        acceptanceType,
        title: title.trim(),
        content: content || undefined,
        firstApproverId: firstApproverId || undefined,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已提交审批");
        onOpenChange(false);
        onSuccess();
      } else {
        setError(res.msg || "提交失败");
      }
    } catch {
      setError("网络异常，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) reset();
        onOpenChange(v);
      }}
    >
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>新建验收单</DialogTitle>
          <DialogDescription>
            成果 / 过程 / 阶段性验收走统一审批流，一级默认项目负责人。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">类型</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={acceptanceType}
                onChange={(e) => setAcceptanceType(Number(e.target.value))}
              >
                {ACCEPTANCE_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">项目</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
              >
                <option value="">请选择项目</option>
                {projects.map((p) => (
                  <option key={p.projectId} value={p.projectId}>
                    {p.projectName} ({p.projectCode})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                项目阶段{isStageType ? " *" : "（可选）"}
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                value={stageId}
                onChange={(e) => setStageId(e.target.value)}
                disabled={!projectId || stages.length === 0}
              >
                <option value="">{isStageType ? "请选择阶段" : "整体验收"}</option>
                {stages.map((s) => (
                  <option key={s.projectStageId} value={s.projectStageId}>
                    {s.stageName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                一级审批人
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                value={firstApproverId}
                onChange={(e) => setFirstApproverId(e.target.value)}
                disabled={!detail}
              >
                <option value="">自动选取（项目经理）</option>
                {memberOptions.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.realName}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">标题</label>
            <input
              type="text"
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="一句话描述本次验收的主题"
            />
          </div>

          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">
              验收说明 / 内容
            </label>
            <textarea
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              rows={4}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="可选：介绍验收范围、关键结论、附件清单等"
            />
          </div>

          {error && (
            <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-600 text-sm">
              {error}
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            取消
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin mr-1" />} 提交审批
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
