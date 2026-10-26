"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";
import { Loader2, Download } from "lucide-react";
import {
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { importProjectsByFileId, getTemplateUrl } from "@/services/project";
import { ResponseCode } from "@/types/api";
import { getAccessToken } from "@/lib/token";

import { FileUploadField } from "@/components/file/file-upload-field";
import type { FilesVo } from "@/types/project";
import { UploadWindowDialog as Dialog, UploadWindowContent as DialogContent, useUploadWindowStatus, withUploadWindow } from "@/components/file/upload-workspace";

interface ImportProjectDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export const ImportProjectDialog = withUploadWindow(ImportProjectDialogSession, {
  key: () => "project-import",
  title: () => "导入项目",
});

function ImportProjectDialogSession({
  open,
  onOpenChange,
  onSuccess,
}: ImportProjectDialogProps) {
  const [file, setFile] = useState<FilesVo | null>(null);
  const [contractFiles, setContractFiles] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState("");
  const [isError, setIsError] = useState(false);
  const [fileBusy, setFileBusy] = useState(false);
  const [contractBusy, setContractBusy] = useState(false);
  const [completed, setCompleted] = useState(false);
  const importing = useRef(false);
  const busy = uploading || fileBusy || contractBusy;
  useUploadWindowStatus(undefined, uploading, completed);
  const contractNames = contractFiles.map((item) => item.displayName.trim());
  const duplicateNames = [...new Set(contractNames.filter((name, index) => contractNames.indexOf(name) !== index))];

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen && (busy || importing.current)) return;
    onOpenChange(nextOpen);
  };

  const handleImport = async () => {
    if (!file || busy || completed || duplicateNames.length || importing.current) return;

    importing.current = true;
    setUploading(true);
    setResult("");
    setIsError(false);

    try {
      const res = await importProjectsByFileId(file.fileId, contractFiles.map((item) => item.fileId));
      if (res.code === ResponseCode.SUCCESS) {
        const imported = res.data;
        setResult(imported.message);
        setIsError(imported.successCount === 0 || imported.failCount > 0);
        if (imported.successCount > 0) {
          setCompleted(true);
          if (imported.failCount > 0) toast.warning(imported.message);
          else toast.success(imported.message);
          onSuccess();
        } else {
          toast.error(imported.message);
        }
      } else {
        // toast 由 request.ts 统一提示
        setResult(res.msg || "导入失败");
        setIsError(true);
      }
    } catch {
      setResult("网络异常，请稍后重试");
      setIsError(true);
    } finally {
      importing.current = false;
      setUploading(false);
    }
  };

  const handleDownloadTemplate = () => {
    const url = getTemplateUrl();
    const token = getAccessToken();
    // 通过动态创建 a 标签下载，携带 token
    const link = document.createElement("a");
    link.href = token ? `${url}?token=${token}` : url;
    link.click();
  };

  return (
    <Dialog
      open={open}
      onOpenChange={handleOpenChange}
    >
      <DialogContent
        className={cn("max-w-lg", busy && "[&>button]:hidden")}
        onEscapeKeyDown={(event) => { if (busy) event.preventDefault(); }}
        onPointerDownOutside={(event) => { if (busy) event.preventDefault(); }}
      >
        <DialogHeader>
          <DialogTitle>导入项目</DialogTitle>
          <DialogDescription>
            上传 Excel 文件批量导入项目数据
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          {/* 下载模板 */}
          <div className="flex items-center justify-between p-3 bg-blue-50 rounded-xl">
            <div>
              <p className="text-sm font-medium text-blue-700">
                下载导入模板
              </p>
              <p className="text-xs text-blue-500 mt-0.5">
                请按照模板格式填写项目数据
              </p>
            </div>
            <Button
              variant="outline"
              className="text-blue-600 border-blue-200 hover:bg-blue-100"
              disabled={busy}
              onClick={handleDownloadTemplate}
            >
              <Download className="w-4 h-4 mr-1" /> 下载模板
            </Button>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium">Excel 项目文件</p>
            <FileUploadField fieldKey="import-workbook" purpose="import" maxFiles={1} accept=".xlsx,.xls" value={file ? [file] : []}
              disabled={busy} onBusyChange={setFileBusy}
              onChange={(files) => { setFile(files[0] ?? null); setResult(""); setCompleted(false); }} />
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium">合同主文件（选填，Excel 列：合同主文件名）</p>
            <FileUploadField fieldKey="import-contracts" purpose="contract" value={contractFiles} label="选择合同文件"
              disabled={busy} onBusyChange={setContractBusy}
              onChange={(files) => { setContractFiles(files); setResult(""); }} />
            {duplicateNames.length > 0 && <p role="alert" className="text-sm text-rose-600 break-all">
              合同文件名重复：{duplicateNames.join("、")}
            </p>}
          </div>

          {/* 导入结果 */}
          {result && (
            <div
              className={cn(
                "p-3 rounded-xl text-sm break-words",
                isError
                  ? "bg-rose-50 border border-rose-200 text-rose-600"
                  : "bg-emerald-50 border border-emerald-200 text-emerald-600"
              )}
            >
              {result}
            </div>
          )}
        </div>

        {/* 操作按钮 */}
        <div className="flex justify-end gap-2 pt-4 border-t border-slate-100 mt-4">
          <Button variant="outline" disabled={busy} onClick={() => handleOpenChange(false)}>
            关闭
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            disabled={!file || busy || completed || duplicateNames.length > 0}
            onClick={handleImport}
          >
            {uploading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
                导入中...
              </>
            ) : (
              completed ? "已导入" : "开始导入"
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
