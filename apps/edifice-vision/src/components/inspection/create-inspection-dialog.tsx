"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, RefreshCw, Upload, UserRoundSearch } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { applyInspection } from "@/services/inspection";
import {
  getMyProjects,
  getProjectDetail,
  MAX_ATTACHMENT_FILE_SIZE,
  uploadProjectAttachment,
} from "@/services/project";
import { ResponseCode } from "@/types/api";
import type { FilesVo, ProjectListVo, ProjectStageVo } from "@/types/project";
import type { InspectionFormDetailVo } from "@/types/inspection";
import {
  EditableAttachmentFileList,
  parseFileIdList,
} from "@/components/file/attachment-file-list";
import { UserPickerDialog } from "@/components/user/user-picker-dialog";

type InitialInspectionProject = Pick<ProjectListVo, "projectId" | "projectName" | "projectCode">;

interface CreateInspectionDialogProps {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  onSuccess: () => void;
  initialProject?: InitialInspectionProject | null;
  initialInspection?: InspectionFormDetailVo | null;
}

function inheritedFile(fileId: string, index: number): FilesVo {
  return {
    fileId,
    fileType: "",
    displayName: `原验收材料${index + 1}`,
    fileExtension: "",
    fileUrl: "",
    fileSize: "0",
    status: 1,
  };
}

function availableStages(stages: ProjectStageVo[], rejectedStageId = "") {
  return stages.filter(
    (stage) =>
      stage.stageStatus === 1 ||
      (rejectedStageId &&
        String(stage.projectStageId) === rejectedStageId &&
        stage.stageStatus === 4)
  );
}

function normalizedPercentage(value?: number) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) return 0;
  return Math.min(100, Math.max(0, numericValue));
}

function formatPercentage(value: number) {
  return String(Math.round(value * 100) / 100);
}

export function CreateInspectionDialog({
  open,
  onOpenChange,
  onSuccess,
  initialProject,
  initialInspection,
}: CreateInspectionDialogProps) {
  const sourceProject = useMemo(
    () =>
      initialInspection
        ? {
            projectId: initialInspection.projectId,
            projectName: initialInspection.projectName,
            projectCode: initialInspection.projectCode,
          }
        : initialProject,
    [initialInspection, initialProject]
  );
  const initialProjectId = sourceProject?.projectId ? String(sourceProject.projectId) : "";
  const initialStageId = initialInspection?.projectStageId
    ? String(initialInspection.projectStageId)
    : "";
  const initialApproverId = initialInspection?.approvalRecords?.[0]?.approver
    ? String(initialInspection.approvalRecords[0].approver)
    : "";
  const initialApproverName = initialInspection?.approvalRecords?.[0]?.approverName ?? "";
  const projectLocked = Boolean(sourceProject && initialProjectId);
  const projectLabel = sourceProject
    ? [sourceProject.projectName, sourceProject.projectCode].filter(Boolean).join(" · ")
    : "";
  const [projects, setProjects] = useState<InitialInspectionProject[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [selectedStageId, setSelectedStageId] = useState("");
  const [firstApproverId, setFirstApproverId] = useState("");
  const [firstApproverName, setFirstApproverName] = useState("");
  const [approverPickerOpen, setApproverPickerOpen] = useState(false);
  const [description, setDescription] = useState("");
  const [files, setFiles] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [completionRatio, setCompletionRatio] = useState("100");
  const [dataReady, setDataReady] = useState(false);
  const [stageLoading, setStageLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [reloadVersion, setReloadVersion] = useState(0);
  const selectedStage = useMemo(
    () => stages.find((stage) => String(stage.projectStageId) === selectedStageId),
    [stages, selectedStageId]
  );
  const approvedInspectionRatio = normalizedPercentage(selectedStage?.completionRatio);
  const pendingInspectionRatio = normalizedPercentage(selectedStage?.pendingInspectionRatio);
  const remainingInspectionRatio = selectedStage
    ? Math.max(0, Math.round((100 - approvedInspectionRatio - pendingInspectionRatio) * 100) / 100)
    : 0;

  useEffect(() => {
    let cancelled = false;

    if (!open) {
      setSelectedProjectId("");
      setSelectedStageId("");
      setDescription("");
      setFiles([]);
      setUploadProgress(0);
      setStages([]);
      setFirstApproverId("");
      setFirstApproverName("");
      setApproverPickerOpen(false);
      setProjects([]);
      setCompletionRatio("100");
      setDataReady(false);
      setStageLoading(false);
      setLoadError("");
      return;
    }

    setDataReady(false);
    setStageLoading(false);
    setLoadError("");
    setSelectedProjectId(initialProjectId);
    setSelectedStageId(initialStageId);
    setFirstApproverId(initialApproverId);
    setFirstApproverName(initialApproverName);
    setDescription(initialInspection?.inspectionFormDescription ?? "");
    setCompletionRatio(String(initialInspection?.completionRatio ?? 100));
    setFiles(
      parseFileIdList(initialInspection?.fileIds).map((fileId, index) =>
        inheritedFile(fileId, index)
      )
    );
    setStages([]);
    setProjects(sourceProject ? [sourceProject] : []);

    async function loadInitialData() {
      try {
        if (sourceProject && initialProjectId) {
          const detailRes = await getProjectDetail(initialProjectId);
          if (cancelled) return;
          if (detailRes.code !== ResponseCode.SUCCESS || !detailRes.data) {
            throw new Error("Failed to load inspection form data");
          }

          setStages(
            availableStages(detailRes.data.projectStages ?? [], initialInspection ? initialStageId : "")
          );
          setDataReady(true);
          return;
        }

        const projectRes = await getMyProjects({ projectStatus: 1, pageSize: 100 });
        if (cancelled) return;
        if (projectRes.code !== ResponseCode.SUCCESS || !projectRes.data) {
          throw new Error("Failed to load inspection form data");
        }

        setProjects(projectRes.data.records ?? []);
        setDataReady(true);
      } catch {
        if (cancelled) return;
        setLoadError("验工申请数据加载失败，请稍后重试");
        setStages([]);
      }
    }

    loadInitialData();

    return () => {
      cancelled = true;
    };
  }, [
    open,
    initialProjectId,
    initialStageId,
    initialApproverId,
    initialApproverName,
    initialInspection,
    sourceProject,
    reloadVersion,
  ]);

  useEffect(() => {
    if (!open || !dataReady || projectLocked || loadError) return;

    if (!selectedProjectId) {
      setStages([]);
      setSelectedStageId("");
      setFirstApproverId("");
      setFirstApproverName("");
      setStageLoading(false);
      return;
    }

    let cancelled = false;

    async function loadStages() {
      setStageLoading(true);
      setStages([]);
      setSelectedStageId("");
      setFirstApproverId("");
      setFirstApproverName("");
      try {
        const res = await getProjectDetail(selectedProjectId);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setStages(availableStages(res.data.projectStages ?? []));
        }
      } catch {
        if (cancelled) return;
        setStages([]);
        setFirstApproverId("");
        setFirstApproverName("");
      } finally {
        if (!cancelled) setStageLoading(false);
      }
    }

    loadStages();

    return () => {
      cancelled = true;
    };
  }, [open, selectedProjectId, dataReady, projectLocked, loadError]);

  useEffect(() => {
    if (!selectedStage) return;
    setCompletionRatio((currentValue) => {
      const numericValue = Number(currentValue);
      if (
        !Number.isFinite(numericValue) ||
        numericValue <= 0 ||
        numericValue > remainingInspectionRatio
      ) {
        return formatPercentage(remainingInspectionRatio);
      }
      return currentValue;
    });
  }, [selectedStage, remainingInspectionRatio]);

  const handleFileUpload = async (file: File) => {
    if (file.size > MAX_ATTACHMENT_FILE_SIZE) {
      toast.error(`文件大小不能超过 500MB，当前文件 ${(file.size / 1024 / 1024).toFixed(1)}MB`);
      return;
    }

    setUploading(true);
    setUploadProgress(0);
    try {
      const res = await uploadProjectAttachment(file, setUploadProgress);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setUploadProgress(100);
        setFiles((prev) => [...prev, res.data]);
      } else {
        toast.error(res.msg || "文件上传失败");
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "文件上传失败，请稍后重试");
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async () => {
    if (!selectedProjectId) {
      toast.error("请选择项目");
      return;
    }
    if (!selectedStageId) {
      toast.error("请选择验工阶段");
      return;
    }
    if (!selectedStage) {
      toast.error("验工阶段数据尚未加载完成");
      return;
    }
    const applyRatio = Number(completionRatio);
    if (!Number.isFinite(applyRatio) || applyRatio <= 0) {
      toast.error("本次完成比例必须大于0");
      return;
    }
    if (applyRatio > remainingInspectionRatio) {
      toast.error(`本次完成比例不能超过${formatPercentage(remainingInspectionRatio)}%`);
      return;
    }
    if (!firstApproverId) {
      toast.error("请选择一级审批人");
      return;
    }
    if (!description.trim()) {
      toast.error("请填写验工说明");
      return;
    }
    if (files.length === 0) {
      toast.error("请上传验收材料");
      return;
    }

    setSubmitting(true);
    try {
      const fileIds = files.length > 0 ? JSON.stringify(files.map((file) => file.fileId)) : undefined;

      const res = await applyInspection({
        projectId: selectedProjectId,
        projectStageId: selectedStageId,
        inspectionFormDescription: description,
        fileIds,
        firstApproverId,
        completionRatio: applyRatio,
        sourceInspectionFormId: initialInspection?.inspectionFormId,
      });

      if (res.code === ResponseCode.SUCCESS) {
        toast.success(initialInspection ? "验工单已重新提交" : "验工单提交成功");
        onOpenChange(false);
        onSuccess();
      }
    } catch {
      /* request.ts handles user-facing errors */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-2xl max-h-[85vh] min-h-[420px] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{initialInspection ? "重新提交验工申请" : "发起验工申请"}</DialogTitle>
          <DialogDescription>
            {initialInspection
              ? "已载入原验工单内容，请根据驳回意见调整后重新提交"
              : projectLocked
                ? projectLabel
                : "选择项目和阶段，提交验工材料"}
          </DialogDescription>
        </DialogHeader>

        {!dataReady && !loadError && (
          <div className="flex min-h-[300px] flex-col items-center justify-center gap-3 text-slate-500">
            <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
            <p className="text-sm">正在加载验工数据...</p>
          </div>
        )}

        {loadError && (
          <div className="flex min-h-[300px] flex-col items-center justify-center gap-4 text-center">
            <p className="text-sm text-slate-500">{loadError}</p>
            <Button variant="outline" onClick={() => setReloadVersion((value) => value + 1)}>
              <RefreshCw className="mr-1.5 h-4 w-4" />
              重新加载
            </Button>
          </div>
        )}

        <div className={dataReady && !loadError ? "mt-4 space-y-5" : "hidden"}>
          {!projectLocked && (
            <div>
              <label className="text-sm font-medium text-slate-700 mb-1.5 block">
                选择项目 <span className="text-rose-500">*</span>
              </label>
              <select
                value={selectedProjectId}
                onChange={(event) => setSelectedProjectId(event.target.value)}
                className="form-input"
              >
                <option value="">请选择进行中的项目</option>
                {projects.map((project) => (
                  <option key={project.projectId} value={project.projectId}>
                    {project.projectName} ({project.projectCode})
                  </option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              验工阶段 <span className="text-rose-500">*</span>
            </label>
            <select
              value={selectedStageId}
              onChange={(event) => setSelectedStageId(event.target.value)}
              className="form-input"
              disabled={!selectedProjectId || Boolean(initialInspection) || stageLoading}
            >
              <option value="">
                {stageLoading ? "阶段加载中..." : selectedProjectId ? "请选择阶段" : "请先选择项目"}
              </option>
              {stages.map((stage) => (
                <option key={stage.projectStageId} value={stage.projectStageId}>
                  {stage.stageName} (产值比例 {stage.stageOutput}%)
                </option>
              ))}
            </select>
            {selectedProjectId && !stageLoading && stages.length === 0 && (
              <p className="text-xs text-amber-500 mt-1">
                该项目暂无进行中的阶段，请先在项目详情中启动阶段
              </p>
            )}
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              本次完成比例 (%) <span className="text-rose-500">*</span>
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <input
                type="number"
                min="0.01"
                max={remainingInspectionRatio}
                step="0.01"
                value={completionRatio}
                onChange={(event) => {
                  const nextValue = event.target.value;
                  const numericValue = Number(nextValue);
                  if (nextValue && Number.isFinite(numericValue) && numericValue > remainingInspectionRatio) {
                    setCompletionRatio(formatPercentage(remainingInspectionRatio));
                    return;
                  }
                  setCompletionRatio(nextValue);
                }}
                placeholder={selectedStage ? `最多 ${formatPercentage(remainingInspectionRatio)}` : "请先选择阶段"}
                className="form-input w-32"
                disabled={!selectedStage || remainingInspectionRatio <= 0}
              />
              <span className="text-sm text-slate-500">%</span>
              {selectedStage && (
                <span className={remainingInspectionRatio > 0 ? "text-xs text-slate-400" : "text-xs text-amber-500"}>
                  已通过 {formatPercentage(approvedInspectionRatio)}%
                  {pendingInspectionRatio > 0 && `，待审核 ${formatPercentage(pendingInspectionRatio)}%`}
                  ，本次最多可申请 {formatPercentage(remainingInspectionRatio)}%
                </span>
              )}
            </div>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              一级审批人 <span className="text-rose-500">*</span>
            </label>
            <button
              type="button"
              className="form-input flex items-center justify-between gap-3 text-left disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"
              disabled={!selectedProjectId}
              aria-haspopup="dialog"
              onClick={() => setApproverPickerOpen(true)}
            >
              <span className={firstApproverId ? "truncate text-slate-700" : "truncate text-slate-400"}>
                {!selectedProjectId ? "请先选择项目" : firstApproverName || "请选择一级审批人"}
              </span>
              <UserRoundSearch className="h-4 w-4 shrink-0 text-slate-400" />
            </button>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              验工说明 <span className="text-rose-500">*</span>
            </label>
            <textarea
              rows={4}
              placeholder="请描述本阶段完成的主要工作内容..."
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              className="form-input resize-none"
            />
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              验收材料 <span className="text-rose-500">*</span>
            </label>
            {files.length > 0 && (
              <div className="mb-2">
                <EditableAttachmentFileList
                  files={files}
                  onRemove={(fileId) => setFiles((prev) => prev.filter((item) => item.fileId !== fileId))}
                />
              </div>
            )}
            <label className="flex items-center justify-center gap-2 p-4 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors">
              {uploading ? (
                <Loader2 className="w-4 h-4 text-blue-500 animate-spin" />
              ) : (
                <Upload className="w-4 h-4 text-slate-400" />
              )}
              <span className="text-sm text-slate-500">{uploading ? "上传中..." : "点击上传验收材料"}</span>
              <input
                type="file"
                className="hidden"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.csv,.rtf,.odt,.ods,.odp,.jpg,.jpeg,.png,.gif,.bmp,.webp,.svg,.tiff,.tif,.zip,.rar,.7z,.mp3,.wav"
                disabled={uploading}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) handleFileUpload(file);
                  event.target.value = "";
                }}
              />
            </label>
            <div className="mt-2 flex items-center justify-between text-xs text-slate-400">
              <span>单个文件最大 500MB</span>
              {uploading && <span>{uploadProgress}%</span>}
            </div>
            {uploading && (
              <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
                <div
                  className="h-full rounded-full bg-blue-500 transition-all duration-300"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            )}
          </div>
        </div>

        <div
          className={
            dataReady && !loadError
              ? "flex justify-end gap-3 pt-4 border-t border-slate-100 mt-4"
              : "hidden"
          }
        >
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            disabled={submitting || uploading}
            onClick={handleSubmit}
          >
            {submitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
                提交中...
              </>
            ) : (
              initialInspection ? "重新提交" : "提交验工"
            )}
          </Button>
        </div>
        </DialogContent>
      </Dialog>

      <UserPickerDialog
        open={approverPickerOpen}
        onOpenChange={setApproverPickerOpen}
        value={firstApproverId}
        title="选择一级审批人"
        onSelect={(user) => {
          setFirstApproverId(String(user.userId));
          setFirstApproverName(user.realName || user.username);
        }}
      />
    </>
  );
}
