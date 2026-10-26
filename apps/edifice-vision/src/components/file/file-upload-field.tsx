"use client";

import { useId, useLayoutEffect, useRef, useSyncExternalStore, type ReactNode } from "react";
import { FolderUp, Upload } from "lucide-react";
import { toast } from "sonner";
import { ATTACHMENT_ACCEPT, MAX_BATCH_FILES, MAX_FILE_SIZE, MAX_FILE_SIZE_LABEL, type UploadPurpose } from "@/lib/file-upload";
import type { FilesVo } from "@/types/project";
import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import { relativeDirectory } from "@/lib/project-file-folders";
import { UploadTaskRow, useUploadGroup } from "@/components/file/upload-workspace";

interface Props {
  fieldKey: string;
  value: FilesVo[];
  onChange: (files: FilesVo[]) => void;
  purpose: UploadPurpose;
  maxFiles?: number;
  accept?: string;
  label?: string;
  disabled?: boolean;
  onBusyChange?: (busy: boolean) => void;
  renderExtra?: (file: FilesVo) => ReactNode;
  allowDirectory?: boolean;
  maxDirectoryDepth?: number;
}

export function FileUploadField({ fieldKey, value, onChange, purpose, maxFiles = MAX_BATCH_FILES,
  accept = ATTACHMENT_ACCEPT, label = "选择文件", disabled, onBusyChange, renderExtra, allowDirectory = false, maxDirectoryDepth = 20 }: Props) {
  const inputId = useId();
  const directoryInput = useRef<HTMLInputElement>(null);
  const { queue, groupId, windowId, scope } = useUploadGroup(fieldKey);
  const allTasks = useSyncExternalStore(queue.subscribe, queue.getSnapshot, queue.getSnapshot);
  const tasks = allTasks.filter((task) => task.groupId === groupId && ["waiting", "uploading", "error"].includes(task.status));
  useLayoutEffect(() => {
    queue.bindGroup(groupId, { windowId, scope, value, onChange, onBusyChange, purpose });
  }, [queue, groupId, windowId, scope, value, onChange, onBusyChange, purpose]);

  const choose = (files: File[]) => {
    const visibleFiles = files.filter((file) => !file.webkitRelativePath || !(/^(\.DS_Store|Thumbs\.db|desktop\.ini|\._.*)$/i.test(file.name)
      || file.webkitRelativePath.split("/").includes("__MACOSX")));
    if (visibleFiles.length < files.length) toast.info(`已忽略 ${files.length - visibleFiles.length} 个系统元数据文件`);
    files = visibleFiles;
    if (!files.length) return;
    try { files.forEach((file) => relativeDirectory(file.webkitRelativePath, maxDirectoryDepth)); }
    catch (error) { toast.error(error instanceof Error ? error.message : "文件夹路径无效"); return; }
    const occupied = value.length + tasks.length;
    if (files.length + occupied > maxFiles) { toast.error(`最多添加${maxFiles}个文件`); return; }
    const allowed = new Set(accept.split(",").map((x) => x.trim().toLowerCase()));
    for (const file of files) {
      const name = file.webkitRelativePath || file.name;
      if (!file.size || file.size > MAX_FILE_SIZE) { toast.error(`${name}：单个文件必须大于0且不能超过${MAX_FILE_SIZE_LABEL}`); return; }
      const extension = "." + file.name.split(".").pop()?.toLowerCase();
      if (!allowed.has(extension)) { toast.error(`${name}：不支持该文件格式`); return; }
    }
    try { queue.enqueue(groupId, files); }
    catch (error) { toast.error(error instanceof Error ? error.message : "无法添加上传任务"); }
  };

  return <div className="space-y-2">
    <div className="max-h-64 overflow-y-auto overscroll-contain">
    {value.map((file) => <div key={`${file.fileId}:${file.relativePath || ""}`}>
      <AttachmentFileActions fileId={file.fileId} fileName={file.displayName} fileSize={file.fileSize} disabled={disabled}
        onRemove={() => onChange(value.filter((f) => f !== file))} />
      {renderExtra?.(file)}
      {file.relativePath && <p className="px-2 pb-2 text-xs text-slate-500 break-all">{file.relativePath}</p>}
    </div>)}
    {tasks.map((task) => <UploadTaskRow key={task.id} task={task} queue={queue} />)}
    </div>
    {value.length + tasks.length < maxFiles && <label htmlFor={inputId}
      className={`flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 px-3 py-4 text-sm text-slate-600 hover:border-blue-500 ${disabled ? "pointer-events-none opacity-50" : ""}`}>
      <Upload className="h-4 w-4" />{label}
      <input id={inputId} type="file" className="hidden" multiple={maxFiles > 1} accept={accept} disabled={disabled}
        onChange={(event) => { choose(Array.from(event.target.files ?? [])); event.target.value = ""; }} />
    </label>}
    {allowDirectory && value.length + tasks.length < maxFiles && <>
      <button type="button" disabled={disabled} title="上传文件夹（不包含空文件夹）" onClick={() => {
        if (!directoryInput.current || !("webkitdirectory" in directoryInput.current)) { toast.error("当前浏览器不支持文件夹选择，请使用支持该功能的桌面浏览器"); return; }
        directoryInput.current.click();
      }}
        className="flex h-10 w-full items-center justify-center gap-2 rounded-md border border-slate-200 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-50">
        <FolderUp className="h-4 w-4" />选择文件夹
      </button>
      <input ref={(node) => { directoryInput.current = node; node?.setAttribute("webkitdirectory", ""); node?.setAttribute("directory", ""); }}
        type="file" multiple className="hidden" disabled={disabled} aria-label="选择上传文件夹" onChange={(event) => {
          choose(Array.from(event.target.files ?? [])); event.target.value = "";
        }} />
    </>}
    <p className="text-xs text-slate-400">单个文件最大 {MAX_FILE_SIZE_LABEL}{maxFiles > 1 ? `，最多 ${maxFiles} 个` : ""}</p>
  </div>;
}
