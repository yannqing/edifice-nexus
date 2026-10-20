"use client";

import { useState } from "react";
import { Download, Eye, FileText, Loader2, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { getProjectFileDownloadUrl } from "@/services/project-file";
import { getAccessToken } from "@/lib/token";

const browserPreviewableExtensions = new Set([
  "pdf", "png", "jpg", "jpeg", "gif", "bmp", "webp", "txt", "md", "csv",
]);

const unsupportedPreviewMessage = "暂不支持预览该格式，请点击下载后查看";

export function parseFileIdList(raw: string | null | undefined): string[] {
  if (!raw) return [];
  const text = String(raw).trim();
  if (!text || text === "null" || text === "[]") return [];
  return (text.match(/\d+/g) ?? []).map((v) => v.trim()).filter(Boolean);
}

function getFileExtension(fileName: string | null | undefined): string {
  const name = fileName?.toLowerCase() ?? "";
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1) : "";
}

export function isFileNameBrowserPreviewable(fileName: string | null | undefined): boolean {
  const ext = getFileExtension(fileName);
  return !!ext && browserPreviewableExtensions.has(ext);
}

export function showUnsupportedPreviewToast() {
  toast.info(unsupportedPreviewMessage);
}

function resolveDisposition(disposition: string | null): string | null {
  if (!disposition) return null;
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) return decodeURIComponent(encoded.replace(/^"|"$/g, ""));
  const raw = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return raw ? decodeURIComponent(raw) : null;
}

// ==================== 列表组件 ====================

export function AttachmentFileList({
  fileIds,
  labelPrefix = "附件",
  thumbnailUrlMap,
}: {
  fileIds: string[];
  labelPrefix?: string;
  thumbnailUrlMap?: Record<string, string>;
}) {
  if (fileIds.length === 0) return null;
  return (
    <div className="space-y-1.5">
      {fileIds.map((fileId, index) => (
        <AttachmentFileRow
          key={`${fileId}-${index}`}
          fileId={fileId}
          fallbackName={`${labelPrefix}${index + 1}`}
          thumbnailUrl={thumbnailUrlMap?.[fileId]}
        />
      ))}
    </div>
  );
}

export function EditableAttachmentFileList({
  files,
  onRemove,
}: {
  files: Array<{ fileId: string; displayName?: string | null; thumbnailUrl?: string | null }>;
  onRemove: (fileId: string) => void;
}) {
  if (files.length === 0) return null;
  return (
    <div className="space-y-1.5">
      {files.map((file, index) => (
        <AttachmentFileRow
          key={file.fileId}
          fileId={file.fileId}
          fallbackName={file.displayName || `附件${index + 1}`}
          thumbnailUrl={file.thumbnailUrl ?? undefined}
          onRemove={() => onRemove(file.fileId)}
        />
      ))}
    </div>
  );
}

// ==================== 兼容旧接口 ====================

export function AttachmentFileActions({
  fileId,
  fileName,
  fileSize,
  disabled,
  onDisplayNameChange,
  action,
  onActionChange,
  onRemove,
}: {
  fileId: string;
  fileName?: string | null;
  fileSize?: string;
  disabled?: boolean;
  onDisplayNameChange?: (fileName: string) => void;
  action?: "preview" | "download" | null;
  onActionChange?: (action: "preview" | "download" | null) => void;
  onRemove?: () => void;
}) {
  return (
    <AttachmentFileRow
      fileId={fileId}
      fallbackName={fileName || "附件"}
      fileSize={fileSize}
      disabled={disabled}
      onRemove={onRemove}
    />
  );
}

// ==================== 行组件 ====================

function AttachmentFileRow({
  fileId,
  fallbackName,
  fileSize,
  disabled,
  onRemove,
  thumbnailUrl,
}: {
  fileId: string;
  fallbackName: string;
  fileSize?: string;
  disabled?: boolean;
  onRemove?: () => void;
  thumbnailUrl?: string;
}) {
  const [action, setAction] = useState<"preview" | "download" | null>(null);
  const [displayName, setDisplayName] = useState(fallbackName);


  const checkDownload = async (mode?: "preview") => {
    const base = getProjectFileDownloadUrl(fileId, getAccessToken());
    const url = base + (mode ? `${base.includes("?") ? "&" : "?"}mode=preview` : "");
    const response = await fetch(url, { method: "HEAD", cache: "no-store" });
    if (!response.ok || response.headers.get("content-type")?.includes("application/json"))
      throw new Error("文件不存在、登录已过期或没有访问权限");
    const fileName = resolveDisposition(response.headers.get("content-disposition"));
    if (fileName) setDisplayName(fileName);
    return { url, inline: response.headers.get("content-disposition")?.startsWith("inline") };
  };

  const handlePreview = async () => {
    const preview = window.open("about:blank", "_blank");
    if (!preview) { toast.error("浏览器拦截了预览窗口，请允许弹窗后重试"); return; }
    preview.opener = null;
    preview.document.body.textContent = "文件加载中...";
    setAction("preview");
    try {
      const result = await checkDownload("preview");
      if (!result.inline) { preview.close(); showUnsupportedPreviewToast(); return; }
      preview.location.href = result.url;
    } catch (error) {
      preview.close();
      toast.error(error instanceof Error ? error.message : "文件预览失败");
    } finally { setAction(null); }
  };

  const handleDownload = async () => {
    setAction("download");
    try {
      const { url } = await checkDownload();
      const link = document.createElement("a");
      link.href = url; link.download = displayName;
      document.body.appendChild(link); link.click(); link.remove();
    } catch (error) { toast.error(error instanceof Error ? error.message : "文件下载失败"); }
    finally { setAction(null); }
  };

  return (
    <div className="flex items-center gap-3 py-2 px-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors">
      <FileText className="w-4 h-4 text-blue-500 shrink-0" />
      <div className="flex-1 min-w-0 text-left">
        <p className="text-sm text-slate-700 font-medium truncate">{displayName}</p>
        {fileSize && <p className="text-xs text-slate-400">{(Number(fileSize) / 1024 / 1024).toFixed(1)} MB</p>}

      </div>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-7 w-7 text-slate-400 hover:text-blue-600"
        title="预览文件"
        onClick={handlePreview}
        disabled={disabled || action !== null}
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
        disabled={disabled || action !== null}
      >
        {action === "download" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
      </Button>
      {onRemove && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-red-500"
          title="移除"
          onClick={onRemove}
          disabled={disabled || action !== null}
        >
          <X className="w-4 h-4" />
        </Button>
      )}
    </div>
  );
}
