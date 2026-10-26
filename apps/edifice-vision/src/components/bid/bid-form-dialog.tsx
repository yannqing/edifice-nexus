"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Loader2, Paperclip } from "lucide-react";
import {
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ResponseCode } from "@/types/api";
import { getUserList } from "@/services/project";
import { createBid, updateBid } from "@/services/bid";
import type { UserListItem, FilesVo } from "@/types/project";
import type { BidFileVo, BidVo } from "@/types/bid";
import { BID_FILE_CATEGORIES } from "@/types/bid";

import { FileUploadField } from "@/components/file/file-upload-field";
import { UploadWindowDialog as Dialog, UploadWindowContent as DialogContent, useUploadWindowStatus, withUploadWindow } from "@/components/file/upload-workspace";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 非空时为编辑模式 */
  bid?: BidVo | null;
  onSuccess: () => void;
}

interface AttachmentDraft {
  _key: string;
  fileId?: string;
  fileName: string;
  fileSize?: string;
  fileCategory: string;
  uploading?: boolean;
}

const newAttachment = (overrides?: Partial<AttachmentDraft>): AttachmentDraft => ({
  _key: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
  fileName: "",
  fileCategory: BID_FILE_CATEGORIES[1],
  ...overrides,
});

export const BidFormDialog = withUploadWindow(BidFormDialogSession, {
  key: (props) => `bid:${props.bid?.bidId ?? "new"}`,
  title: (props) => props.bid ? `编辑投标 · ${props.bid.bidName}` : "新建投标",
});

function BidFormDialogSession({ open, onOpenChange, bid, onSuccess }: Props) {
  const editing = !!bid;

  const [users, setUsers] = useState<UserListItem[]>([]);
  const [bidName, setBidName] = useState("");
  const [bidCode, setBidCode] = useState("");
  const [ownerUserId, setOwnerUserId] = useState("");
  const [tenderAmount, setTenderAmount] = useState<string>("");
  const [clientName, setClientName] = useState("");
  const [bidDate, setBidDate] = useState("");
  const [resultDate, setResultDate] = useState("");
  const [description, setDescription] = useState("");
  const [attachments, setAttachments] = useState<AttachmentDraft[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [uploading, setUploading] = useState(false);
  useUploadWindowStatus(bidName ? `${editing ? "编辑" : "新建"}投标 · ${bidName}` : undefined, submitting);

  const reset = useCallback(() => {
    if (bid) {
      setBidName(bid.bidName ?? "");
      setBidCode(bid.bidCode ?? "");
      setOwnerUserId(bid.ownerUserId ?? "");
      setTenderAmount(bid.tenderAmount != null ? String(bid.tenderAmount) : "");
      setClientName(bid.clientName ?? "");
      setBidDate(bid.bidDate ?? "");
      setResultDate(bid.resultDate ?? "");
      setDescription(bid.description ?? "");
      setAttachments(
        (bid.files ?? []).map((f: BidFileVo) =>
          newAttachment({
            fileId: f.fileId,
            fileName: f.fileName ?? "(未命名文件)",
            fileSize: f.fileSize ?? undefined,
            fileCategory: f.fileCategory ?? BID_FILE_CATEGORIES[1],
          }),
        ),
      );
    } else {
      setBidName("");
      setBidCode("");
      setOwnerUserId("");
      setTenderAmount("");
      setClientName("");
      setBidDate("");
      setResultDate("");
      setDescription("");
      setAttachments([]);
    }
    setError("");
  }, [bid]);

  useEffect(() => {
    if (!open) return;
    reset();
    (async () => {
      const res = await getUserList();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setUsers(res.data.records ?? []);
      }
    })();
  }, [open, reset]);

  const handleSubmit = async () => {
    setError("");
    if (!bidName.trim()) return setError("请填写投标项目名称");
    if (!ownerUserId) return setError("请选择负责人");
    if (uploading || attachments.some((a) => !a.fileId)) {
      return setError("请等待附件上传完成");
    }

    const params = {
      bidName: bidName.trim(),
      bidCode: bidCode || undefined,
      ownerUserId,
      tenderAmount: tenderAmount ? Number(tenderAmount) : undefined,
      clientName: clientName || undefined,
      bidDate: bidDate || undefined,
      resultDate: resultDate || undefined,
      description: description || undefined,
      files: attachments.map((a) => ({
        fileId: a.fileId!,
        fileCategory: a.fileCategory,
      })),
    };

    setSubmitting(true);
    try {
      const res = editing
        ? await updateBid({ bidId: bid!.bidId, ...params })
        : await createBid(params);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(editing ? "已更新" : "已创建");
        onOpenChange(false);
        onSuccess();
      } else {
        setError(res.msg || "提交失败");
      }
    } catch {
      setError("网络异常，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) reset();
        onOpenChange(v);
      }}
    >
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? "编辑投标" : "新建投标"}</DialogTitle>
          <DialogDescription>
            先保存基础信息 + 附件，再到列表或详情提交审批。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                投标项目名称 <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={bidName}
                onChange={(e) => setBidName(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">投标编号</label>
              <input
                type="text"
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={bidCode}
                onChange={(e) => setBidCode(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                负责人 <span className="text-rose-500">*</span>
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={ownerUserId}
                onChange={(e) => setOwnerUserId(e.target.value)}
              >
                <option value="">请选择</option>
                {users.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.realName || u.username}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                标的金额（元）
              </label>
              <input
                type="number"
                min={0}
                step={0.01}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={tenderAmount}
                onChange={(e) => setTenderAmount(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                业主 / 甲方
              </label>
              <input
                type="text"
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={clientName}
                onChange={(e) => setClientName(e.target.value)}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <div>
                <label className="text-xs font-medium text-slate-600 mb-1 block">
                  投标日期
                </label>
                <input
                  type="date"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                  value={bidDate}
                  onChange={(e) => setBidDate(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium text-slate-600 mb-1 block">
                  结果日期
                </label>
                <input
                  type="date"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                  value={resultDate}
                  onChange={(e) => setResultDate(e.target.value)}
                />
              </div>
            </div>
          </div>

          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">说明</label>
            <textarea
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="可选：投标背景 / 竞争情况 / 风险说明"
            />
          </div>

          {/* 附件 */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-slate-600">附件</span>

            </div>
            <FileUploadField fieldKey="bid-files" purpose="bid" disabled={submitting} onBusyChange={setUploading}
              renderExtra={(file) => <label className="my-2 flex items-center justify-end gap-2 text-xs text-slate-500">
                文件分类
                <select disabled={submitting} className="rounded border border-slate-200 bg-white px-2 py-1"
                  value={attachments.find((a) => a.fileId === file.fileId)?.fileCategory ?? BID_FILE_CATEGORIES[1]}
                  onChange={(event) => setAttachments((previous) => previous.map((item) =>
                    item.fileId === file.fileId ? { ...item, fileCategory: event.target.value } : item))}>
                  {BID_FILE_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}
                </select>
              </label>}
              value={attachments.filter((a) => a.fileId).map((a): FilesVo => ({
                fileId: a.fileId!, displayName: a.fileName, fileSize: a.fileSize ?? "0",
                fileType: "", fileExtension: "", fileUrl: "", status: 1,
              }))}
              onChange={(files) => setAttachments((previous) => files.map((f) =>
                previous.find((a) => a.fileId === f.fileId) ?? newAttachment({
                  fileId: f.fileId, fileName: f.displayName, fileSize: f.fileSize,
                })))}
            />

            <p className="text-[11px] text-slate-400 mt-1 flex items-center gap-1">
              <Paperclip className="w-3 h-3" /> 支持 pdf / doc / xls / ppt 等文档格式
            </p>
          </div>

          {error && (
            <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-600 text-sm">
              {error}
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting || uploading}>
            取消
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            onClick={handleSubmit}
            disabled={submitting || uploading}
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
            {editing ? "保存" : "创建"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
