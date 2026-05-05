"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ResponseCode } from "@/types/api";
import { reviseBenefit } from "@/services/contract-benefit";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  contractId: string | null;
  currentAmount: number | null | undefined;
  onSuccess: () => void;
}

export function ReviseBenefitDialog({
  open,
  onOpenChange,
  contractId,
  currentAmount,
  onSuccess,
}: Props) {
  const [newAmount, setNewAmount] = useState<string>("");
  const [reason, setReason] = useState<string>("");
  const [isFinal, setIsFinal] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setNewAmount(currentAmount != null ? String(currentAmount) : "");
    setReason("");
    setIsFinal(false);
  }, [open, currentAmount]);

  const handleSubmit = async () => {
    if (!contractId) return;
    const amt = Number(newAmount);
    if (isNaN(amt) || amt < 0) {
      toast.error("请填写有效的效益金额（≥ 0）");
      return;
    }
    setSubmitting(true);
    try {
      const res = await reviseBenefit(contractId, {
        newAmount: amt,
        revisionReason: reason || undefined,
        isFinal,
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(isFinal ? "效益已最终确认（结算锁定）" : "效益已修正");
        onOpenChange(false);
        onSuccess();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>效益预测修正</DialogTitle>
          <DialogDescription>
            修正后下次创建产值分配单时自动生效（多退少补）；勾选"最终确认"后合同效益锁定不可再改。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">
              当前预计效益金额
            </label>
            <p className="text-sm text-slate-500">
              ¥{currentAmount != null ? currentAmount.toLocaleString() : "—"}
            </p>
          </div>
          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">
              新预计效益金额（元）
            </label>
            <input
              type="number"
              min={0}
              step={0.01}
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              value={newAmount}
              onChange={(e) => setNewAmount(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs font-medium text-slate-600 mb-1 block">
              修正原因
            </label>
            <textarea
              className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="如：审减额复核后调整 / 客户反馈调整 / 项目结算最终确认"
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
            <input
              type="checkbox"
              checked={isFinal}
              onChange={(e) => setIsFinal(e.target.checked)}
            />
            最终确认（结算）— 之后合同效益不可再改
          </label>
        </div>

        <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            取消
          </Button>
          <Button
            className={isFinal ? "bg-rose-600 hover:bg-rose-700 text-white" : "bg-blue-600 hover:bg-blue-700 text-white"}
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin mr-1" />}
            {isFinal ? "最终确认并保存" : "保存修正"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
