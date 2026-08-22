"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Upload } from "lucide-react";
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
  getUserList,
  MAX_ATTACHMENT_FILE_SIZE,
  uploadProjectAttachment,
} from "@/services/project";
import { ResponseCode } from "@/types/api";
import type { FilesVo, ProjectListVo, ProjectStageVo, UserListItem } from "@/types/project";
import type { InspectionFormDetailVo } from "@/types/inspection";
import {
  EditableAttachmentFileList,
  parseFileIdList,
} from "@/components/file/attachment-file-list";

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
  const [projects, setProjects] = useState<InitialInspectionProject[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [selectedStageId, setSelectedStageId] = useState("");
  const [firstApproverId, setFirstApproverId] = useState("");
  const [description, setDescription] = useState("");
  const [files, setFiles] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [completionRatio, setCompletionRatio] = useState("100");

  useEffect(() => {
    if (!open) {
      setSelectedProjectId("");
      setSelectedStageId("");
      setDescription("");
      setFiles([]);
      setUploadProgress(0);
      setStages([]);
      setFirstApproverId("");
      setProjects([]);
      setCompletionRatio("100");
      return;
    }

    setSelectedProjectId(initialProjectId);
    setSelectedStageId(initialStageId);
    setFirstApproverId(initialApproverId);
    setDescription(initialInspection?.inspectionFormDescription ?? "");
    setCompletionRatio(String(initialInspection?.completionRatio ?? 100));
    setFiles(
      parseFileIdList(initialInspection?.fileIds).map((fileId, index) =>
        inheritedFile(fileId, index)
      )
    );

    async function loadProjects() {
      try {
        const [projectRes, userRes] = await Promise.all([
          getMyProjects({ projectStatus: 1, pageSize: 100 }),
          getUserList(),
        ]);
        if (projectRes.code === ResponseCode.SUCCESS && projectRes.data) {
          const records = projectRes.data.records ?? [];
          const hasInitialProject = sourceProject
            ? records.some((project) => String(project.projectId) === String(sourceProject.projectId))
            : true;
          setProjects(hasInitialProject || !sourceProject ? records : [sourceProject, ...records]);
        }
        if (userRes.code === ResponseCode.SUCCESS && userRes.data) {
          setUsers(userRes.data.records ?? []);
        }
      } catch {
        if (sourceProject) setProjects([sourceProject]);
      }
    }

    loadProjects();
  }, [open, initialProjectId, initialStageId, initialApproverId, initialInspection, sourceProject]);

  useEffect(() => {
    if (!selectedProjectId) {
      setStages([]);
      setSelectedStageId("");
      setFirstApproverId("");
      return;
    }

    async function loadStages() {
      try {
        const res = await getProjectDetail(selectedProjectId);
        if (res.code === ResponseCode.SUCCESS && res.data) {
          const inProgressStages = (res.data.projectStages ?? []).filter(
            (stage) =>
              stage.stageStatus === 1 ||
              (Boolean(initialInspection) &&
                String(stage.projectStageId) === initialStageId &&
                stage.stageStatus === 4)
          );
          setStages(inProgressStages);
          setSelectedStageId(initialInspection ? initialStageId : "");
          setFirstApproverId(initialInspection ? initialApproverId : "");
        }
      } catch {
        setStages([]);
        setFirstApproverId("");
      }
    }

    loadStages();
  }, [selectedProjectId, initialInspection, initialStageId, initialApproverId]);

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
        completionRatio: Number(completionRatio) || 100,
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{initialInspection ? "重新提交验工申请" : "发起验工申请"}</DialogTitle>
          <DialogDescription>
            {initialInspection
              ? "已载入原验工单内容，请根据驳回意见调整后重新提交"
              : "选择项目和阶段，提交验工材料"}
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              选择项目 <span className="text-rose-500">*</span>
            </label>
            <select
              value={selectedProjectId}
              onChange={(event) => setSelectedProjectId(event.target.value)}
              className="form-input"
              disabled={Boolean(initialInspection)}
            >
              <option value="">请选择进行中的项目</option>
              {projects.map((project) => (
                <option key={project.projectId} value={project.projectId}>
                  {project.projectName} ({project.projectCode})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              验工阶段 <span className="text-rose-500">*</span>
            </label>
            <select
              value={selectedStageId}
              onChange={(event) => setSelectedStageId(event.target.value)}
              className="form-input"
              disabled={!selectedProjectId || Boolean(initialInspection)}
            >
              <option value="">{selectedProjectId ? "请选择阶段" : "请先选择项目"}</option>
              {stages.map((stage) => (
                <option key={stage.projectStageId} value={stage.projectStageId}>
                  {stage.stageName} (产值比例 {stage.stageOutput}%)
                </option>
              ))}
            </select>
            {selectedProjectId && stages.length === 0 && (
              <p className="text-xs text-amber-500 mt-1">
                该项目暂无进行中的阶段，请先在项目详情中启动阶段
              </p>
            )}
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              本次完成比例 (%) <span className="text-rose-500">*</span>
            </label>
            <div className="flex items-center gap-3">
              <input
                type="number"
                min="1"
                max="100"
                step="1"
                value={completionRatio}
                onChange={(e) => setCompletionRatio(e.target.value)}
                placeholder="1-100"
                className="form-input w-32"
              />
              <span className="text-sm text-slate-500">%</span>
              {(() => {
                const selectedStage = stages.find((s) => String(s.projectStageId) === selectedStageId);
                if (selectedStage) {
                  const used = selectedStage.completionRatio ?? 0;
                  const remaining = 100 - used;
                  return (
                    <span className="text-xs text-slate-400">
                      该阶段已完成 {used}%，剩余可申请 {remaining}%
                    </span>
                  );
                }
                return null;
              })()}
            </div>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              一级审批人 <span className="text-rose-500">*</span>
            </label>
            <select
              value={firstApproverId}
              onChange={(event) => setFirstApproverId(event.target.value)}
              className="form-input"
              disabled={!selectedProjectId || users.length === 0}
            >
              <option value="">
                {!selectedProjectId
                  ? "请先选择项目"
                  : users.length === 0
                    ? "暂无可选审批人"
                    : "请选择一级审批人"}
              </option>
              {users.map((user) => (
                <option key={user.userId} value={user.userId}>
                  {user.realName || user.username}
                </option>
              ))}
            </select>
            {selectedProjectId && users.length === 0 && (
              <p className="text-xs text-amber-500 mt-1">暂无可选审批人，请先维护启用员工</p>
            )}
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

        <div className="flex justify-end gap-3 pt-4 border-t border-slate-100 mt-4">
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
  );
}
