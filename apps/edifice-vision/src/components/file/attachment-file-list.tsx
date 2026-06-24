"use client";

import { useState } from "react";
import { Download, Eye, FileText, Loader2, X } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { fetchFileBlobWithMeta, getProjectFileDownloadUrl } from "@/services/project-file";
import { getAccessToken } from "@/lib/token";

const browserPreviewableExtensions = new Set([
  "pdf", "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "txt", "md", "csv",
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

function isBlobBrowserPreviewable(blob: Blob, fileName?: string | null): boolean {
  const ext = getFileExtension(fileName);
  if (ext && browserPreviewableExtensions.has(ext)) return true;
  const type = blob.type.toLowerCase();
  return type.startsWith("image/") || type.startsWith("text/") || type === "application/pdf";
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
  onDisplayNameChange,
  action,
  onActionChange,
  onRemove,
}: {
  fileId: string;
  fileName?: string | null;
  onDisplayNameChange?: (fileName: string) => void;
  action?: "preview" | "download" | null;
  onActionChange?: (action: "preview" | "download" | null) => void;
  onRemove?: () => void;
}) {
  return (
    <AttachmentFileRow
      fileId={fileId}
      fallbackName={fileName || "附件"}
      onRemove={onRemove}
    />
  );
}

// ==================== 行组件 ====================

function AttachmentFileRow({
  fileId,
  fallbackName,
  onRemove,
  thumbnailUrl,
}: {
  fileId: string;
  fallbackName: string;
  onRemove?: () => void;
  thumbnailUrl?: string;
}) {
  const [action, setAction] = useState<"preview" | "download" | null>(null);
  const [displayName, setDisplayName] = useState(fallbackName);
  const [downloadProgress, setDownloadProgress] = useState<number | null>(null);

  const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

  const handlePreview = async () => {
    // 有缩略图的图片直接用缩略图 URL 打开（秒开）
    if (thumbnailUrl) {
      const url = thumbnailUrl.startsWith("http") ? thumbnailUrl : `${BASE_URL}${thumbnailUrl}`;
      window.open(url, "_blank");
      return;
    }

    const previewWindow = window.open("about:blank", "_blank");
    if (!previewWindow) {
      toast.error("浏览器拦截了预览窗口，请允许弹窗后重试");
      return;
    }
    previewWindow.opener = null;
    previewWindow.document.title = displayName;
    previewWindow.document.body.textContent = "文件加载中...";

    setAction("preview");
    try {
      const { blob, fileName } = await fetchFileBlobWithMeta(fileId);
      const nextName = fileName || displayName;
      setDisplayName(nextName);
      if (!isBlobBrowserPreviewable(blob, nextName)) {
        previewWindow.close();
        toast.info(unsupportedPreviewMessage);
        return;
      }
      const url = URL.createObjectURL(blob);
      previewWindow.document.title = nextName;
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
    setDownloadProgress(0);
    try {
      const token = getAccessToken();
      const url = getProjectFileDownloadUrl(fileId, token);
      const response = await fetch(url, { headers: token ? { token } : {} });

      if (!response.ok) throw new Error("文件下载失败");

      const contentLength = response.headers.get("content-length");
      const total = contentLength ? parseInt(contentLength, 10) : 0;

      if (!response.body) {
        const blob = await response.blob();
        triggerDownload(blob, displayName);
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
        const disposition = response.headers.get("content-disposition");
        const fileName = disposition ? resolveDisposition(disposition) : displayName;
        if (fileName) setDisplayName(fileName);
        triggerDownload(blob, fileName || displayName);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "文件下载失败");
    } finally {
      setAction(null);
      setDownloadProgress(null);
    }
  };

  return (
    <div className="flex items-center gap-3 py-2 px-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors">
      <FileText className="w-4 h-4 text-blue-500 shrink-0" />
      <div className="flex-1 min-w-0 text-left">
        <p className="text-sm text-slate-700 font-medium truncate">{displayName}</p>
        {downloadProgress !== null && (
          <div className="mt-1">
            <div className="flex items-center justify-between text-xs text-slate-500 mb-0.5">
              <span>下载中...</span>
              <span>{downloadProgress}%</span>
            </div>
            <div className="w-full bg-slate-200 rounded-full h-1">
              <div
                className="bg-blue-500 h-1 rounded-full transition-all duration-300"
                style={{ width: `${downloadProgress}%` }}
              />
            </div>
          </div>
        )}
      </div>
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
      {onRemove && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-red-500"
          title="移除"
          onClick={onRemove}
          disabled={action !== null}
        >
          <X className="w-4 h-4" />
        </Button>
      )}
    </div>
  );
}
