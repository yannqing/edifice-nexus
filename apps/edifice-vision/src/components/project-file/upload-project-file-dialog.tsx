"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Eye, FileText, Loader2, Upload, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ResponseCode } from "@/types/api";
import { getAllProjects, getProjectDetail, getUserList, uploadProjectAttachment } from "@/services/project";
import { createProjectFile } from "@/services/project-file";
import type {
  ProjectListVo,
  ProjectDetailVo,
  ProjectStageVo,
  UserListItem,
  FilesVo,
} from "@/types/project";
import { FILE_CATEGORY_OPTIONS } from "@/types/project-file";
import {
  isFileNameBrowserPreviewable,
  showUnsupportedPreviewToast,
} from "@/components/file/attachment-file-list";

const PROJECT_FILE_ACCEPT_TYPES = [
  ".pdf",
  ".doc",
  ".docx",
  ".xls",
  ".xlsx",
  ".ppt",
  ".pptx",
  ".txt",
  ".md",
  ".csv",
  ".rtf",
  ".odt",
  ".ods",
  ".odp",
  ".jpg",
  ".jpeg",
  ".png",
  ".gif",
  ".bmp",
  ".webp",
  ".svg",
  ".ico",
  ".tiff",
  ".tif",
  ".mp3",
  ".wav",
  ".flac",
  ".aac",
  ".ogg",
  ".wma",
  ".m4a",
  ".opus",
].join(",");

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
  const [file, setFile] = useState<File | null>(null);
  const [uploadedFile, setUploadedFile] = useState<FilesVo | null>(null);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 仅重置表单录入项；项目详情和阶段刷新交给下面的 effect 按 open 触发。
  const reset = useCallback(() => {
    setProjectId(lockedProjectId ?? "");
    setStageId("");
    setDisplayName("");
    setCategory("");
    setDescription("");
    setFirstApproverId("");
    setFile(null);
    setUploadedFile(null);
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

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (selected) {
      setFile(selected);
      setUploadedFile(null);
      // 默认用原文件名填到展示名，用户可自行改
      if (!displayName) {
        const name = selected.name.replace(/\.[^./\\]+$/, "");
        setDisplayName(name);
      }
    }
    e.target.value = "";
  };

  const handleLocalPreview = () => {
    if (!file) return;
    const browserCanPreview =
      isFileNameBrowserPreviewable(file.name) ||
      file.type.startsWith("image/") ||
      file.type.startsWith("text/") ||
      file.type === "application/pdf";
    if (!browserCanPreview) {
      showUnsupportedPreviewToast();
      return;
    }

    const url = URL.createObjectURL(file);
    const previewWindow = window.open(url, "_blank");
    if (!previewWindow) {
      URL.revokeObjectURL(url);
      toast.error("浏览器拦截了预览窗口，请允许弹窗后重试");
      return;
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  const handleSubmit = async () => {
    setError("");
    if (!projectId) return setError("请选择项目");
    if (!file) return setError("请选择要上传的文件");
    if (!displayName.trim()) return setError("请填写文件名称");

    setSubmitting(true);
    try {
      // 1. 上传文件拿 fileId
      let fid = uploadedFile?.fileId;
      if (!fid) {
        setUploading(true);
        const upRes = await uploadProjectAttachment(file);
        setUploading(false);
        if (upRes.code !== ResponseCode.SUCCESS || !upRes.data) {
          return setError(upRes.msg || "文件上传失败");
        }
        setUploadedFile(upRes.data);
        fid = upRes.data.fileId;
      }

      // 2. 创建项目文件并提交审批
      const res = await createProjectFile({
        projectId,
        projectStageId: stageId || undefined,
        fileId: fid,
        fileName: displayName.trim(),
        fileCategory: category || undefined,
        description: description || undefined,
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
                文件名称 *
              </label>
              <input
                type="text"
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="如：施工图-A区-V2"
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
            {!file ? (
              <label className="flex flex-col items-center justify-center gap-2 p-6 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors">
                <Upload className="w-6 h-6 text-slate-300" />
                <p className="text-sm text-slate-600 font-medium">
                  点击选择文件（文档、图片、音频等）
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  className="hidden"
                  accept={PROJECT_FILE_ACCEPT_TYPES}
                  onChange={handleFileSelect}
                />
              </label>
            ) : (
              <div className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl">
                <FileText className="w-6 h-6 text-emerald-500 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-700 truncate">{file.name}</p>
                  <p className="text-xs text-slate-400">
                    {(file.size / 1024).toFixed(1)} KB
                    {uploadedFile ? " · 已上传" : ""}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleLocalPreview}
                  className="p-1.5 text-slate-400 hover:text-blue-600"
                  title="预览文件"
                >
                  <Eye className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setFile(null);
                    setUploadedFile(null);
                  }}
                  className="p-1.5 text-slate-400 hover:text-rose-500"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}
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
