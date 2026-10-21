"use client";

import { useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { FolderUp, Loader2, RotateCcw, Upload, X } from "lucide-react";
import { toast } from "sonner";
import { ATTACHMENT_ACCEPT, cancelUpload, MAX_BATCH_FILES, MAX_FILE_SIZE, MAX_FILE_SIZE_LABEL, uploadFile, type UploadPurpose } from "@/lib/file-upload";
import type { FilesVo } from "@/types/project";
import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import { relativeDirectory } from "@/lib/project-file-folders";

interface Task {
  id: string; file: File; progress: number; status: "waiting" | "uploading" | "error" | "cancelled";
  error?: string; sessionId?: string; controller?: AbortController;
}
interface Props {
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

export function FileUploadField({ value, onChange, purpose, maxFiles = MAX_BATCH_FILES,
  accept = ATTACHMENT_ACCEPT, label = "选择文件", disabled, onBusyChange, renderExtra, allowDirectory = false, maxDirectoryDepth = 20 }: Props) {
  const inputId = useId();
  const directoryInput = useRef<HTMLInputElement>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const refs = useRef({ value, onChange, onBusyChange });
  useLayoutEffect(() => { refs.current = { value, onChange, onBusyChange }; }, [value, onChange, onBusyChange]);
  const tasksRef = useRef<Task[]>([]);
  const active = useRef(0);
  const alive = useRef(true);
  const update = (next: Task[]) => {
    tasksRef.current = next;
    if (alive.current) setTasks(next);
    refs.current.onBusyChange?.(next.length > 0);
  };
  const patch = (id: string, changes: Partial<Task>) => update(tasksRef.current.map((t) => t.id === id ? { ...t, ...changes } : t));

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      tasksRef.current.forEach((task) => task.controller?.abort());
      refs.current.onBusyChange?.(false);
    };
  }, []);

  const pump = () => {
    while (active.current < 2) {
      const task = tasksRef.current.find((t) => t.status === "waiting");
      if (!task) break;
      const controller = new AbortController(); active.current++;
      patch(task.id, { status: "uploading", controller });
      void uploadFile(task.file, purpose, {
        signal: controller.signal,
        onSession: (sessionId) => patch(task.id, { sessionId }),
        onProgress: (progress) => patch(task.id, { progress }),
      }).then((file) => {
        if (!alive.current || controller.signal.aborted) return;
        const nextValue = [...refs.current.value, { ...file, ...(task.file.webkitRelativePath ? { relativePath: task.file.webkitRelativePath } : {}) }];
        refs.current.value = nextValue; refs.current.onChange(nextValue);
        update(tasksRef.current.filter((t) => t.id !== task.id));
      }).catch((error) => {
        if (!alive.current || controller.signal.aborted) return;
        patch(task.id, { status: "error", error: error instanceof Error ? error.message : "上传失败" });
      }).finally(() => { active.current--; if (alive.current) pump(); });
    }
  };

  const choose = (files: File[]) => {
    const visibleFiles = files.filter((file) => !file.webkitRelativePath || !(/^(\.DS_Store|Thumbs\.db|desktop\.ini|\._.*)$/i.test(file.name)
      || file.webkitRelativePath.split("/").includes("__MACOSX")));
    if (visibleFiles.length < files.length) toast.info(`已忽略 ${files.length - visibleFiles.length} 个系统元数据文件`);
    files = visibleFiles;
    if (!files.length) return;
    try { files.forEach((file) => relativeDirectory(file.webkitRelativePath, maxDirectoryDepth)); }
    catch (error) { toast.error(error instanceof Error ? error.message : "文件夹路径无效"); return; }
    const occupied = refs.current.value.length + tasksRef.current.filter((t) => t.status !== "cancelled").length;
    if (files.length + occupied > maxFiles) { toast.error(`最多添加${maxFiles}个文件`); return; }
    const allowed = new Set(accept.split(",").map((x) => x.trim().toLowerCase()));
    for (const file of files) {
      const name = file.webkitRelativePath || file.name;
      if (!file.size || file.size > MAX_FILE_SIZE) { toast.error(`${name}：单个文件必须大于0且不能超过${MAX_FILE_SIZE_LABEL}`); return; }
      const extension = "." + file.name.split(".").pop()?.toLowerCase();
      if (!allowed.has(extension)) { toast.error(`${name}：不支持该文件格式`); return; }
    }
    update([...tasksRef.current, ...files.map((file): Task => ({ id: crypto.randomUUID(), file, progress: 0, status: "waiting" }))]);
    pump();
  };
  const removeTask = (task: Task) => {
    task.controller?.abort();
    if (task.sessionId) void cancelUpload(task.sessionId).catch(() => toast.error("服务器取消失败，临时上传将在过期后清理"));
    update(tasksRef.current.filter((t) => t.id !== task.id));
  };

  return <div className="space-y-2">
    <div className="max-h-64 overflow-y-auto overscroll-contain">
    {value.map((file) => <div key={`${file.fileId}:${file.relativePath || ""}`}>
      <AttachmentFileActions fileId={file.fileId} fileName={file.displayName} fileSize={file.fileSize} disabled={disabled}
        onRemove={() => onChange(value.filter((f) => f !== file))} />
      {renderExtra?.(file)}
      {file.relativePath && <p className="px-2 pb-2 text-xs text-slate-500 break-all">{file.relativePath}</p>}
    </div>)}
    {tasks.map((task) => <div key={task.id} className="border-b border-slate-100 py-2 text-xs">
      <div className="flex min-w-0 items-center gap-2">
        {task.status !== "error" && <Loader2 className="h-4 w-4 shrink-0 animate-spin text-blue-600" />}
        <span className="min-w-0 flex-1 truncate" title={task.file.webkitRelativePath || task.file.name}>{task.file.webkitRelativePath || task.file.name}</span>
        <span>{task.progress === 95 ? "校验中" : `${task.progress}%`}</span>
        {task.status === "error" && <button type="button" title="重试上传" aria-label="重试上传" onClick={() => { patch(task.id, { status: "waiting", error: undefined }); pump(); }} className="p-1"><RotateCcw className="h-4 w-4" /></button>}
        <button type="button" title="取消上传" aria-label="取消上传" onClick={() => removeTask(task)} className="p-1 text-slate-400 hover:text-rose-600"><X className="h-4 w-4" /></button>
      </div>
      {task.error ? <p className="mt-1 break-words text-rose-600">{task.error}</p>
        : <progress className="mt-2 h-1 w-full accent-blue-600" value={task.progress} max={100} />}
    </div>)}
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
