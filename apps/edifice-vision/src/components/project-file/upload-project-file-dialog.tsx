"use client";

import { useCallback, useEffect, useState } from "react";
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
import {
  getAllProjects,
  getProjectDetail,
  getUserList,
} from "@/services/project";
import { createProjectFilesBatch } from "@/services/project-file";
import type {
  ProjectListVo,
  ProjectDetailVo,
  ProjectStageVo,
  UserListItem,
  FilesVo,
} from "@/types/project";
import { FILE_CATEGORY_OPTIONS } from "@/types/project-file";
import { FileUploadField } from "@/components/file/file-upload-field";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
  /** 传入时锁定项目（项目详情内部上传场景） */
  lockedProjectId?: string;
  /** 锁定项目时用于展示的名称 */
  lockedProjectName?: string;
}

export function UploadProjectFileDialog({
  open,
  onOpenChange,
  onSuccess,
  lockedProjectId,
  lockedProjectName,
}: Props) {
  const locked = !!lockedProjectId;

  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [projectId, setProjectId] = useState<string>(lockedProjectId ?? "");
  const [detail, setDetail] = useState<ProjectDetailVo | null>(null);
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [stageId, setStageId] = useState<string>("");
  const [displayName, setDisplayName] = useState<string>("");
  const [category, setCategory] = useState<string>("");
  const [description, setDescription] = useState<string>("");
  const [firstApproverId, setFirstApproverId] = useState<string>("");
  const [files, setFiles] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>("");

  // 仅重置表单录入项；项目详情和阶段刷新交给下面的 effect 按 open 触发。
  const reset = useCallback(() => {
    setProjectId(lockedProjectId ?? "");
    setStageId("");
    setDisplayName("");
    setCategory("");
    setDescription("");
    setFirstApproverId("");
    setFiles([]);
    setError("");
  }, [lockedProjectId]);

  // 打开时：重置表单 + 非锁定模式下加载项目列表
  useEffect(() => {
    if (!open) return;
    reset();
    (async () => {
      const [projectRes, userRes] = await Promise.all([
        locked ? Promise.resolve(null) : getAllProjects({ pageSize: 200 }),
        getUserList(),
      ]);
      if (projectRes?.code === ResponseCode.SUCCESS && projectRes.data) {
        setProjects(projectRes.data.records ?? []);
      }
      if (userRes.code === ResponseCode.SUCCESS && userRes.data) {
        setUsers(userRes.data.records ?? []);
      }
    })();
  }, [open, reset, locked]);

  // 打开且 projectId 就绪时加载项目详情（锁定模式下，同一 projectId 每次开弹窗也会重新拉一次）
  useEffect(() => {
    if (!open || !projectId) {
      if (!open) {
        setDetail(null);
        setStages([]);
      }
      return;
    }
    let cancelled = false;
    (async () => {
      const res = await getProjectDetail(projectId);
      if (cancelled) return;
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setDetail(res.data);
        setStages(res.data.projectStages ?? []);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, projectId]);

  const handleSubmit = async () => {
    setError("");
    if (!projectId) return setError("请选择项目");
    if (!files.length || uploading) return setError("请先完成文件上传");

    setSubmitting(true);
    try {
      const res = await createProjectFilesBatch(files.map((file) => ({
        projectId,
        projectStageId: stageId || undefined,
        fileId: file.fileId,
        fileName: files.length === 1 && displayName.trim() ? displayName.trim() : file.displayName,
        fileCategory: category || undefined,
        description: description || undefined,
        firstApproverId: firstApproverId || undefined,
      })));
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
      setUploading(false);
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
          <DialogTitle>上传项目文件</DialogTitle>
          <DialogDescription>
            归档到项目并提交三级审批：项目负责人 → 专业主管 → 总工。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">项目</label>
              {locked ? (
                <div className="w-full px-3 py-2 rounded-lg bg-slate-50 border border-slate-200 text-sm text-slate-600">
                  {lockedProjectName ?? projectId}
                </div>
              ) : (
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
              )}
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                所属阶段（选填）
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                value={stageId}
                onChange={(e) => setStageId(e.target.value)}
                disabled={!projectId || stages.length === 0}
              >
                <option value="">不关联阶段</option>
                {stages.map((s) => (
                  <option key={s.projectStageId} value={s.projectStageId}>
                    {s.stageName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                文件名称（单文件选填）
              </label>
              <input
                type="text"
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="留空使用原文件名" disabled={files.length > 1}
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                文件分类（选填）
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option value="">未分类</option>
                {FILE_CATEGORY_OPTIONS.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-span-2">
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                一级审批人（缺省自动取项目经理）
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                value={firstApproverId}
                onChange={(e) => setFirstApproverId(e.target.value)}
                disabled={!detail}
              >
                <option value="">自动选取（项目经理）</option>
                {users.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.realName || u.username}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">文件说明</label>
            <textarea
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              rows={2}
              placeholder="可选：描述这份文件的内容"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">
              上传文件 <span className="text-rose-500">*</span>
            </label>
            <FileUploadField purpose="project" value={files} onChange={setFiles}
              onBusyChange={setUploading} disabled={submitting} />
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
            disabled={submitting || uploading}
          >
            {(submitting || uploading) && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
            {uploading ? "上传中..." : "提交审批"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
