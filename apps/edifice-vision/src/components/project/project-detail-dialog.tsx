"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Users,
  FileText,
  Banknote,
  Layers,
  Info,
  Loader2,
  Play,
  RotateCcw,
  FolderPlus,
  Paperclip,
  TrendingUp,
  Pencil,
  Eye,
  Download,
  XCircle,
  Search,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { toast } from "sonner";
import { DialogSkeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { getProjectDetail, startStages, restartStage, updateStageCoefficient } from "@/services/project";
import {
  cancelProjectFile,
  fetchFileBlobWithMeta,
  fetchProjectFileBlob,
  getProjectFileList,
  getProjectFileDownloadUrl,
} from "@/services/project-file";
import { getAccessToken } from "@/lib/token";
import { getBenefitHistory } from "@/services/contract-benefit";
import type { ContractBenefitRevisionVo } from "@/types/contract-benefit";
import { ReviseBenefitDialog } from "@/components/contract-benefit/revise-benefit-dialog";
import { ResponseCode } from "@/types/api";
import type { ProjectDetailVo } from "@/types/project";
import {
  PROJECT_STATUS_MAP,
  STAGE_COMPLETED_STATUSES,
} from "@/types/project";
import type { ProjectFileVo } from "@/types/project-file";
import {
  FILE_CATEGORY_OPTIONS,
  PROJECT_FILE_STATUS_MAP,
} from "@/types/project-file";
import type { ProjectStatus, ProjectCategory } from "@/types";
import { UploadProjectFileDialog } from "@/components/project-file/upload-project-file-dialog";
import { useAuth } from "@/store/auth-context";

function triggerDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

const statusStyles: Record<ProjectStatus, string> = {
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
  未开始: "bg-slate-100 text-slate-500",
};

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-50 text-blue-600",
  B类: "bg-emerald-50 text-emerald-600",
  C类: "bg-amber-50 text-amber-600",
  D类: "bg-purple-50 text-purple-600",
  E类: "bg-rose-50 text-rose-600",
};

const browserPreviewableExtensions = new Set([
  "pdf",
  "png",
  "jpg",
  "jpeg",
  "gif",
  "bmp",
  "webp",
  "svg",
  "txt",
  "md",
  "csv",
]);
const unsupportedPreviewMessage = "暂不支持预览该格式，请点击下载后查看";

function getProjectFileDisplayName(file: ProjectFileVo): string {
  const name = file.fileName?.trim() || "项目文件";
  const extension = file.fileExtension?.replace(/^\./, "").trim();
  if (!extension) return name;

  return name.toLowerCase().endsWith(`.${extension.toLowerCase()}`)
    ? name
    : `${name}.${extension}`;
}

function isBrowserPreviewable(file: ProjectFileVo): boolean {
  const extension = file.fileExtension?.replace(/^\./, "").toLowerCase();
  return !!extension && browserPreviewableExtensions.has(extension);
}

function getFileExtensionFromName(fileName: string | null | undefined): string {
  const name = fileName?.toLowerCase() ?? "";
  const dotIndex = name.lastIndexOf(".");
  return dotIndex >= 0 ? name.slice(dotIndex + 1) : "";
}

function isBlobBrowserPreviewable(blob: Blob, fileName?: string | null): boolean {
  const extension = getFileExtensionFromName(fileName);
  if (extension && browserPreviewableExtensions.has(extension)) return true;

  const type = blob.type.toLowerCase();
  return (
    type.startsWith("image/") ||
    type.startsWith("text/") ||
    type === "application/pdf"
  );
}

function parseContractFileIds(raw: string | null | undefined): string[] {
  if (!raw) return [];
  const text = String(raw).trim();
  if (!text) return [];

  return (text.match(/\d+/g) ?? [])
    .map((value) => value.trim())
    .filter(Boolean);
}

const stageStatusLabels: Record<number, string> = {
  0: "未开始",
  1: "进行中",
  2: "待验收",
  3: "已验收",
  4: "已驳回",
  5: "待分配",
  6: "已完成",
};

const stageStatusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-500",
  1: "bg-blue-100 text-blue-600",
  2: "bg-amber-100 text-amber-600",
  3: "bg-emerald-100 text-emerald-600",
  4: "bg-rose-100 text-rose-600",
  5: "bg-purple-100 text-purple-600",
  6: "bg-emerald-100 text-emerald-600",
};

function getStatusLabel(status: number): ProjectStatus {
  return (PROJECT_STATUS_MAP[status] ?? "未开始") as ProjectStatus;
}

function getCategoryLabel(typeCode: string): ProjectCategory {
  if (typeCode.startsWith("A")) return "A类";
  if (typeCode.startsWith("B")) return "B类";
  if (typeCode.startsWith("C")) return "C类";
  if (typeCode.startsWith("D")) return "D类";
  if (typeCode.startsWith("E")) return "E类";
  return "A类";
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.slice(0, 10);
}

function formatAmount(amount: number): string {
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(1)}万`;
  return `¥${amount.toLocaleString()}`;
}

interface ProjectDetailDialogProps {
  projectId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStageChange?: () => void;
}

export function ProjectDetailDialog({
  projectId,
  open,
  onOpenChange,
  onStageChange,
}: ProjectDetailDialogProps) {
  const [detail, setDetail] = useState<ProjectDetailVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [stageActionLoading, setStageActionLoading] = useState(false);
  const [editingCoeffStageId, setEditingCoeffStageId] = useState<string | null>(null);
  const [editingCoeffValue, setEditingCoeffValue] = useState<string>("1");

  const reloadDetail = async () => {
    if (!projectId) return;
    try {
      const response = await getProjectDetail(projectId);
      if (response.code === ResponseCode.SUCCESS && response.data) {
        setDetail(response.data);
      }
    } catch {
      // 静默
    }
  };

  // 启动阶段
  const handleStartStages = async (stageIds: string[]) => {
    setStageActionLoading(true);
    try {
      const res = await startStages(stageIds);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("阶段启动成功");
        await reloadDetail();
        onStageChange?.();
      }
      // 错误 toast 由 request.ts 统一处理
    } catch {
      /* 网络错误由 request.ts 提示 */
    } finally {
      setStageActionLoading(false);
    }
  };

  // 重启已驳回阶段
  const handleRestartStage = async (stageId: string) => {
    setStageActionLoading(true);
    try {
      const res = await restartStage(stageId);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("阶段已重新启动");
        await reloadDetail();
        onStageChange?.();
      }
    } catch {
      /* 网络错误由 request.ts 提示 */
    } finally {
      setStageActionLoading(false);
    }
  };

  // 保存阶段系数
  const handleSaveCoefficient = async (stageId: string) => {
    const coeff = Number(editingCoeffValue);
    if (isNaN(coeff) || coeff <= 0) { toast.error("系数需大于0"); return; }
    try {
      const res = await updateStageCoefficient(stageId, coeff);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("系数已更新");
        setEditingCoeffStageId(null);
        await reloadDetail();
        onStageChange?.();
      } else { toast.error(res.msg || "更新失败"); }
    } catch { toast.error("更新失败"); }
  };

  useEffect(() => {
    if (!open || !projectId) {
      setDetail(null);
      setError("");
      return;
    }

    let cancelled = false;

    async function fetchDetail() {
      setLoading(true);
      setError("");
      try {
        const response = await getProjectDetail(projectId!);
        if (cancelled) return;
        if (response.code === ResponseCode.SUCCESS && response.data) {
          setDetail(response.data);
        } else {
          setError(response.msg || "获取项目详情失败");
        }
      } catch {
        if (!cancelled) setError("网络异常，请稍后重试");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    fetchDetail();
    return () => {
      cancelled = true;
    };
  }, [open, projectId]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        {/* 加载态 */}
        {loading && (
          <>
            <DialogHeader>
              <DialogTitle>项目详情</DialogTitle>
            </DialogHeader>
            <DialogSkeleton />
          </>
        )}

        {/* 错误态 */}
        {!loading && error && (
          <>
            <DialogHeader>
              <DialogTitle>项目详情</DialogTitle>
            </DialogHeader>
            <div className="py-12 text-center">
              <p className="text-sm text-rose-500">{error}</p>
            </div>
          </>
        )}

        {/* 详情内容 */}
        {!loading && detail && (
          <ProjectDetailContent
            detail={detail}
            stageActionLoading={stageActionLoading}
            onStartStages={handleStartStages}
            onRestartStage={handleRestartStage}
            editingCoeffStageId={editingCoeffStageId}
            editingCoeffValue={editingCoeffValue}
            onStartEditCoeff={(stageId, val) => { setEditingCoeffStageId(stageId); setEditingCoeffValue(String(val)); }}
            onChangeCoeffValue={setEditingCoeffValue}
            onSaveCoeff={handleSaveCoefficient}
            onCancelEditCoeff={() => setEditingCoeffStageId(null)}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

/** 项目文件 section：列出归档文件 + 顶部上传按钮（分页 + 搜索 + 分类筛选）*/
function ProjectFilesSection({
  projectId,
  projectName,
}: {
  projectId: string;
  projectName: string;
}) {
  const SECTION_PAGE_SIZE = 5;
  const [items, setItems] = useState<ProjectFileVo[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const { user } = useAuth();

  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [fileCategory, setFileCategory] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [total, setTotal] = useState(0);
  const totalPages = Math.max(1, Math.ceil(total / SECTION_PAGE_SIZE));

  // 搜索防抖
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  // 切分类重置页码
  useEffect(() => {
    setCurrentPage(1);
  }, [fileCategory]);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const res = await getProjectFileList({
        projectId,
        keyword: debouncedKeyword || undefined,
        fileCategory: fileCategory || undefined,
        current: currentPage,
        pageSize: SECTION_PAGE_SIZE,
      }, signal);
      if (!signal?.aborted && res.code === ResponseCode.SUCCESS && res.data) {
        setItems(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      }
    } catch (err) {
      if (isAbortError(err)) return;
      setItems([]);
      setTotal(0);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [projectId, debouncedKeyword, fileCategory, currentPage]);

  useEffect(() => {
    const controller = new AbortController();
    fetchData(controller.signal);
    return () => controller.abort();
  }, [fetchData]);

  return (
    <section>
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
          <Paperclip className="w-4 h-4 text-slate-400" /> 项目文件
          <span className="text-xs font-normal text-slate-400 ml-1">
            共 {total} 项
          </span>
        </h4>
        <Button
          className="h-7 text-xs bg-blue-600 hover:bg-blue-700 text-white"
          onClick={() => setUploadOpen(true)}
        >
          <FolderPlus className="w-3 h-3 mr-1" /> 上传文件
        </Button>
      </div>

      {/* 搜索 + 分类筛选 */}
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <div className="relative flex-1 min-w-[180px]">
          <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="按分类 / 说明搜索..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full pl-8 pr-3 py-1.5 rounded-md border border-slate-200 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <select
          value={fileCategory}
          onChange={(e) => setFileCategory(e.target.value)}
          className="px-2 py-1.5 rounded-md border border-slate-200 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
        >
          <option value="">全部分类</option>
          {FILE_CATEGORY_OPTIONS.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
      </div>

      {loading ? (
        <p className="text-xs text-slate-400 text-center py-4">加载中...</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 text-center py-4 bg-slate-50 rounded-lg">
          暂无文件，点击右上角上传文件开始
        </p>
      ) : (
        <>
          <div className="space-y-1.5">
            {items.map((f) => (
              <ProjectFileRow
                key={f.projectFileId}
                file={f}
                currentUserId={user?.userId}
                onCancelled={() => fetchData()}
              />
            ))}
          </div>

          {/* 分页 */}
          {total > SECTION_PAGE_SIZE && (
            <div className="flex items-center justify-between text-xs text-slate-500 mt-3">
              <span>共 {total} 条</span>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  className="h-6 px-2"
                  disabled={currentPage <= 1}
                  onClick={() => setCurrentPage((p) => p - 1)}
                >
                  <ChevronLeft className="w-3 h-3" />
                </Button>
                <span>{currentPage} / {totalPages}</span>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-6 px-2"
                  disabled={currentPage >= totalPages}
                  onClick={() => setCurrentPage((p) => p + 1)}
                >
                  <ChevronRight className="w-3 h-3" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <UploadProjectFileDialog
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        lockedProjectId={projectId}
        lockedProjectName={projectName}
        onSuccess={() => fetchData()}
      />
    </section>
  );
}

function ProjectFileRow({
  file,
  currentUserId,
  onCancelled,
}: {
  file: ProjectFileVo;
  currentUserId?: string | number | null;
  onCancelled: () => void;
}) {
  const [action, setAction] = useState<"preview" | "download" | "cancel" | null>(null);
  const [downloadProgress, setDownloadProgress] = useState<number | null>(null);
  const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const statusStyle =
    file.approvalStatus === 2
      ? "bg-emerald-100 text-emerald-600"
      : file.approvalStatus === 3
        ? "bg-rose-100 text-rose-600"
        : file.approvalStatus === 1
          ? "bg-amber-100 text-amber-600"
          : "bg-slate-100 text-slate-500";
  const fileName = getProjectFileDisplayName(file);
  const canCancel =
    file.approvalStatus === 1 &&
    currentUserId != null &&
    file.uploadUserId != null &&
    String(file.uploadUserId) === String(currentUserId);

  const handlePreview = async () => {
    if (!file.fileId) {
      toast.error("文件不存在");
      return;
    }

    // 有缩略图的图片直接用缩略图 URL 打开（秒开）
    if (file.thumbnailUrl) {
      const url = file.thumbnailUrl.startsWith("http") ? file.thumbnailUrl : `${BASE_URL}${file.thumbnailUrl}`;
      window.open(url, "_blank");
      return;
    }

    if (!isBrowserPreviewable(file)) {
      toast.info(unsupportedPreviewMessage);
      return;
    }

    const previewWindow = window.open("about:blank", "_blank");
    if (!previewWindow) {
      toast.error("浏览器拦截了预览窗口，请允许弹窗后重试");
      return;
    }
    previewWindow.opener = null;
    previewWindow.document.title = fileName;
    previewWindow.document.body.textContent = "文件加载中...";

    setAction("preview");
    try {
      const blob = await fetchProjectFileBlob(file.fileId);
      const url = URL.createObjectURL(blob);
      previewWindow.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      previewWindow.close();
      toast.error(err instanceof Error ? err.message : "文件预览失败");
    } finally {
      setAction(null);
    }
  };

  const handleDownload = async () => {
    if (!file.fileId) {
      toast.error("文件不存在");
      return;
    }

    setAction("download");
    setDownloadProgress(0);
    try {
      const token = getAccessToken();
      const url = getProjectFileDownloadUrl(file.fileId, token);
      const response = await fetch(url, { headers: token ? { token } : {} });
      if (!response.ok) throw new Error("文件下载失败");

      const contentLength = response.headers.get("content-length");
      const total = contentLength ? parseInt(contentLength, 10) : 0;

      if (!response.body) {
        const blob = await response.blob();
        triggerDownload(blob, fileName);
      } else {
        const reader = response.body.getReader();
        const chunks: Uint8Array[] = [];
        let received = 0;
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          chunks.push(value);
          received += value.length;
          if (total > 0) setDownloadProgress(Math.round((received / total) * 100));
        }
        const blob = new Blob(chunks as BlobPart[]);
        triggerDownload(blob, fileName);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "文件下载失败");
    } finally {
      setAction(null);
      setDownloadProgress(null);
    }
  };

  const handleCancel = async () => {
    if (!window.confirm("确定撤销这个项目文件吗？撤销后该文件将从项目文件列表中移除。")) {
      return;
    }

    setAction("cancel");
    try {
      const res = await cancelProjectFile(file.projectFileId);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("已撤销");
        onCancelled();
      } else {
        toast.error(res.msg || "撤销失败");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "撤销失败");
    } finally {
      setAction(null);
    }
  };

  return (
    <div className="flex items-center gap-3 py-2 px-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors">
      <FileText className="w-4 h-4 text-blue-500 shrink-0" />
      <button
        type="button"
        className="flex-1 min-w-0 text-left"
        onClick={handlePreview}
        title="预览文件"
        disabled={action !== null}
      >
        <div className="flex items-center gap-2">
          <p className="text-sm text-slate-700 font-medium truncate">
            {file.fileName ?? "(未命名)"}
          </p>
          {file.fileCategory && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-white text-slate-500 border border-slate-200">
              {file.fileCategory}
            </span>
          )}
        </div>
        <p className="text-xs text-slate-400 truncate">
          {file.uploadUserName ?? "-"}
          {file.stageName && <> · {file.stageName}</>}
          {file.createdTime && <> · {file.createdTime.replace("T", " ").slice(0, 16)}</>}
        </p>
        {downloadProgress !== null && (
          <div className="mt-1">
            <div className="flex items-center justify-between text-xs text-slate-500 mb-0.5">
              <span>下载中...</span>
              <span>{downloadProgress}%</span>
            </div>
            <div className="w-full bg-slate-200 rounded-full h-1">
              <div className="bg-blue-500 h-1 rounded-full transition-all duration-300" style={{ width: `${downloadProgress}%` }} />
            </div>
          </div>
        )}
      </button>
      <span className={cn("text-xs px-2 py-0.5 rounded-full", statusStyle)}>
        {PROJECT_FILE_STATUS_MAP[file.approvalStatus] ?? "-"}
      </span>
      <div className="flex items-center gap-1 shrink-0">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-blue-600"
          title="预览文件"
          onClick={handlePreview}
          disabled={action !== null}
        >
          {action === "preview" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-blue-600"
          title="下载文件"
          onClick={handleDownload}
          disabled={action !== null}
        >
          {action === "download" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
        </Button>
        {canCancel && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-slate-400 hover:text-rose-600"
            title="撤销上传"
            onClick={handleCancel}
            disabled={action !== null}
          >
            {action === "cancel" ? <Loader2 className="w-4 h-4 animate-spin" /> : <XCircle className="w-4 h-4" />}
          </Button>
        )}
      </div>
    </div>
  );
}

function ContractAttachmentRow({
  fileId,
  label,
}: {
  fileId: string;
  label: string;
}) {
  const [action, setAction] = useState<"preview" | "download" | null>(null);
  const fallbackName = `${label}-${fileId}`;

  const handlePreview = async () => {
    const previewWindow = window.open("about:blank", "_blank");
    if (!previewWindow) {
      toast.error("浏览器拦截了预览窗口，请允许弹窗后重试");
      return;
    }
    previewWindow.opener = null;
    previewWindow.document.title = fallbackName;
    previewWindow.document.body.textContent = "文件加载中...";

    setAction("preview");
    try {
      const { blob, fileName } = await fetchFileBlobWithMeta(fileId);
      const displayName = fileName || fallbackName;
      if (!isBlobBrowserPreviewable(blob, displayName)) {
        previewWindow.close();
        toast.info(unsupportedPreviewMessage);
        return;
      }
      const url = URL.createObjectURL(blob);
      previewWindow.document.title = displayName;
      previewWindow.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      previewWindow.close();
      toast.error(err instanceof Error ? err.message : "文件预览失败");
    } finally {
      setAction(null);
    }
  };

  const handleDownload = async () => {
    setAction("download");
    try {
      const { blob, fileName } = await fetchFileBlobWithMeta(fileId);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = fileName || fallbackName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "文件下载失败");
    } finally {
      setAction(null);
    }
  };

  return (
    <div className="flex items-center gap-3 py-2 px-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors">
      <FileText className="w-4 h-4 text-blue-500 shrink-0" />
      <button
        type="button"
        className="flex-1 min-w-0 text-left"
        onClick={handlePreview}
        title="预览合同附件"
        disabled={action !== null}
      >
        <p className="text-sm text-slate-700 font-medium truncate">{label}</p>
        <p className="text-xs text-slate-400 truncate">附件 ID: {fileId}</p>
      </button>
      <div className="flex items-center gap-1 shrink-0">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-blue-600"
          title="预览合同附件"
          onClick={handlePreview}
          disabled={action !== null}
        >
          {action === "preview" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-blue-600"
          title="下载合同附件"
          onClick={handleDownload}
          disabled={action !== null}
        >
          {action === "download" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
        </Button>
      </div>
    </div>
  );
}

/** 项目经理角色ID（对应 sys_role.role_id = 101） */
const ROLE_PROJECT_MANAGER = 101;

/** 详情内容 */
function ProjectDetailContent({
  detail,
  stageActionLoading,
  onStartStages,
  onRestartStage,
  editingCoeffStageId,
  editingCoeffValue,
  onStartEditCoeff,
  onChangeCoeffValue,
  onSaveCoeff,
  onCancelEditCoeff,
}: {
  detail: ProjectDetailVo;
  stageActionLoading: boolean;
  onStartStages: (stageIds: string[]) => void;
  onRestartStage: (stageId: string) => void;
  editingCoeffStageId: string | null;
  editingCoeffValue: string;
  onStartEditCoeff: (stageId: string, currentValue: number) => void;
  onChangeCoeffValue: (value: string) => void;
  onSaveCoeff: (stageId: string) => void;
  onCancelEditCoeff: () => void;
}) {
  const { user } = useAuth();

  /** 判断当前用户是否为该项目的项目经理 */
  const isManager = (() => {
    if (!user || !detail.projectMemberList) return false;
    return detail.projectMemberList.some(
      (m) => String(m.userId) === String(user.userId) && Number(m.projectRoleId) === ROLE_PROJECT_MANAGER
    );
  })();

  const statusLabel = getStatusLabel(detail.projectStatus);
  const category = getCategoryLabel(detail.projectType?.projectTypeCode ?? "");
  const typeName = detail.projectType?.projectTypeName ?? "";
  const stages = detail.projectStages ?? [];
  const completedStages = stages.filter((s) =>
    STAGE_COMPLETED_STATUSES.includes(s.stageStatus)
  ).length;
  const members = (detail.projectMemberList ?? []).filter((m) => m.realName);
  const contract = detail.contract;
  const contractAttachments = [
    ...(contract?.contractFile
      ? [{ fileId: String(contract.contractFile), label: "合同主文件" }]
      : []),
    ...parseContractFileIds(contract?.contractOtherFiles).map((fileId, index) => ({
      fileId,
      label: `合同附件 ${index + 1}`,
    })),
  ].filter(
    (item, index, list) =>
      item.fileId && list.findIndex((next) => next.fileId === item.fileId) === index,
  );
  const startDate = contract?.preStartDate ?? detail.preStartTime;
  const endDate = contract?.preEndDate ?? detail.preEndTime;

  return (
    <>
      <DialogHeader>
        <div className="flex items-center gap-2 mb-1">
          <span
            className={cn(
              "text-xs px-2 py-0.5 rounded-md font-medium",
              categoryStyles[category]
            )}
          >
            {category}
            {typeName ? ` · ${typeName}` : ""}
          </span>
          <Badge
            variant="secondary"
            className={cn("text-xs", statusStyles[statusLabel])}
          >
            {statusLabel}
          </Badge>
        </div>
        <DialogTitle>{detail.projectName}</DialogTitle>
        <DialogDescription>{detail.projectCode}</DialogDescription>
      </DialogHeader>

      <div className="mt-4 space-y-5">
        {/* 基本信息 */}
        <section>
          <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
            <Info className="w-4 h-4 text-slate-400" /> 基本信息
          </h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <InfoItem label="项目编码" value={detail.projectCode} />
            <InfoItem label="项目状态" value={statusLabel} />
            <InfoItem label="开始日期" value={formatDate(startDate)} />
            <InfoItem label="结束日期" value={formatDate(endDate)} />
          </div>
        </section>

        {/* 合同信息 */}
        {contract && (
          <section>
            <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
              <Banknote className="w-4 h-4 text-slate-400" /> 合同信息
            </h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <InfoItem
                label="合同金额"
                value={formatAmount(contract.contractAmount)}
                highlight
              />
              <InfoItem
                label="合同类型"
                value={
                  contract.contractType === 0 ? "基本收费" : "基本+效益"
                }
              />
              {contract.baseAmount > 0 && (
                <InfoItem
                  label="基本金额"
                  value={formatAmount(contract.baseAmount)}
                />
              )}
              {contract.signingDate && (
                <InfoItem
                  label="签订日期"
                  value={formatDate(contract.signingDate)}
                />
              )}
            </div>
          </section>
        )}

        {/* v0.4 效益管理（仅基本+效益类型显示） */}
        {contract && contract.contractType === 1 && (
          <ContractBenefitSection contract={contract} isManager={isManager} />
        )}

        {/* 阶段进度 */}
        {stages.length > 0 && (() => {
          const notStartedIds = stages
            .filter((s) => s.stageStatus === 0)
            .map((s) => s.projectStageId);

          return (
            <section>
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                  <Layers className="w-4 h-4 text-slate-400" /> 阶段进度
                  <span className="text-xs font-normal text-slate-400 ml-1">
                    {completedStages}/{stages.length}
                  </span>
                </h4>
                {isManager && notStartedIds.length > 0 && (
                  <Button
                    className="h-7 text-xs bg-blue-600 hover:bg-blue-700 text-white"
                    disabled={stageActionLoading}
                    onClick={() => onStartStages(notStartedIds)}
                  >
                    {stageActionLoading ? (
                      <Loader2 className="w-3 h-3 animate-spin mr-1" />
                    ) : (
                      <Play className="w-3 h-3 mr-1" />
                    )}
                    全部启动
                  </Button>
                )}
              </div>
              <div className="flex gap-1 mb-3">
                {stages.map((s, i) => (
                  <div
                    key={i}
                    className={cn(
                      "h-2 flex-1 rounded-full",
                      s.stageStatus === 6 || s.stageStatus === 3
                        ? "bg-emerald-500"
                        : s.stageStatus === 1 && (s.completionRatio ?? 0) > 0
                        ? "bg-amber-400"
                        : s.stageStatus === 1
                        ? "bg-blue-500"
                        : s.stageStatus === 2
                        ? "bg-amber-500"
                        : s.stageStatus === 4
                        ? "bg-rose-500"
                        : "bg-slate-200"
                    )}
                  />
                ))}
              </div>
              <div className="space-y-2">
                {stages.map((stage, idx) => (
                  <div
                    key={stage.projectStageId ?? idx}
                    className="flex items-center justify-between py-2 px-3 bg-slate-50 rounded-lg"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-slate-400 w-5">
                        {idx + 1}
                      </span>
                      <span className="text-sm text-slate-700">
                        {stage.stageName}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      {stage.stageOutput > 0 && (
                        <span className="text-xs text-slate-400">
                          产值比 {stage.stageOutput}%
                        </span>
                      )}
                      {(stage.completionRatio ?? (stage.stageStatus === 6 ? 100 : 0)) > 0
                        && (stage.completionRatio ?? (stage.stageStatus === 6 ? 100 : 0)) < 100 && (
                        <span className="text-xs text-amber-600">
                          完成 {stage.completionRatio ?? (stage.stageStatus === 6 ? 100 : 0)}%
                        </span>
                      )}
                      {/* 系数：仅项目经理可编辑 */}
                      {editingCoeffStageId === stage.projectStageId ? (
                        <span className="inline-flex items-center gap-1">
                          <input
                            type="number"
                            min={0.01}
                            max={99.99}
                            step={0.01}
                            value={editingCoeffValue}
                            onChange={(e) => onChangeCoeffValue(e.target.value)}
                            className="w-14 px-1 py-0.5 text-xs border border-blue-300 rounded bg-white text-center"
                            autoFocus
                            onKeyDown={(e) => { if (e.key === 'Enter') onSaveCoeff(stage.projectStageId); if (e.key === 'Escape') onCancelEditCoeff(); }}
                          />
                          <button onClick={() => onSaveCoeff(stage.projectStageId)} className="text-xs text-blue-600 hover:underline">✓</button>
                          <button onClick={onCancelEditCoeff} className="text-xs text-slate-400 hover:underline">✕</button>
                        </span>
                      ) : isManager ? (
                        <button
                          onClick={() => onStartEditCoeff(stage.projectStageId, stage.coefficient ?? 1)}
                          className="text-xs text-blue-600 hover:text-blue-800 hover:underline cursor-pointer"
                          title="点击编辑系数"
                        >
                          系数 ×{stage.coefficient ?? 1}
                        </button>
                      ) : (
                        <span className="text-xs text-slate-500">
                          系数 ×{stage.coefficient ?? 1}
                        </span>
                      )}
                      <span
                        className={cn(
                          "text-xs px-1.5 py-0.5 rounded",
                          stage.stageStatus === 1 && (stage.completionRatio ?? 0) > 0
                            ? "bg-amber-100 text-amber-600"
                            : stageStatusStyles[stage.stageStatus] ??
                            "bg-slate-100 text-slate-500"
                        )}
                      >
                        {stage.stageStatus === 1 && (stage.completionRatio ?? 0) > 0
                          ? `部分完成 ${stage.completionRatio}%`
                          : stageStatusLabels[stage.stageStatus] ?? "未知"}
                      </span>
                      {/* 未开始 → 可单独启动（仅项目经理） */}
                      {isManager && stage.stageStatus === 0 && (
                        <button
                          disabled={stageActionLoading}
                          onClick={() => onStartStages([stage.projectStageId])}
                          className="text-xs text-blue-600 hover:text-blue-700 font-medium disabled:opacity-50"
                        >
                          启动
                        </button>
                      )}
                      {/* 已驳回 → 可重启（仅项目经理） */}
                      {isManager && stage.stageStatus === 4 && (
                        <button
                          disabled={stageActionLoading}
                          onClick={() => onRestartStage(stage.projectStageId)}
                          className="text-xs text-amber-600 hover:text-amber-700 font-medium disabled:opacity-50 flex items-center gap-0.5"
                        >
                          <RotateCcw className="w-3 h-3" /> 重启
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          );
        })()}

        {/* 项目成员 */}
        {members.length > 0 && (
          <section>
            <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
              <Users className="w-4 h-4 text-slate-400" /> 项目成员
              <span className="text-xs font-normal text-slate-400 ml-1">
                {members.length}人
              </span>
            </h4>
            <div className="flex flex-wrap gap-2">
              {members.map((member) => (
                <div
                  key={member.userId}
                  className="flex items-center gap-2 py-1.5 px-3 bg-slate-50 rounded-lg"
                >
                  <div className="w-6 h-6 rounded-full bg-blue-100 flex items-center justify-center text-xs font-medium text-blue-600">
                    {member.realName[0]}
                  </div>
                  <span className="text-sm text-slate-700">
                    {member.realName}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 合同附件 */}
        {contractAttachments.length > 0 && (
          <section>
            <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
              <FileText className="w-4 h-4 text-slate-400" /> 合同附件
            </h4>
            <div className="space-y-1.5">
              {contractAttachments.map((file) => (
                <ContractAttachmentRow
                  key={file.fileId}
                  fileId={file.fileId}
                  label={file.label}
                />
              ))}
            </div>
          </section>
        )}

        {/* 项目文件（审批归档） */}
        <ProjectFilesSection
          projectId={detail.projectId}
          projectName={detail.projectName}
        />
      </div>
    </>
  );
}

/** v0.4：合同效益管理 Section（含修正历史 + 修正按钮） */
function ContractBenefitSection({
  contract,
  isManager,
}: {
  contract: NonNullable<ProjectDetailVo["contract"]>;
  isManager: boolean;
}) {
  const [history, setHistory] = useState<ContractBenefitRevisionVo[]>([]);
  const [reviseOpen, setReviseOpen] = useState(false);

  const fetchHistory = useCallback(async () => {
    if (!contract.contractId) return;
    const res = await getBenefitHistory(contract.contractId);
    if (res.code === ResponseCode.SUCCESS) {
      setHistory(res.data ?? []);
    }
  }, [contract.contractId]);

  useEffect(() => {
    let cancelled = false;
    async function loadHistory() {
      if (!contract.contractId) return;
      const res = await getBenefitHistory(contract.contractId);
      if (!cancelled && res.code === ResponseCode.SUCCESS) {
        setHistory(res.data ?? []);
      }
    }

    void loadHistory();
    return () => {
      cancelled = true;
    };
  }, [contract.contractId]);

  const isFinal = contract.benefitStatus === 1;

  return (
    <section>
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
          <TrendingUp className="w-4 h-4 text-slate-400" /> 效益管理
          {isFinal && (
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-100 text-rose-600 font-medium">
              已结算锁定
            </span>
          )}
        </h4>
        {isManager && !isFinal && (
          <Button
            className="h-7 text-xs bg-blue-600 hover:bg-blue-700 text-white"
            onClick={() => setReviseOpen(true)}
          >
            <Pencil className="w-3 h-3 mr-1" /> 效益修正
          </Button>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
        <div className="py-2 px-3 bg-blue-50 rounded-lg">
          <p className="text-xs text-blue-500 mb-0.5">当前预计效益金额</p>
          <p className="text-base font-bold text-blue-700">
            ¥{(contract.benefitAmount ?? 0).toLocaleString()}
          </p>
        </div>
        <div className="py-2 px-3 bg-slate-50 rounded-lg">
          <p className="text-xs text-slate-400 mb-0.5">修正次数</p>
          <p className="text-sm font-medium text-slate-700">{history.length} 次</p>
        </div>
      </div>

      {/* 修正历史时间线 */}
      {history.length > 0 ? (
        <div className="space-y-1.5 max-h-48 overflow-y-auto">
          {history.map((h) => (
            <div
              key={h.revisionId}
              className="text-xs flex items-start gap-2 py-1.5 px-3 bg-slate-50 rounded-lg"
            >
              <div className="flex-1 min-w-0">
                <p className="text-slate-700">
                  {h.oldAmount != null ? (
                    <>
                      <span className="text-slate-500">
                        ¥{h.oldAmount.toLocaleString()}
                      </span>
                      {" → "}
                    </>
                  ) : null}
                  <span className="font-semibold">¥{h.newAmount.toLocaleString()}</span>
                  {h.deltaAmount != null && (
                    <span
                      className={cn(
                        "ml-1.5",
                        h.deltaAmount >= 0 ? "text-emerald-600" : "text-rose-500",
                      )}
                    >
                      ({h.deltaAmount >= 0 ? "+" : ""}
                      {h.deltaAmount.toLocaleString()})
                    </span>
                  )}
                  {h.isFinal === 1 && (
                    <span className="ml-1.5 text-[10px] px-1.5 py-0.5 rounded bg-rose-100 text-rose-600">
                      最终
                    </span>
                  )}
                </p>
                {h.revisionReason && (
                  <p className="text-slate-400 mt-0.5">{h.revisionReason}</p>
                )}
                <p className="text-slate-400 mt-0.5">
                  {h.operatorName ?? "-"} · {formatDate(h.createdTime)}
                </p>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-xs text-slate-400 text-center py-3 bg-slate-50 rounded-lg">
          暂无修正记录
        </p>
      )}

      <ReviseBenefitDialog
        open={reviseOpen}
        onOpenChange={setReviseOpen}
        contractId={contract.contractId}
        currentAmount={contract.benefitAmount}
        onSuccess={fetchHistory}
      />
    </section>
  );
}

/** 信息展示项 */
function InfoItem({
  label,
  value,
  highlight,
}: {
  label: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <div className="py-2 px-3 bg-slate-50 rounded-lg">
      <p className="text-xs text-slate-400 mb-0.5">{label}</p>
      <p
        className={cn(
          "text-sm font-medium",
          highlight ? "text-blue-600" : "text-slate-800"
        )}
      >
        {value}
      </p>
    </div>
  );
}
