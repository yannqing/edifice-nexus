"use client";

import { useEffect, useState } from "react";
import { FileText, Loader2, Upload, X } from "lucide-react";
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
import { getMyProjects, getProjectDetail, uploadDocument } from "@/services/project";
import { ResponseCode } from "@/types/api";
import type { FilesVo, ProjectListVo, ProjectStageVo } from "@/types/project";

type InitialInspectionProject = Pick<ProjectListVo, "projectId" | "projectName" | "projectCode">;

interface CreateInspectionDialogProps {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  onSuccess: () => void;
  initialProject?: InitialInspectionProject | null;
}

export function CreateInspectionDialog({
  open,
  onOpenChange,
  onSuccess,
  initialProject,
}: CreateInspectionDialogProps) {
  const initialProjectId = initialProject?.projectId ? String(initialProject.projectId) : "";
  const [projects, setProjects] = useState<InitialInspectionProject[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [selectedStageId, setSelectedStageId] = useState("");
  const [description, setDescription] = useState("");
  const [files, setFiles] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setSelectedProjectId("");
      setSelectedStageId("");
      setDescription("");
      setFiles([]);
      setStages([]);
      setProjects([]);
      return;
    }

    setSelectedProjectId(initialProjectId);

    async function loadProjects() {
      try {
        const res = await getMyProjects({ projectStatus: 1, pageSize: 100 });
        if (res.code === ResponseCode.SUCCESS && res.data) {
          const records = res.data.records ?? [];
          const hasInitialProject = initialProject
            ? records.some((project) => String(project.projectId) === String(initialProject.projectId))
            : true;
          setProjects(hasInitialProject || !initialProject ? records : [initialProject, ...records]);
        }
      } catch {
        if (initialProject) setProjects([initialProject]);
      }
    }

    loadProjects();
  }, [open, initialProjectId, initialProject]);

  useEffect(() => {
    if (!selectedProjectId) {
      setStages([]);
      setSelectedStageId("");
      return;
    }

    async function loadStages() {
      try {
        const res = await getProjectDetail(selectedProjectId);
        if (res.code === ResponseCode.SUCCESS && res.data?.projectStages) {
          const inProgressStages = res.data.projectStages.filter((stage) => stage.stageStatus === 1);
          setStages(inProgressStages);
        }
      } catch {
        setStages([]);
      }
    }

    loadStages();
  }, [selectedProjectId]);

  const handleFileUpload = async (file: File) => {
    setUploading(true);
    try {
      const res = await uploadDocument(file);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setFiles((prev) => [...prev, res.data]);
      }
    } catch {
      /* request.ts handles user-facing errors */
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
    if (!description.trim()) {
      toast.error("请填写验工说明");
      return;
    }

    setSubmitting(true);
    try {
      const fileIds = files.length > 0 ? JSON.stringify(files.map((file) => file.fileId)) : undefined;

      const res = await applyInspection({
        projectId: Number(selectedProjectId),
        projectStageId: Number(selectedStageId),
        inspectionFormDescription: description,
        fileIds,
      });

      if (res.code === ResponseCode.SUCCESS) {
        toast.success("验工单提交成功");
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
          <DialogTitle>发起验工申请</DialogTitle>
          <DialogDescription>选择项目和阶段，提交验工材料</DialogDescription>
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
              disabled={!selectedProjectId}
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
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">验收材料</label>
            {files.length > 0 && (
              <div className="space-y-2 mb-2">
                {files.map((file) => (
                  <div key={file.fileId} className="flex items-center gap-2 p-2 bg-slate-50 rounded-lg">
                    <FileText className="w-3.5 h-3.5 text-blue-500 shrink-0" />
                    <span className="text-xs text-slate-600 truncate flex-1">{file.displayName}</span>
                    <button
                      type="button"
                      onClick={() => setFiles((prev) => prev.filter((item) => item.fileId !== file.fileId))}
                      className="p-0.5 text-slate-400 hover:text-rose-500 transition-colors"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ))}
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
                accept=".pdf,.doc,.docx,.xls,.xlsx"
                disabled={uploading}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) handleFileUpload(file);
                  event.target.value = "";
                }}
              />
            </label>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-slate-100 mt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button className="bg-blue-600 hover:bg-blue-700 text-white" disabled={submitting} onClick={handleSubmit}>
            {submitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
                提交中...
              </>
            ) : (
              "提交验工"
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
