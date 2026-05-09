"use client";

import { useState } from "react";
import { Download, Eye, FileText, Loader2, X } from "lucide-react";
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

export function isFileNameBrowserPreviewable(fileName: string | null | undefined): boolean {
  const extension = getFileExtension(fileName);
  return !!extension && browserPreviewableExtensions.has(extension);
}

export function showUnsupportedPreviewToast() {
  toast.info(unsupportedPreviewMessage);
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

export function EditableAttachmentFileList({
  files,
  onRemove,
}: {
  files: Array<{
    fileId: string;
    displayName?: string | null;
  }>;
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
          onRemove={() => onRemove(file.fileId)}
        />
      ))}
    </div>
  );
}

function AttachmentFileRow({
  fileId,
  fallbackName,
  onRemove,
}: {
  fileId: string;
  fallbackName: string;
  onRemove?: () => void;
}) {
  const [action, setAction] = useState<"preview" | "download" | null>(null);
  const [displayName, setDisplayName] = useState(fallbackName);

  return (
    <div className="flex items-center gap-3 py-2 px-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors">
      <FileText className="w-4 h-4 text-blue-500 shrink-0" />
      <div className="flex-1 min-w-0 text-left">
        <p className="text-sm text-slate-700 font-medium truncate">{displayName}</p>
        <p className="text-xs text-slate-400 truncate">文件 ID：{fileId}</p>
      </div>
      <AttachmentFileActions
        fileId={fileId}
        fileName={displayName}
        onDisplayNameChange={setDisplayName}
        onActionChange={setAction}
        action={action}
        onRemove={onRemove}
      />
    </div>
  );
}

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
  const [internalAction, setInternalAction] = useState<"preview" | "download" | null>(null);
  const currentAction = action ?? internalAction;
  const setCurrentAction = onActionChange ?? setInternalAction;
  const displayName = fileName || "附件";

  const handlePreview = async () => {
    const previewWindow = window.open("about:blank", "_blank");
    if (!previewWindow) {
      toast.error("浏览器拦截了预览窗口，请允许弹窗后重试");
      return;
    }
    previewWindow.opener = null;
    previewWindow.document.title = displayName;
    previewWindow.document.body.textContent = "文件加载中...";

    setCurrentAction("preview");
    try {
      const { blob, fileName } = await fetchFileBlobWithMeta(fileId);
      const nextName = fileName || displayName;
      onDisplayNameChange?.(nextName);
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
      setCurrentAction(null);
    }
  };

  const handleDownload = async () => {
    setCurrentAction("download");
    try {
      const { blob, fileName } = await fetchFileBlobWithMeta(fileId);
      const nextName = fileName || displayName;
      onDisplayNameChange?.(nextName);
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
      setCurrentAction(null);
    }
  };

  return (
    <div className="flex items-center gap-1 shrink-0">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-7 w-7 text-slate-400 hover:text-blue-600"
        title="预览文件"
        onClick={handlePreview}
        disabled={currentAction !== null}
      >
        {currentAction === "preview" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-7 w-7 text-slate-400 hover:text-blue-600"
        title="下载文件"
        onClick={handleDownload}
        disabled={currentAction !== null}
      >
        {currentAction === "download" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
      </Button>
      {onRemove && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-slate-400 hover:text-rose-600"
          title="删除文件"
          onClick={onRemove}
          disabled={currentAction !== null}
        >
          <X className="w-4 h-4" />
        </Button>
      )}
    </div>
  );
}
