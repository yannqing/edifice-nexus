"use client";

import { useState, useRef } from "react";
import { toast } from "sonner";
import { Upload, FileText, X, Loader2, Download } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { importProjects, getTemplateUrl } from "@/services/project";
import { ResponseCode } from "@/types/api";
import { getAccessToken } from "@/lib/token";

interface ImportProjectDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function ImportProjectDialog({
  open,
  onOpenChange,
  onSuccess,
}: ImportProjectDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState("");
  const [isError, setIsError] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleReset = () => {
    setFile(null);
    setResult("");
    setIsError(false);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (selected) {
      setFile(selected);
      setResult("");
      setIsError(false);
    }
    e.target.value = "";
  };

  const handleImport = async () => {
    if (!file) return;

    setUploading(true);
    setResult("");
    setIsError(false);

    try {
      const res = await importProjects(file);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(res.data || "导入成功");
        setResult(res.data || "导入成功");
        setIsError(false);
        onSuccess();
      } else {
        // toast 由 request.ts 统一提示
        setResult(res.msg || "导入失败");
        setIsError(true);
      }
    } catch {
      setResult("网络异常，请稍后重试");
      setIsError(true);
    } finally {
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
      onOpenChange={(v) => {
        if (!v) handleReset();
        onOpenChange(v);
      }}
    >
      <DialogContent className="max-w-lg">
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
              onClick={handleDownloadTemplate}
            >
              <Download className="w-4 h-4 mr-1" /> 下载模板
            </Button>
          </div>

          {/* 文件选择 */}
          {!file ? (
            <label className="flex flex-col items-center justify-center gap-3 p-8 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors">
              <Upload className="w-8 h-8 text-slate-300" />
              <div className="text-center">
                <p className="text-sm text-slate-600 font-medium">
                  点击选择 Excel 文件
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  支持 .xlsx、.xls 格式
                </p>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                accept=".xlsx,.xls"
                onChange={handleFileChange}
              />
            </label>
          ) : (
            <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-xl">
              <FileText className="w-8 h-8 text-emerald-500 shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-700 truncate">
                  {file.name}
                </p>
                <p className="text-xs text-slate-400">
                  {(file.size / 1024).toFixed(1)} KB
                </p>
              </div>
              <button
                onClick={handleReset}
                className="p-1.5 text-slate-400 hover:text-rose-500 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* 导入结果 */}
          {result && (
            <div
              className={cn(
                "p-3 rounded-xl text-sm",
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
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            关闭
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            disabled={!file || uploading}
            onClick={handleImport}
          >
            {uploading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
                导入中...
              </>
            ) : (
              "开始导入"
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
