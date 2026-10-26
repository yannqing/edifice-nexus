"use client";

import { createContext, memo, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore,
  type ComponentProps, type ComponentType, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { CheckCircle2, ChevronDown, Loader2, Maximize2, Minus, RotateCcw, Upload, X } from "lucide-react";
import { toast } from "sonner";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { useAuth } from "@/store/auth-context";
import { cancelUpload, uploadFile } from "@/lib/file-upload";
import { UploadQueue, type UploadTask } from "@/lib/upload-queue";

interface WindowControl { close: () => void; success: () => void }
interface UploadWindow {
  id: string;
  key: string;
  title: string;
  busy: boolean;
  completed: boolean;
  render: (control: WindowControl) => ReactNode;
  onSuccess?: () => void;
}
interface Workspace {
  queue: UploadQueue;
  ownerId: string;
  activeId: string | null;
  launch: (options: Omit<UploadWindow, "id" | "busy" | "completed">) => void;
  minimize: () => void;
  close: (id: string) => void;
  success: () => void;
  setMeta: (id: string, title: string | undefined, busy: boolean, completed: boolean) => void;
}
const WorkspaceContext = createContext<Workspace | null>(null);
const WindowContext = createContext<{ id: string; key: string; busy: boolean } | null>(null);
const EMPTY_TASKS: UploadTask[] = [];
const serverTasks = () => EMPTY_TASKS;

export const UPLOAD_BUSINESS_UPDATED = "uploads:business-updated";
export const UPLOAD_WINDOW_MINIMIZED = "uploads:window-minimized";

export function useUploadBusinessRefresh(refresh: () => void) {
  const current = useRef(refresh);
  useLayoutEffect(() => { current.current = refresh; }, [refresh]);
  useEffect(() => {
    const update = () => current.current();
    window.addEventListener(UPLOAD_BUSINESS_UPDATED, update);
    return () => window.removeEventListener(UPLOAD_BUSINESS_UPDATED, update);
  }, []);
}

export function UploadWorkspaceProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const ownerId = String(user?.userId ?? "anonymous");
  return <UploadWorkspaceOwner key={ownerId} ownerId={ownerId}>{children}</UploadWorkspaceOwner>;
}

function UploadWorkspaceOwner({ children, ownerId }: { children: ReactNode; ownerId: string }) {
  const [queue] = useState(() => new UploadQueue(uploadFile, cancelUpload));
  const [windows, setWindows] = useState<UploadWindow[]>([]);
  const windowsRef = useRef<UploadWindow[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const tasks = useSyncExternalStore(queue.subscribe, queue.getSnapshot, serverTasks);
  const pathname = usePathname();
  const previousPath = useRef(pathname);

  useEffect(() => {
    queue.activate();
    return () => queue.dispose();
  }, [queue]);

  useEffect(() => {
    if (previousPath.current === pathname) return;
    previousPath.current = pathname;
    const frame = requestAnimationFrame(() => setActiveId(null));
    return () => cancelAnimationFrame(frame);
  }, [pathname]);

  useEffect(() => {
    if (!windows.some((item) => !item.completed)) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [windows]);

  const launch = useCallback((options: Omit<UploadWindow, "id" | "busy" | "completed">) => {
    const existing = windowsRef.current.find((item) => item.key === options.key);
    if (existing) {
      windowsRef.current = windowsRef.current.map((item) => item.id === existing.id ? { ...item, onSuccess: options.onSuccess } : item);
      setWindows(windowsRef.current); setActiveId(existing.id); return;
    }
    if (windowsRef.current.length >= 10) { toast.error("最多保留10个上传窗口，请先完成或关闭已有窗口"); return; }
    const item = { ...options, id: crypto.randomUUID(), busy: false, completed: false };
    windowsRef.current = [...windowsRef.current, item];
    setWindows(windowsRef.current); setActiveId(item.id);
  }, []);
  const minimize = useCallback(() => {
    setActiveId(null); setPanelOpen(true);
    window.dispatchEvent(new Event(UPLOAD_WINDOW_MINIMIZED));
  }, []);
  const close = useCallback((id: string) => {
    queue.removeWindow(id);
    windowsRef.current = windowsRef.current.filter((item) => item.id !== id);
    setWindows(windowsRef.current); setActiveId((current) => current === id ? null : current);
  }, [queue]);
  const success = useCallback(() => {
    window.dispatchEvent(new Event(UPLOAD_BUSINESS_UPDATED));
  }, []);
  const setMeta = useCallback((id: string, title: string | undefined, busy: boolean, completed: boolean) => {
    const item = windowsRef.current.find((candidate) => candidate.id === id);
    if (!item || (item.busy === busy && item.completed === completed && (!title || item.title === title))) return;
    windowsRef.current = windowsRef.current.map((candidate) => candidate.id === id ? { ...candidate, title: title || candidate.title, busy, completed } : candidate);
    setWindows(windowsRef.current);
  }, []);
  const value = useMemo(() => ({ queue, ownerId, activeId, launch, minimize, close, success, setMeta }),
    [queue, ownerId, activeId, launch, minimize, close, success, setMeta]);
  const activeTasks = tasks.filter((task) => ["waiting", "uploading"].includes(task.status));
  const discard = (item: UploadWindow) => {
    if (item.busy || (!item.completed && !window.confirm(`确定结束“${item.title}”吗？正在上传的文件将取消，尚未保存或提交的表单将丢弃。`))) return;
    close(item.id);
  };

  return <WorkspaceContext.Provider value={value}>
    {children}
    {windows.map((item) => <WindowHost key={item.id} item={item} close={close} success={success} />)}
    {windows.length > 0 && <aside className="fixed bottom-4 right-4 z-40 w-[min(400px,calc(100vw-2rem))] text-sm" aria-label="上传任务中心">
      {panelOpen ? <section role="region" aria-label="上传任务" className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl">
        <header className="flex h-12 items-center gap-2 border-b border-slate-200 px-3">
          <Upload className="h-4 w-4 text-blue-600" /><h2 className="min-w-0 flex-1 font-semibold">上传任务（{windows.length}）</h2>
          <button type="button" className="rounded p-2 hover:bg-slate-100" title="隐藏上传任务" aria-label="隐藏上传任务" onClick={() => setPanelOpen(false)}><ChevronDown className="h-4 w-4" /></button>
        </header>
        <div className="max-h-[min(60dvh,520px)] overflow-y-auto overscroll-contain">
          {windows.map((item) => {
            const windowTasks = tasks.filter((task) => task.windowId === item.id);
            const pending = windowTasks.some((task) => ["waiting", "uploading"].includes(task.status));
            const failed = windowTasks.some((task) => task.status === "error");
            const completed = windowTasks.filter((task) => task.status === "completed").length;
            return <section key={item.id} className="border-b border-slate-100 px-3 py-3 last:border-b-0" aria-label={item.title}>
              <div className="flex min-w-0 items-start gap-2">
                <div className="min-w-0 flex-1">
                  <h3 className="break-words font-medium">{item.title}</h3>
                  <p className={`mt-1 text-xs ${failed ? "text-rose-600" : "text-slate-500"}`}>
                    {item.completed ? "操作已完成" : item.busy ? "正在保存或提交" : failed ? "部分上传失败" : pending ? `上传中 · 已完成 ${completed}/${windowTasks.filter((task) => task.status !== "cancelled").length}`
                      : completed ? "文件已上传，待保存或提交" : "待填写或选择文件"}
                  </p>
                </div>
                <button type="button" className="shrink-0 rounded p-2 text-blue-600 hover:bg-blue-50" title="打开上传窗口" aria-label={`打开${item.title}`} onClick={() => setActiveId(item.id)}><Maximize2 className="h-4 w-4" /></button>
                <button type="button" className="shrink-0 rounded p-2 text-slate-400 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40" disabled={item.busy}
                  title="结束任务" aria-label={`结束${item.title}`} onClick={() => discard(item)}><X className="h-4 w-4" /></button>
              </div>
              {windowTasks.map((task) => <UploadTaskRow key={task.id} task={task} queue={queue} />)}
            </section>;
          })}
        </div>
      </section> : <button type="button" aria-label="显示上传任务" title="显示上传任务" aria-expanded={false}
        onClick={() => setPanelOpen(true)} className="ml-auto flex h-11 items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 shadow-lg hover:bg-slate-50">
        {activeTasks.length ? <Loader2 className="h-4 w-4 animate-spin text-blue-600" /> : <Upload className="h-4 w-4 text-blue-600" />}
        上传任务 {windows.length}{activeTasks.length ? ` · 待完成 ${activeTasks.length}` : ""}
      </button>}
    </aside>}
  </WorkspaceContext.Provider>;
}

const WindowHost = memo(function WindowHost({ item, close, success }: { item: UploadWindow; close: (id: string) => void; success: () => void }) {
  const { id, key, busy, onSuccess } = item;
  const context = useMemo(() => ({ id, key, busy }), [id, key, busy]);
  const control = useMemo(() => ({ close: () => close(id), success: () => { onSuccess?.(); success(); } }), [close, success, id, onSuccess]);
  return <WindowContext.Provider value={context}>{item.render(control)}</WindowContext.Provider>;
});

export const UploadTaskRow = memo(function UploadTaskRow({ task, queue }: { task: UploadTask; queue: UploadQueue }) {
  const running = ["waiting", "uploading"].includes(task.status);
  return <div className="mt-2 border-t border-slate-100 pt-2 text-xs">
    <div className="flex min-w-0 items-center gap-2">
      {running ? <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin text-blue-600" />
        : task.status === "completed" ? <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-emerald-600" /> : null}
      <span className="min-w-0 flex-1 truncate" title={task.name}>{task.name}</span>
      <span className="shrink-0 text-slate-500">{task.status === "completed" ? "已上传" : task.status === "error" ? "失败" : task.status === "cancelled" ? "已取消"
        : task.status === "waiting" ? "排队中" : task.progress === 95 ? "校验中" : `${task.progress}%`}</span>
      {task.status === "error" && <button type="button" className="rounded p-1.5 hover:bg-slate-100" title="重试上传" aria-label={`重试上传${task.name}`} onClick={() => queue.retry(task.id)}><RotateCcw className="h-3.5 w-3.5" /></button>}
      {(running || task.status === "error") && <button type="button" className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600" title="取消上传" aria-label={`取消上传${task.name}`} onClick={() => queue.cancel(task.id)}><X className="h-3.5 w-3.5" /></button>}
    </div>
    {task.error ? <p role="alert" className="mt-1 break-words text-rose-600">{task.error}</p> : running && <progress aria-label={`${task.name}上传进度`} className="mt-2 h-1 w-full accent-blue-600" value={task.progress} max={100} />}
  </div>;
});

interface LaunchProps { open: boolean; onOpenChange: (open: boolean) => void; onSuccess: () => void }
export function withUploadWindow<P extends LaunchProps>(Form: ComponentType<P>, options: { key: (props: P) => string; title: (props: P) => string }) {
  return function UploadWindowLauncher(props: P) {
    const workspace = useContext(WorkspaceContext);
    const latest = useRef(props);
    const mounted = useRef(false);
    useLayoutEffect(() => { latest.current = props; });
    useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const key = options.key(props);
    const launch = workspace?.launch;
    useEffect(() => {
      if (!props.open || !launch) return;
      const initial = latest.current;
      launch({ key, title: options.title(initial), onSuccess: () => { if (mounted.current) latest.current.onSuccess(); },
        render: (control) => <Form {...initial} open={true} onOpenChange={(open) => { if (!open) control.close(); }} onSuccess={control.success} /> });
      initial.onOpenChange(false);
    }, [props.open, key, launch]);
    return null;
  };
}

export function useUploadWindowStatus(title: string | undefined, busy: boolean, completed = false) {
  const workspace = useContext(WorkspaceContext);
  const session = useContext(WindowContext);
  const update = workspace?.setMeta;
  const id = session?.id;
  useLayoutEffect(() => { if (id) update?.(id, title, busy, completed); }, [id, update, title, busy, completed]);
  return !session || workspace?.activeId === session.id;
}

export function useUploadGroup(fieldKey: string) {
  const workspace = useContext(WorkspaceContext);
  const session = useContext(WindowContext);
  if (!workspace || !session) throw new Error("文件上传必须在上传工作区窗口中进行");
  return { queue: workspace.queue, groupId: `${session.id}:${fieldKey}`, windowId: session.id,
    scope: `${workspace.ownerId}:${session.key}:${fieldKey}` };
}

export function UploadWindowDialog({ open, onOpenChange, ...props }: ComponentProps<typeof Dialog>) {
  const workspace = useContext(WorkspaceContext);
  const session = useContext(WindowContext);
  const visible = !session || workspace?.activeId === session.id;
  return <Dialog {...props} open={open && visible} onOpenChange={(next) => {
    const pending = session && workspace?.queue.getSnapshot().some((task) => task.windowId === session.id && ["waiting", "uploading", "error"].includes(task.status));
    if (!next && (pending || session?.busy)) workspace?.minimize();
    else onOpenChange?.(next);
  }} />;
}

export function UploadWindowContent({ children, className, ...props }: ComponentProps<typeof DialogContent>) {
  const workspace = useContext(WorkspaceContext);
  return <DialogContent {...props} className={`${className ?? ""} [&>[data-slot=dialog-header]]:pr-20`}>
    {children}
    <button type="button" onClick={workspace?.minimize} className="absolute right-12 top-4 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
      title="最小化到上传任务" aria-label="最小化上传窗口"><Minus className="h-4 w-4" /></button>
  </DialogContent>;
}
