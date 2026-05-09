"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  Briefcase,
  Clipboard,
  Eye,
  LayoutGrid,
  List,
  Plus,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ResponseCode } from "@/types/api";
import {
  deleteBid,
  getBidDetail,
  getBidList,
  updateBidStatus,
} from "@/services/bid";
import type { BidVo } from "@/types/bid";
import {
  BID_APPROVAL_STATUS_MAP,
  BID_STATUS_OPTIONS,
} from "@/types/bid";
import { BidFormDialog } from "@/components/bid/bid-form-dialog";
import { BidApprovalDialog } from "@/components/bid/bid-approval-dialog";
import { AttachmentFileActions } from "@/components/file/attachment-file-list";

type ViewMode = "list" | "board";

function formatAmount(amount: number | null | undefined): string {
  if (amount == null) return "-";
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(2)}万`;
  return `¥${amount.toLocaleString()}`;
}

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.slice(0, 10);
}

const bidStatusColorMap: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-blue-100 text-blue-600",
  2: "bg-emerald-100 text-emerald-600",
  3: "bg-rose-100 text-rose-600",
  4: "bg-amber-100 text-amber-600",
};

const approvalStatusColorMap: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-amber-100 text-amber-600",
  2: "bg-emerald-100 text-emerald-600",
  3: "bg-rose-100 text-rose-600",
};

export default function BidsPage() {
  const [view, setView] = useState<ViewMode>("list");
  const [items, setItems] = useState<BidVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [approvalFilter, setApprovalFilter] = useState<number | "">("");
  const [formOpen, setFormOpen] = useState(false);
  const [approvalOpen, setApprovalOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [current, setCurrent] = useState<BidVo | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getBidList({
        keyword: keyword || undefined,
        approvalStatus: approvalFilter === "" ? undefined : approvalFilter,
      });
      if (res.code === ResponseCode.SUCCESS) setItems(res.data ?? []);
    } finally {
      setLoading(false);
    }
  }, [keyword, approvalFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleEdit = async (id: string) => {
    const res = await getBidDetail(id);
    if (res.code === ResponseCode.SUCCESS && res.data) {
      setCurrent(res.data);
      setFormOpen(true);
    }
  };

  const handleDetail = async (id: string) => {
    const res = await getBidDetail(id);
    if (res.code === ResponseCode.SUCCESS && res.data) {
      setCurrent(res.data);
      setDetailOpen(true);
    }
  };

  const handleApproval = async (id: string) => {
    const res = await getBidDetail(id);
    if (res.code === ResponseCode.SUCCESS && res.data) {
      setCurrent(res.data);
      setApprovalOpen(true);
    }
  };

  const handleDelete = async (b: BidVo) => {
    if (!confirm(`确认删除投标「${b.bidName}」？`)) return;
    const res = await deleteBid(b.bidId);
    if (res.code === ResponseCode.SUCCESS) {
      toast.success("已删除");
      fetchData();
    }
  };

  const handleStatus = async (b: BidVo, target: number) => {
    const res = await updateBidStatus({ bidId: b.bidId, bidStatus: target });
    if (res.code === ResponseCode.SUCCESS) {
      toast.success("状态已更新");
      fetchData();
    }
  };

  const stats = BID_STATUS_OPTIONS.map((opt) => ({
    ...opt,
    count: items.filter((b) => b.bidStatus === opt.value).length,
  }));

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">投标管理</h1>
          <p className="text-slate-500 text-sm mt-1">
            投标从筹备到中标的全流程；投标文件走内部审批链。
          </p>
        </div>
        <Button
          className="bg-blue-600 hover:bg-blue-700 text-white"
          onClick={() => {
            setCurrent(null);
            setFormOpen(true);
          }}
        >
          <Plus className="w-4 h-4 mr-1" /> 新建投标
        </Button>
      </div>

      {/* 状态统计卡 */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        {stats.map((s) => (
          <div key={s.value} className="glass-card p-4 rounded-xl">
            <div className="flex items-center gap-3">
              <div
                className={cn(
                  "p-2 rounded-lg",
                  bidStatusColorMap[s.value] ?? "bg-slate-100 text-slate-600",
                )}
              >
                <Briefcase className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs text-slate-500">{s.label}</p>
                <p className="text-xl font-bold text-slate-800">{s.count}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* 工具条 */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          <button
            onClick={() => setView("list")}
            className={cn(
              "px-3 py-1.5 rounded-lg text-sm font-medium transition-all flex items-center gap-1",
              view === "list"
                ? "bg-blue-600 text-white shadow-sm"
                : "text-slate-500 hover:text-slate-700",
            )}
          >
            <List className="w-4 h-4" /> 列表
          </button>
          <button
            onClick={() => setView("board")}
            className={cn(
              "px-3 py-1.5 rounded-lg text-sm font-medium transition-all flex items-center gap-1",
              view === "board"
                ? "bg-blue-600 text-white shadow-sm"
                : "text-slate-500 hover:text-slate-700",
            )}
          >
            <LayoutGrid className="w-4 h-4" /> 看板
          </button>
        </div>

        <select
          className="px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
          value={approvalFilter}
          onChange={(e) =>
            setApprovalFilter(e.target.value === "" ? "" : Number(e.target.value))
          }
        >
          <option value="">全部审批状态</option>
          {Object.entries(BID_APPROVAL_STATUS_MAP).map(([k, label]) => (
            <option key={k} value={k}>
              {label}
            </option>
          ))}
        </select>

        <input
          type="text"
          placeholder="按名称 / 编号 / 甲方搜索..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          className="ml-auto px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white w-full sm:w-72"
        />
      </div>

      {loading ? (
        <TablePageSkeleton columns={7} rows={6} />
      ) : view === "list" ? (
        <ListView
          items={items}
          onDetail={handleDetail}
          onEdit={handleEdit}
          onApproval={handleApproval}
          onDelete={handleDelete}
          onStatus={handleStatus}
        />
      ) : (
        <BoardView
          items={items}
          onDetail={handleDetail}
          onEdit={handleEdit}
          onApproval={handleApproval}
          onStatus={handleStatus}
        />
      )}

      <BidFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        bid={current}
        onSuccess={fetchData}
      />
      <BidApprovalDialog
        open={approvalOpen}
        onOpenChange={setApprovalOpen}
        bid={current}
        onSuccess={fetchData}
      />
      <BidDetailDialog
        open={detailOpen}
        onOpenChange={setDetailOpen}
        bid={current}
      />
    </div>
  );
}

// ==================== 列表视图 ====================

function ListView({
  items,
  onDetail,
  onEdit,
  onApproval,
  onDelete,
  onStatus,
}: {
  items: BidVo[];
  onDetail: (id: string) => void;
  onEdit: (id: string) => void;
  onApproval: (id: string) => void;
  onDelete: (b: BidVo) => void;
  onStatus: (b: BidVo, target: number) => void;
}) {
  if (items.length === 0) {
    return (
      <div className="glass-card rounded-2xl py-16 text-center">
        <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <Clipboard className="w-8 h-8 text-slate-400" />
        </div>
        <h3 className="text-lg font-semibold text-slate-800 mb-1">暂无投标</h3>
        <p className="text-sm text-slate-500">点击右上角"新建投标"开始</p>
      </div>
    );
  }

  return (
    <div className="glass-card rounded-2xl overflow-x-auto overflow-y-visible">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
          <tr>
            <th className="text-left py-3 px-4 font-semibold">项目名称 / 编号</th>
            <th className="text-left py-3 px-4 font-semibold">甲方</th>
            <th className="text-right py-3 px-4 font-semibold">标的金额</th>
            <th className="text-left py-3 px-4 font-semibold">负责人</th>
            <th className="text-left py-3 px-4 font-semibold">投标 / 结果日期</th>
            <th className="text-center py-3 px-4 font-semibold">业务状态</th>
            <th className="text-center py-3 px-4 font-semibold">审批</th>
            <th className="text-right py-3 px-4 font-semibold">操作</th>
          </tr>
        </thead>
        <tbody>
          {items.map((b) => (
            <tr key={b.bidId} className="border-t border-slate-100 hover:bg-slate-50">
              <td className="py-3 px-4">
                <p className="text-slate-700 font-medium truncate max-w-xs">{b.bidName}</p>
                {b.bidCode && (
                  <p className="text-xs text-slate-400">{b.bidCode}</p>
                )}
              </td>
              <td className="py-3 px-4 text-slate-500">{b.clientName ?? "-"}</td>
              <td className="py-3 px-4 text-right font-medium text-slate-700">
                {formatAmount(b.tenderAmount)}
              </td>
              <td className="py-3 px-4 text-slate-500">{b.ownerUserName ?? "-"}</td>
              <td className="py-3 px-4 text-slate-500 text-xs">
                <p>投：{formatDate(b.bidDate)}</p>
                <p>果：{formatDate(b.resultDate)}</p>
              </td>
              <td className="py-3 px-4 text-center">
                <StatusSelect bid={b} onStatus={onStatus} />
              </td>
              <td className="py-3 px-4 text-center">
                <Badge
                  variant="secondary"
                  className={cn(
                    "text-xs",
                    approvalStatusColorMap[b.approvalStatus] ?? "",
                  )}
                >
                  {b.approvalStatusLabel}
                </Badge>
                {b.currentApproverName && (
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    待 {b.currentApproverName}
                  </p>
                )}
              </td>
              <td className="py-3 px-4 text-right">
                <div className="flex justify-end gap-2">
                  <Button size="sm" variant="outline" onClick={() => onDetail(b.bidId)}>
                    <Eye className="w-3.5 h-3.5 mr-1" /> 详情
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => onApproval(b.bidId)}>
                    审批
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => onEdit(b.bidId)}>
                    编辑
                  </Button>
                  <button
                    type="button"
                    className="p-2 text-slate-400 hover:text-rose-500"
                    onClick={() => onDelete(b)}
                    title="删除"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatusSelect({
  bid,
  onStatus,
}: {
  bid: BidVo;
  onStatus: (b: BidVo, target: number) => void;
}) {
  const [loading, setLoading] = useState(false);

  const handle = async (target: number) => {
    if (target === bid.bidStatus) return;
    setLoading(true);
    try {
      await onStatus(bid, target);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="inline-flex items-center gap-2">
      <span
        className={cn(
          "text-xs px-2 py-0.5 rounded-full font-medium whitespace-nowrap",
          bidStatusColorMap[bid.bidStatus] ?? "bg-slate-100 text-slate-600",
        )}
      >
        {bid.bidStatusLabel}
      </span>
      <select
        value={bid.bidStatus}
        disabled={loading}
        onChange={(e) => handle(Number(e.target.value))}
        className="h-7 w-24 rounded-md border border-slate-200 bg-white px-2 text-xs text-slate-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        {BID_STATUS_OPTIONS.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

// ==================== 看板视图 ====================

function BoardView({
  items,
  onDetail,
  onEdit,
  onApproval,
  onStatus,
}: {
  items: BidVo[];
  onDetail: (id: string) => void;
  onEdit: (id: string) => void;
  onApproval: (id: string) => void;
  onStatus: (b: BidVo, target: number) => void;
}) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {BID_STATUS_OPTIONS.map((col) => {
        const colItems = items.filter((b) => b.bidStatus === col.value);
        return (
          <div
            key={col.value}
            className="bg-slate-50/60 rounded-xl p-3 border border-slate-100 min-h-[400px]"
          >
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <span
                  className={cn(
                    "text-xs font-semibold px-2 py-0.5 rounded-full",
                    bidStatusColorMap[col.value] ?? "",
                  )}
                >
                  {col.label}
                </span>
                <span className="text-xs text-slate-400">{colItems.length}</span>
              </div>
            </div>
            <div className="space-y-2">
              {colItems.length === 0 ? (
                <p className="text-xs text-slate-300 text-center pt-8">暂无</p>
              ) : (
                colItems.map((b) => (
                  <BoardCard
                    key={b.bidId}
                    bid={b}
                    onDetail={onDetail}
                    onEdit={onEdit}
                    onApproval={onApproval}
                    onStatus={onStatus}
                  />
                ))
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function BoardCard({
  bid,
  onDetail,
  onEdit,
  onApproval,
  onStatus,
}: {
  bid: BidVo;
  onDetail: (id: string) => void;
  onEdit: (id: string) => void;
  onApproval: (id: string) => void;
  onStatus: (b: BidVo, target: number) => void;
}) {
  return (
    <div className="bg-white rounded-lg p-3 shadow-sm border border-slate-100 hover:shadow transition-shadow space-y-2">
      <div className="flex justify-between items-start gap-2">
        <p
          className="text-sm font-medium text-slate-700 truncate cursor-pointer hover:text-blue-600"
          onClick={() => onDetail(bid.bidId)}
          title={bid.bidName}
        >
          {bid.bidName}
        </p>
        <Badge
          variant="secondary"
          className={cn("text-[10px]", approvalStatusColorMap[bid.approvalStatus] ?? "")}
        >
          {bid.approvalStatusLabel}
        </Badge>
      </div>
      {bid.clientName && (
        <p className="text-xs text-slate-400 truncate">{bid.clientName}</p>
      )}
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>{bid.ownerUserName ?? "-"}</span>
        <span className="font-semibold text-slate-700">{formatAmount(bid.tenderAmount)}</span>
      </div>
      <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-slate-50">
        <StatusSelect bid={bid} onStatus={onStatus} />
        <button
          className="text-xs text-slate-500 hover:text-blue-600"
          onClick={() => onDetail(bid.bidId)}
        >
          详情
        </button>
        <button
          className="text-xs text-slate-500 hover:text-blue-600"
          onClick={() => onApproval(bid.bidId)}
        >
          审批
        </button>
      </div>
    </div>
  );
}

function BidDetailDialog({
  open,
  onOpenChange,
  bid,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  bid: BidVo | null;
}) {
  if (!bid) return null;
  const files = bid.files ?? [];
  const records = bid.approvalChain ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex flex-wrap items-center gap-2">
            <DialogTitle>投标详情</DialogTitle>
            <Badge
              variant="secondary"
              className={cn("text-xs", bidStatusColorMap[bid.bidStatus] ?? "")}
            >
              {bid.bidStatusLabel}
            </Badge>
            <Badge
              variant="secondary"
              className={cn("text-xs", approvalStatusColorMap[bid.approvalStatus] ?? "")}
            >
              {bid.approvalStatusLabel}
            </Badge>
          </div>
          <DialogDescription>{bid.bidCode || bid.bidName}</DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <DetailItem label="投标名称" value={bid.bidName} />
            <DetailItem label="甲方" value={bid.clientName || "-"} />
            <DetailItem label="负责人" value={bid.ownerUserName || "-"} />
            <DetailItem label="标的金额" value={formatAmount(bid.tenderAmount)} />
            <DetailItem label="投标日期" value={formatDate(bid.bidDate)} />
            <DetailItem label="结果日期" value={formatDate(bid.resultDate)} />
          </div>

          {bid.description && (
            <section>
              <p className="text-xs text-slate-400 mb-2">说明</p>
              <div className="p-4 bg-slate-50 rounded-xl text-sm text-slate-600 leading-relaxed">
                {bid.description}
              </div>
            </section>
          )}

          {files.length > 0 && (
            <section>
              <p className="text-xs text-slate-400 mb-2">附件</p>
              <div className="space-y-1.5">
                {files.map((file, index) => (
                  <div
                    key={file.bidFileId}
                    className="flex items-center gap-3 py-2 px-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors"
                  >
                    <Clipboard className="w-4 h-4 text-blue-500 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-slate-700 font-medium truncate">
                        {file.fileName || `附件${index + 1}`}
                      </p>
                      <p className="text-xs text-slate-400 truncate">
                        {file.fileCategory || "未分类"}
                        {file.fileSize && ` · ${file.fileSize} bytes`}
                      </p>
                    </div>
                    <AttachmentFileActions
                      fileId={file.fileId}
                      fileName={file.fileName || `附件${index + 1}`}
                    />
                  </div>
                ))}
              </div>
            </section>
          )}

          {records.length > 0 && (
            <section>
              <p className="text-xs text-slate-400 mb-2">审批记录</p>
              <div className="space-y-2">
                {records.map((record) => (
                  <div key={record.approvalRecordId} className="p-3 bg-slate-50 rounded-xl">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-sm font-medium text-slate-700">
                        L{record.approvalLevel ?? "-"} · {record.approverName || "审批人"}
                      </span>
                      <Badge variant="secondary" className="text-xs">
                        {record.inspectionFormStatus === 0
                          ? "待审核"
                          : record.inspectionFormStatus === 1
                            ? "通过"
                            : "驳回"}
                      </Badge>
                    </div>
                    {record.approvalDescription && (
                      <p className="text-sm text-slate-600 mt-1">
                        {record.approvalDescription}
                      </p>
                    )}
                    <p className="text-xs text-slate-400 mt-1">
                      {formatDate(record.createdTime)}
                    </p>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="py-2 px-3 bg-slate-50 rounded-lg">
      <p className="text-xs text-slate-400 mb-0.5">{label}</p>
      <p className="text-sm font-medium text-slate-800">{value}</p>
    </div>
  );
}
