"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { FileText, Loader2, Paperclip, Plus, Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ResponseCode } from "@/types/api";
import { getUserList, uploadDocument } from "@/services/project";
import { createBid, updateBid } from "@/services/bid";
import type { UserListItem, FilesVo } from "@/types/project";
import type { BidFileVo, BidVo } from "@/types/bid";
import { BID_FILE_CATEGORIES } from "@/types/bid";

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

export function BidFormDialog({ open, onOpenChange, bid, onSuccess }: Props) {
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
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [pendingAddKey, setPendingAddKey] = useState<string | null>(null);

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

  const handleAddAttachment = () => {
    const draft = newAttachment();
    setAttachments((prev) => [...prev, draft]);
    setPendingAddKey(draft._key);
    fileInputRef.current?.click();
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    const key = pendingAddKey;
    e.target.value = "";
    if (!file || !key) return;

    setAttachments((prev) =>
      prev.map((a) => (a._key === key ? { ...a, uploading: true, fileName: file.name } : a)),
    );
    try {
      const res = await uploadDocument(file);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        const vo: FilesVo = res.data;
        setAttachments((prev) =>
          prev.map((a) =>
            a._key === key
              ? {
                  ...a,
                  fileId: vo.fileId,
                  fileName: vo.displayName ?? file.name,
                  fileSize: String(vo.fileSize),
                  uploading: false,
                }
              : a,
          ),
        );
      } else {
        toast.error(res.msg || "上传失败");
        setAttachments((prev) => prev.filter((a) => a._key !== key));
      }
    } catch {
      toast.error("上传异常");
      setAttachments((prev) => prev.filter((a) => a._key !== key));
    } finally {
      setPendingAddKey(null);
    }
  };

  const handleSubmit = async () => {
    setError("");
    if (!bidName.trim()) return setError("请填写投标项目名称");
    if (!ownerUserId) return setError("请选择负责人");
    if (attachments.some((a) => a.uploading || !a.fileId)) {
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
                投标项目名称 *
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
                负责人 *
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
              <Button variant="outline" size="sm" onClick={handleAddAttachment}>
                <Plus className="w-4 h-4 mr-1" /> 添加附件
              </Button>
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.csv,.rtf"
                onChange={handleFileSelect}
              />
            </div>
            {attachments.length === 0 ? (
              <div className="text-xs text-slate-400 p-4 text-center border border-dashed border-slate-200 rounded-lg">
                尚无附件
              </div>
            ) : (
              <div className="space-y-2">
                {attachments.map((a) => (
                  <div
                    key={a._key}
                    className="flex items-center gap-2 p-2 bg-slate-50 rounded-lg"
                  >
                    <FileText className="w-4 h-4 text-blue-500 shrink-0" />
                    <span className="flex-1 text-sm truncate">
                      {a.fileName || "(待上传)"}
                      {a.uploading && (
                        <span className="ml-2 text-xs text-amber-500">上传中...</span>
                      )}
                    </span>
                    <select
                      className="text-xs px-2 py-1 rounded border border-slate-200 bg-white"
                      value={a.fileCategory}
                      onChange={(e) =>
                        setAttachments((prev) =>
                          prev.map((x) =>
                            x._key === a._key ? { ...x, fileCategory: e.target.value } : x,
                          ),
                        )
                      }
                    >
                      {BID_FILE_CATEGORIES.map((c) => (
                        <option key={c} value={c}>
                          {c}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      onClick={() =>
                        setAttachments((prev) => prev.filter((x) => x._key !== a._key))
                      }
                      className="text-slate-400 hover:text-rose-500"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
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
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            取消
          </Button>
          <Button
            className="bg-blue-600 hover:bg-blue-700 text-white"
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
            {editing ? "保存" : "创建"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

