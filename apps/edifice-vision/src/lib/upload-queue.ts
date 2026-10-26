import type { FilesVo } from "@/types/project";
import type { UploadPurpose } from "./file-upload";

export type UploadTaskStatus = "waiting" | "uploading" | "completed" | "error" | "cancelled";
export interface UploadTask {
  id: string;
  windowId: string;
  groupId: string;
  name: string;
  size: number;
  progress: number;
  status: UploadTaskStatus;
  error?: string;
}
interface Group {
  windowId: string;
  scope: string;
  purpose: UploadPurpose;
  value: FilesVo[];
  onChange: (files: FilesVo[]) => void;
  onBusyChange?: (busy: boolean) => void;
}
interface Job {
  task: UploadTask;
  file?: File;
  controller?: AbortController;
  sessionId?: string;
}
type Executor = (file: File, purpose: UploadPurpose, options: {
  signal: AbortSignal;
  scope: string;
  onProgress: (progress: number) => void;
  onSession: (sessionId: string) => void;
}) => Promise<FilesVo>;

export class UploadQueue {
  private groups = new Map<string, Group>();
  private jobs = new Map<string, Job>();
  private listeners = new Set<() => void>();
  private snapshot: UploadTask[] = [];
  private active = 0;
  private disposed = false;
  private lastServed = new Map<string, number>();
  private sequence = 0;
  private execute: Executor;
  private cancelSession: (id: string) => Promise<void>;
  private concurrency: number;

  constructor(execute: Executor, cancelSession: (id: string) => Promise<void>, concurrency = 2) {
    this.execute = execute; this.cancelSession = cancelSession; this.concurrency = concurrency;
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };
  getSnapshot = () => this.snapshot;

  activate() { this.disposed = false; }

  bindGroup(id: string, group: Group) {
    if (this.disposed) return;
    this.groups.set(id, group);
    this.notifyBusy(id);
  }

  enqueue(groupId: string, files: File[]) {
    const group = this.groups.get(groupId);
    if (!group || this.disposed) throw new Error("上传窗口已关闭，请重新打开");
    if (this.snapshot.filter((task) => ["waiting", "uploading", "error"].includes(task.status)).length + files.length > 500) {
      throw new Error("待处理文件最多500个，请等待部分任务完成后再添加");
    }
    for (const file of files) {
      const id = crypto.randomUUID();
      this.jobs.set(id, { file, task: { id, groupId, windowId: group.windowId,
        name: file.webkitRelativePath || file.name, size: file.size, progress: 0, status: "waiting" } });
    }
    this.publish(); this.notifyBusy(groupId); this.pump();
  }

  retry(id: string) {
    const job = this.jobs.get(id);
    if (!job || job.task.status !== "error" || !job.file || this.disposed) return;
    job.task = { ...job.task, status: "waiting", error: undefined };
    this.publish(); this.notifyBusy(job.task.groupId); this.pump();
  }

  cancel(id: string) {
    const job = this.jobs.get(id);
    if (!job || !["waiting", "uploading", "error"].includes(job.task.status)) return;
    job.task = { ...job.task, status: "cancelled", error: undefined };
    job.controller?.abort();
    if (job.sessionId) void this.cancelSession(job.sessionId).catch(() => {
      if (!this.disposed && this.jobs.has(id)) {
        job.task = { ...job.task, error: "服务器取消失败，临时文件将在过期后清理" }; this.publish();
      }
    });
    job.file = undefined;
    this.publish(); this.notifyBusy(job.task.groupId); this.pump();
  }

  removeWindow(windowId: string) {
    for (const [id, job] of this.jobs) {
      if (job.task.windowId !== windowId) continue;
      this.cancel(id); this.jobs.delete(id);
    }
    for (const [id, group] of this.groups) if (group.windowId === windowId) this.groups.delete(id);
    this.lastServed.delete(windowId);
    this.publish();
  }

  dispose() {
    this.disposed = true;
    for (const job of this.jobs.values()) job.controller?.abort();
    this.jobs.clear(); this.groups.clear(); this.lastServed.clear(); this.publish(); this.listeners.clear();
  }

  private publish() {
    this.snapshot = [...this.jobs.values()].map((job) => job.task);
    this.listeners.forEach((listener) => listener());
  }
  private notifyBusy(groupId: string) {
    this.groups.get(groupId)?.onBusyChange?.([...this.jobs.values()].some((job) => job.task.groupId === groupId
      && ["waiting", "uploading", "error"].includes(job.task.status)));
  }
  private pump() {
    if (this.disposed) return;
    while (this.active < this.concurrency) {
      const waiting = [...this.jobs.values()].filter((candidate) => candidate.task.status === "waiting" && candidate.file);
      // Rotate windows so a large folder upload cannot starve a different project.
      const job = waiting.reduce<Job | undefined>((chosen, candidate) => !chosen
        || (this.lastServed.get(candidate.task.windowId) ?? 0) < (this.lastServed.get(chosen.task.windowId) ?? 0) ? candidate : chosen, undefined);
      if (!job || !job.file) break;
      const group = this.groups.get(job.task.groupId);
      if (!group) break;
      const controller = new AbortController();
      job.controller = controller;
      job.task = { ...job.task, status: "uploading" };
      this.lastServed.set(job.task.windowId, ++this.sequence);
      const file = job.file;
      this.active++; this.publish();
      void this.execute(file, group.purpose, {
        signal: controller.signal, scope: group.scope,
        onProgress: (progress) => {
          if (controller.signal.aborted || !this.jobs.has(job.task.id)) return;
          job.task = { ...job.task, progress }; this.publish();
        },
        onSession: (sessionId) => {
          job.sessionId = sessionId;
          if (controller.signal.aborted && !this.disposed) void this.cancelSession(sessionId).catch(() => {});
        },
      }).then((result) => {
        if (this.disposed || controller.signal.aborted || !this.jobs.has(job.task.id)) return;
        const current = this.groups.get(job.task.groupId);
        if (!current) return;
        const uploaded = { ...result, ...(file.webkitRelativePath ? { relativePath: file.webkitRelativePath } : {}) };
        current.value = [...current.value, uploaded];
        current.onChange(current.value);
        job.file = undefined;
        job.task = { ...job.task, status: "completed", progress: 100 };
      }).catch((error) => {
        if (this.disposed || controller.signal.aborted || !this.jobs.has(job.task.id)) return;
        job.task = { ...job.task, status: "error", error: error instanceof Error ? error.message : "上传失败" };
      }).finally(() => {
        this.active--; this.publish(); this.notifyBusy(job.task.groupId); this.pump();
      });
    }
  }
}
