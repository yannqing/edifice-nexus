"use client";

import { useState } from "react";
import { Download, Eye, FileText, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { fetchFileBlobWithMeta } from "@/services/project-file";

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

export function parseFileIdList(raw: string | null | undefined): string[] {
  if (!raw) return [];
  const text = String(raw).trim();
  if (!text || text === "null" || text === "[]") return [];

  return (text.match(/\d+/g) ?? [])
    .map((value) => value.trim())
    .filter(Boolean);
}

function getFileExtension(fileName: string | null | undefined): string {
  const name = fileName?.toLowerCase() ?? "";
  const dotIndex = name.lastIndexOf(".");
  return dotIndex >= 0 ? name.slice(dotIndex + 1) : "";
}

function isBlobBrowserPreviewable(blob: Blob, fileName?: string | null): boolean {
  const extension = getFileExtension(fileName);
  if (extension && browserPreviewableExtensions.has(extension)) return true;

  const type = blob.type.toLowerCase();
  return (
    type.startsWith("image/") ||
    type.startsWith("text/") ||
    type === "application/pdf"
  );
}

export function AttachmentFileList({
  fileIds,
  labelPrefix = "附件",
}: {
  fileIds: string[];
  labelPrefix?: string;
}) {
  if (fileIds.length === 0) return null;

  return (
    <div className="space-y-1.5">
      {fileIds.map((fileId, index) => (
        <AttachmentFileRow
          key={`${fileId}-${index}`}
          fileId={fileId}
          fallbackName={`${labelPrefix}${index + 1}`}
        />
      ))}
    </div>
  );
}

function AttachmentFileRow({
  fileId,
  fallbackName,
}: {
  fileId: string;
  fallbackName: string;
}) {
  const [action, setAction] = useState<"preview" | "download" | null>(null);
  const [displayName, setDisplayName] = useState(fallbackName);

  const handlePreview = async () => {
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
    try {
      const { blob, fileName } = await fetchFileBlobWithMeta(fileId);
      const nextName = fileName || displayName;
      setDisplayName(nextName);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = nextName;
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
        title="预览文件"
        disabled={action !== null}
      >
        <p className="text-sm text-slate-700 font-medium truncate">{displayName}</p>
        <p className="text-xs text-slate-400 truncate">文件 ID：{fileId}</p>
      </button>
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
      </div>
    </div>
  );
}
