"use client";

import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { ChevronLeft, ChevronRight, FileCheck2, Loader2, Pencil, Save, Search, Trash2, Upload, UserRound } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { FileUploadField } from "@/components/file/file-upload-field";
import { UserPickerDialog } from "@/components/user/user-picker-dialog";
import { getAllProjects, getProjectDetail } from "@/services/project";
import { getProjectFileDraftPolicy, getProjectFileDrafts, getProjectFileFolders, resolveProjectFileFolders, removeProjectFileDraft, saveProjectFileDrafts, submitProjectFileDrafts, updateProjectFileDraft } from "@/services/project-file";
import { folderAncestors, folderOptions, relativeDirectory } from "@/lib/project-file-folders";
import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import { ResponseCode } from "@/types/api";
import type { FilesVo, ProjectListVo, ProjectStageVo } from "@/types/project";
import { FILE_CATEGORY_OPTIONS, type ProjectFileVo, type ProjectFileFolder } from "@/types/project-file";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
  lockedProjectId?: string;
  lockedProjectName?: string;
  initialTab?: "upload" | "drafts";
  initialFolderId?: string;
}

const fieldClass = "h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm disabled:bg-slate-50";
const dateText = (value?: string | null) => value?.replace("T", " ").slice(0, 16) ?? "-";

export function UploadProjectFileDialog({ open, onOpenChange, onSuccess, lockedProjectId, lockedProjectName, initialTab = "upload", initialFolderId = "0" }: Props) {
  const [tab, setTab] = useState<"upload" | "drafts">(initialTab);
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [projectId, setProjectId] = useState(lockedProjectId ?? "");
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [stageId, setStageId] = useState("");
  const [category, setCategory] = useState("");
  const [description, setDescription] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [files, setFiles] = useState<FilesVo[]>([]);
  const [folders, setFolders] = useState<ProjectFileFolder[]>([]);
  const [folderId, setFolderId] = useState(initialFolderId);
  const [foldersReady, setFoldersReady] = useState(false);
  const locations = useMemo(() => folderOptions(folders), [folders]);
  const [uploading, setUploading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [initializing, setInitializing] = useState(true);
  const [retention, setRetention] = useState(30);
  const [maxBatchFiles, setMaxBatchFiles] = useState(200);
  const [drafts, setDrafts] = useState<ProjectFileVo[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [title, setTitle] = useState("");
  const [applyDescription, setApplyDescription] = useState("");
  const [approver, setApprover] = useState<{ id: string; name: string } | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<ProjectFileVo | null>(null);
  const pageCount = Math.max(1, Math.ceil(total / 10));

  useEffect(() => {
    if (!open) return;
    let alive = true;
    setTab(initialTab); setProjectId(lockedProjectId ?? ""); setStages([]); setStageId("");
    setFiles([]); setSelected(new Set()); setPage(1); setKeyword(""); setSearch(""); setError("");
    setCategory(""); setDescription(""); setDisplayName(""); setTitle(""); setApplyDescription(""); setApprover(null);
    setInitializing(true);
    setEditing(null); setPickerOpen(false);
    setFolderId(initialFolderId); setFolders([]); setFoldersReady(false);
    void Promise.all([getProjectFileDraftPolicy(), lockedProjectId ? Promise.resolve(null) : getAllProjects({ pageSize: 200 })])
      .then(([policy, projectRes]) => {
        if (!alive) return;
        if (policy.code !== ResponseCode.SUCCESS || !policy.data) throw new Error(policy.msg || "草稿规则加载失败");
        setRetention(policy.data.retentionDays); setMaxBatchFiles(policy.data.maxBatchFiles);
        if (projectRes?.code === ResponseCode.SUCCESS) setProjects(projectRes.data?.records ?? []);
      }).catch((e) => { if (alive) setError(e instanceof Error ? e.message : "加载失败，请重新打开"); })
      .finally(() => { if (alive) setInitializing(false); });
    return () => { alive = false; };
  }, [open, initialTab, lockedProjectId, initialFolderId]);

  useEffect(() => {
    if (!open || !projectId) return;
    let alive = true;
    setStages([]); setFoldersReady(false);
    void Promise.all([getProjectDetail(projectId), getProjectFileFolders(projectId)]).then(([res, folderRes]) => {
      if (!alive) return;
      if (res.code !== ResponseCode.SUCCESS || !res.data) throw new Error(res.msg || "项目加载失败");
      if (folderRes.code !== ResponseCode.SUCCESS || !folderRes.data) throw new Error(folderRes.msg || "文件夹加载失败");
      setStages(res.data.projectStages ?? []); setFolders(folderRes.data);
      setFolderId((id) => id === "0" || folderRes.data!.some((folder) => folder.folderId === id) ? id : "0");
      setFoldersReady(true);
    }).catch((e) => { if (alive) setError(e instanceof Error ? e.message : "项目加载失败，请重新打开"); });
    return () => { alive = false; };
  }, [open, projectId]);

  useEffect(() => {
    const timer = setTimeout(() => { setSearch(keyword.trim()); setPage(1); }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    if (!open || !projectId) { setDrafts([]); setTotal(0); return; }
    const controller = new AbortController();
    setLoading(true);
    void getProjectFileDrafts({ projectId, current: page, pageSize: 10, keyword: search || undefined }, controller.signal)
      .then((res) => {
        if (controller.signal.aborted) return;
        if (res.code !== ResponseCode.SUCCESS || !res.data) throw new Error(res.msg || "草稿加载失败");
        setDrafts(res.data.records ?? []); setTotal(res.data.total);
        if (page > 1 && page > Math.max(1, Math.ceil(res.data.total / 10))) setPage(Math.max(1, Math.ceil(res.data.total / 10)));
      }).catch((e) => { if (!isAbortError(e) && !controller.signal.aborted) setError(e instanceof Error ? e.message : "草稿加载失败"); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [open, projectId, page, search, reload]);

  const close = (next: boolean) => {
    if (next) return onOpenChange(true);
    if (busy || uploading) { toast.error("请先完成或取消当前操作"); return; }
    if (files.length && !window.confirm("还有未保存为草稿的文件，确定离开吗？")) return;
    onOpenChange(false);
  };

  const save = async () => {
    if (!projectId || !files.length || uploading) return;
    setBusy(true); setError("");
    try {
      const paths = [...new Set(files.map((file) => relativeDirectory(file.relativePath)).filter(Boolean))];
      let folderIds: Record<string, string> = {};
      if (paths.length) {
        const resolved = await resolveProjectFileFolders({ projectId, parentFolderId: folderId, paths });
        if (resolved.code !== ResponseCode.SUCCESS || !resolved.data) throw new Error(resolved.msg || "文件夹创建失败");
        folderIds = resolved.data;
      }
      let saved = 0;
      for (let offset = 0; offset < files.length; offset += 20) {
        const group = files.slice(offset, offset + 20);
        const res = await saveProjectFileDrafts(group.map((file) => {
          const directory = relativeDirectory(file.relativePath);
          if (directory && !folderIds[directory]) throw new Error("文件夹路径未完整保存，请重试");
          return { projectId, fileId: file.fileId, folderId: directory ? folderIds[directory] : folderId,
            projectStageId: stageId || undefined, fileCategory: category || undefined, description: description || undefined,
            fileName: files.length === 1 && displayName.trim() ? displayName.trim() : file.displayName };
        }));
        if (res.code !== ResponseCode.SUCCESS || !res.data) throw new Error(res.msg || "草稿保存失败");
        saved += group.length;
        setSelected((prev) => new Set([...prev, ...res.data!]));
        setFiles(files.slice(saved));
      }
      const updatedFolders = await getProjectFileFolders(projectId).catch(() => null);
      if (updatedFolders?.code === ResponseCode.SUCCESS && updatedFolders.data) setFolders(updatedFolders.data);
      setPage(1); setKeyword(""); setSearch("");
      setReload((v) => v + 1); setTab("drafts"); toast.success(`已保存 ${saved} 份草稿`); onSuccess();
    } catch (e) { setReload((v) => v + 1); onSuccess(); setError(e instanceof Error ? e.message : "保存失败，请重试"); }
    finally { setBusy(false); }
  };

  const remove = async (draft: ProjectFileVo) => {
    if (!window.confirm(`确定移除草稿“${draft.fileName ?? "文件"}”吗？`)) return;
    setBusy(true); setError("");
    try {
      const res = await removeProjectFileDraft(draft.projectFileId);
      if (res.code !== ResponseCode.SUCCESS) throw new Error(res.msg || "移除失败");
      setSelected((prev) => { const next = new Set(prev); next.delete(draft.projectFileId); return next; });
      setReload((v) => v + 1);
    } catch (e) { setError(e instanceof Error ? e.message : "移除失败"); }
    finally { setBusy(false); }
  };

  const selectAll = async () => {
    setBusy(true); setError("");
    try {
      const ids: string[] = [];
      for (let p = 1; ; p++) {
        const res = await getProjectFileDrafts({ projectId, current: p, pageSize: 100, keyword: search || undefined });
        if (res.code !== ResponseCode.SUCCESS || !res.data) throw new Error(res.msg || "草稿加载失败");
        ids.push(...res.data.records.map((f) => f.projectFileId));
        if (ids.length > maxBatchFiles) throw new Error(`一批最多 ${maxBatchFiles} 个文件，请缩小搜索范围或分批提交`);
        if (ids.length >= res.data.total || !res.data.records.length) break;
      }
      setSelected(new Set(ids));
    } catch (e) { setError(e instanceof Error ? e.message : "选择失败"); }
    finally { setBusy(false); }
  };

  const saveEdit = async () => {
    if (!editing) return;
    setBusy(true);
    try {
      const res = await updateProjectFileDraft(editing.projectFileId, { projectId: editing.projectId, fileId: editing.fileId,
        folderId: editing.folderId || "0",
        fileName: editing.fileName || undefined, projectStageId: editing.projectStageId || undefined,
        fileCategory: editing.fileCategory || undefined, description: editing.description || undefined });
      if (res.code !== ResponseCode.SUCCESS) throw new Error(res.msg || "修改失败");
      setEditing(null); setReload((v) => v + 1); toast.success("草稿已更新");
    } catch (e) { toast.error(e instanceof Error ? e.message : "修改失败"); }
    finally { setBusy(false); }
  };

  const submit = async () => {
    if (!projectId || !selected.size || files.length) return;
    if (selected.size > maxBatchFiles) return setError(`一批最多 ${maxBatchFiles} 个文件`);
    if (!window.confirm(`将 ${selected.size} 个文件作为一批发起审批，提交后本批文件清单不可增删。确定提交吗？`)) return;
    setBusy(true); setError("");
    try {
      const res = await submitProjectFileDrafts({ projectId, draftIds: [...selected], title: title || undefined,
        description: applyDescription || undefined, firstApproverId: approver?.id });
      if (res.code !== ResponseCode.SUCCESS) throw new Error(res.msg || "提交失败");
      toast.success(`已整批提交 ${selected.size} 个文件`); onSuccess(); onOpenChange(false);
    } catch (e) { setError(e instanceof Error ? e.message : "提交失败，请刷新草稿重试"); setReload((v) => v + 1); }
    finally { setBusy(false); }
  };

  return <>
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="flex h-[min(820px,calc(100dvh_-_2rem))] max-h-[calc(100dvh_-_2rem)] max-w-4xl flex-col gap-0 overflow-hidden p-0 transition-[opacity,transform]">
        <DialogHeader className="shrink-0 border-b border-slate-100 px-5 pb-3 pt-5 pr-12">
          <DialogTitle>项目文件申请</DialogTitle>
          <DialogDescription>草稿自保存起保留 {retention} 天，到期失效；已提交文件不受影响。</DialogDescription>
        </DialogHeader>
        {initializing ? <div className="flex flex-1 items-center justify-center gap-2 text-sm text-slate-500"><Loader2 className="h-5 w-5 animate-spin" />加载中...</div> : <>
          <div className="shrink-0 space-y-3 px-5 pt-3">
            <div className="flex min-w-0 items-center gap-3 text-sm">
              <label htmlFor="file-project" className="shrink-0 text-slate-500">项目</label>
              {lockedProjectId ? <span className="min-w-0 truncate font-medium" title={lockedProjectName}>{lockedProjectName ?? projectId}</span>
                : <select id="file-project" className={fieldClass} value={projectId} disabled={busy || uploading || files.length > 0}
                    onChange={(e) => { setProjectId(e.target.value); setStageId(""); setSelected(new Set()); setPage(1); setError(""); }}>
                    <option value="">请选择项目</option>{projects.map((p) => <option key={p.projectId} value={p.projectId}>{p.projectName}</option>)}
                  </select>}
            </div>
            <div role="tablist" aria-label="项目文件" className="flex border-b border-slate-200">
              {([{ key: "upload", name: "上传文件", icon: Upload }, { key: "drafts", name: `我的草稿${total ? `（${total}）` : ""}`, icon: FileCheck2 }] as const).map((t) =>
                <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} disabled={busy || uploading}
                  onClick={() => setTab(t.key)} className={cn("flex items-center gap-2 border-b-2 px-3 py-2 text-sm", tab === t.key ? "border-blue-600 text-blue-600" : "border-transparent text-slate-500")}>
                  <t.icon className="h-4 w-4" />{t.name}
                </button>)}
            </div>
          </div>
          {tab === "upload" ? <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="space-y-1 text-xs text-slate-600 sm:col-span-2">保存目录
                <select className={fieldClass} value={folderId} disabled={busy || uploading || !foldersReady || files.length > 0} onChange={(e) => setFolderId(e.target.value)}>
                  <option value="0">项目文件 / 根目录</option>{locations.map((folder) => <option key={folder.id} value={folder.id}>{folder.path}</option>)}
                </select>
              </label>
              <label className="space-y-1 text-xs text-slate-600">所属阶段
                <select className={fieldClass} value={stageId} disabled={busy || uploading || !projectId} onChange={(e) => setStageId(e.target.value)}>
                  <option value="">不关联阶段</option>{stages.map((s) => <option key={s.projectStageId} value={s.projectStageId}>{s.stageName}</option>)}
                </select>
              </label>
              <label className="space-y-1 text-xs text-slate-600">文件分类
                <select className={fieldClass} value={category} disabled={busy || uploading} onChange={(e) => setCategory(e.target.value)}>
                  <option value="">未分类</option>{FILE_CATEGORY_OPTIONS.map((c) => <option key={c}>{c}</option>)}
                </select>
              </label>
              <label className="space-y-1 text-xs text-slate-600">文件名称（单文件选填）
                <input className={fieldClass} value={displayName} maxLength={255} disabled={busy || files.length > 1} placeholder="留空使用原文件名" onChange={(e) => setDisplayName(e.target.value)} />
              </label>
              <label className="space-y-1 text-xs text-slate-600">文件说明
                <input className={fieldClass} value={description} maxLength={512} disabled={busy} onChange={(e) => setDescription(e.target.value)} />
              </label>
            </div>
            <div className="mt-4"><FileUploadField purpose="project" value={files} onChange={setFiles} onBusyChange={setUploading}
              allowDirectory maxDirectoryDepth={20 - folderAncestors(folders, folderId).length}
              maxFiles={maxBatchFiles} disabled={busy || !projectId || !foldersReady} /></div>
          </div> : <div className="flex min-h-0 flex-1 flex-col px-5 pt-3">
            <div className="flex shrink-0 flex-wrap items-center gap-2 pb-3">
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input aria-label="搜索草稿" className={cn(fieldClass, "pl-10")} value={keyword} placeholder="搜索文件名、分类、说明" onChange={(e) => setKeyword(e.target.value)} />
              </div>
              <Button size="sm" variant="outline" disabled={busy || loading || !total} onClick={selectAll}>选择全部{search ? "结果" : "草稿"}</Button>
              <Button size="sm" variant="ghost" disabled={busy || !selected.size} onClick={() => setSelected(new Set())}>清空选择</Button>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
              {loading ? <div className="flex h-full items-center justify-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" />加载草稿...</div>
                : !drafts.length ? <p className="py-10 text-center text-sm text-slate-400">暂无可提交草稿</p>
                : drafts.map((draft) => <div key={draft.projectFileId} className="flex min-w-0 items-start gap-3 border-b border-slate-100 py-3 text-sm">
                    <input type="checkbox" className="mt-1 shrink-0" aria-label={`选择${draft.fileName ?? "文件"}`} checked={selected.has(draft.projectFileId)} disabled={busy}
                      onChange={(e) => setSelected((prev) => { const next = new Set(prev); if (e.target.checked) next.add(draft.projectFileId); else next.delete(draft.projectFileId); return next; })} />
                    <div className="min-w-0 flex-1">
                      <AttachmentFileActions fileId={draft.fileId} fileName={draft.fileName} fileSize={draft.fileSize ?? undefined} />
                      <p className="mt-1 text-xs text-slate-500">{draft.fileCategory || "未分类"} · {draft.stageName || "不关联阶段"} · 保存于 {dateText(draft.createdTime)}</p>
                      <p className="mt-1 break-all text-xs text-slate-500">目录：{draft.folderPath || "根目录"}</p>
                      <p className={cn("mt-1 text-xs", draft.draftExpiresAt && new Date(draft.draftExpiresAt).getTime() - Date.now() < 3 * 86400000 ? "text-amber-700" : "text-slate-500")}>到期时间：{dateText(draft.draftExpiresAt)}</p>
                    </div>
                    <div className="flex shrink-0 flex-col sm:flex-row">
                    <Button size="icon" variant="ghost" className="h-8 w-8" title="编辑草稿" aria-label="编辑草稿" disabled={busy} onClick={() => setEditing({ ...draft })}><Pencil className="h-4 w-4 text-slate-400" /></Button>
                    <Button size="icon" variant="ghost" className="h-8 w-8" title="移除草稿" aria-label="移除草稿" disabled={busy} onClick={() => remove(draft)}><Trash2 className="h-4 w-4 text-slate-400" /></Button>
                    </div>
                  </div>)}
            </div>
            <div className="flex shrink-0 items-center justify-between border-b border-slate-100 py-2 text-xs text-slate-500">
              <span>共 {total} 份，已选 {selected.size} 份</span>
              <div className="flex items-center gap-2">
                <Button size="icon" variant="ghost" className="h-7 w-7" title="上一页" aria-label="上一页" disabled={page <= 1 || loading || busy} onClick={() => setPage((p) => p - 1)}><ChevronLeft className="h-4 w-4" /></Button>
                <span>{page}/{pageCount}</span>
                <Button size="icon" variant="ghost" className="h-7 w-7" title="下一页" aria-label="下一页" disabled={page >= pageCount || loading || busy} onClick={() => setPage((p) => p + 1)}><ChevronRight className="h-4 w-4" /></Button>
              </div>
            </div>
            <div className="grid shrink-0 grid-cols-1 gap-2 py-3 sm:grid-cols-2">
              <input aria-label="申请标题" className={fieldClass} placeholder="申请标题（选填）" maxLength={255} value={title} disabled={busy} onChange={(e) => setTitle(e.target.value)} />
              <Button variant="outline" className="h-9 min-w-0 justify-start" disabled={busy} onClick={() => setPickerOpen(true)}>
                <UserRound className="mr-2 h-4 w-4 shrink-0" /><span className="truncate">{approver?.name ?? "首审人：默认项目负责人"}</span>
              </Button>
              <input aria-label="申请说明" className={cn(fieldClass, "sm:col-span-2")} placeholder="申请说明（选填）" maxLength={512} value={applyDescription} disabled={busy} onChange={(e) => setApplyDescription(e.target.value)} />
            </div>
          </div>}
          {error && <p role="alert" className="max-h-20 shrink-0 overflow-y-auto break-words px-5 pb-2 text-sm text-rose-600">{error}</p>}
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-slate-100 px-5 py-3">
            <Button variant="outline" disabled={busy || uploading} onClick={() => close(false)}>关闭</Button>
            {tab === "upload" ? <Button disabled={busy || uploading || !projectId || !files.length} onClick={save}>
              {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}保存到草稿
            </Button> : <Button disabled={busy || uploading || !projectId || !selected.size || files.length > 0} onClick={submit}>
              {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FileCheck2 className="mr-2 h-4 w-4" />}整批发起申请（{selected.size}）
            </Button>}
          </div>
        </>}
      </DialogContent>
    </Dialog>
    <UserPickerDialog open={pickerOpen} onOpenChange={setPickerOpen} value={approver?.id} title="选择首审人"
      onSelect={(user) => setApprover({ id: user.userId, name: user.realName || user.username })} />
    <Dialog open={!!editing} onOpenChange={(value) => { if (!value && !busy) setEditing(null); }}>
      <DialogContent className="max-w-lg">
        <DialogHeader><DialogTitle>编辑文件草稿</DialogTitle><DialogDescription>到期时间：{dateText(editing?.draftExpiresAt)}</DialogDescription></DialogHeader>
        {editing && <div className="mt-4 space-y-3">
          <label className="block space-y-1 text-xs text-slate-600">文件名称<input className={fieldClass} maxLength={255} value={editing.fileName ?? ""} onChange={(e) => setEditing({ ...editing, fileName: e.target.value })} /></label>
          <label className="block space-y-1 text-xs text-slate-600">保存目录<select className={fieldClass} value={editing.folderId || "0"} onChange={(e) => setEditing({ ...editing, folderId: e.target.value })}>
            <option value="0">根目录</option>{locations.map((folder) => <option key={folder.id} value={folder.id}>{folder.path}</option>)}
          </select></label>
          <label className="block space-y-1 text-xs text-slate-600">所属阶段<select className={fieldClass} value={editing.projectStageId ?? ""} onChange={(e) => setEditing({ ...editing, projectStageId: e.target.value })}>
            <option value="">不关联阶段</option>{stages.map((s) => <option key={s.projectStageId} value={s.projectStageId}>{s.stageName}</option>)}
          </select></label>
          <label className="block space-y-1 text-xs text-slate-600">文件分类<select className={fieldClass} value={editing.fileCategory ?? ""} onChange={(e) => setEditing({ ...editing, fileCategory: e.target.value })}>
            <option value="">未分类</option>{FILE_CATEGORY_OPTIONS.map((c) => <option key={c}>{c}</option>)}
          </select></label>
          <label className="block space-y-1 text-xs text-slate-600">说明<input className={fieldClass} maxLength={512} value={editing.description ?? ""} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></label>
          <div className="flex justify-end gap-2"><Button variant="outline" disabled={busy} onClick={() => setEditing(null)}>取消</Button><Button disabled={busy} onClick={saveEdit}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}保存</Button></div>
        </div>}
      </DialogContent>
    </Dialog>
  </>;
}
