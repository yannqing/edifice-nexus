"use client";

import { useState, useEffect } from "react";
import {
  Users,
  FileText,
  Banknote,
  Layers,
  Info,
  Loader2,
  Play,
  RotateCcw,
} from "lucide-react";
import { toast } from "sonner";
import { DialogSkeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { getProjectDetail, startStages, restartStage } from "@/services/project";
import { ResponseCode } from "@/types/api";
import type { ProjectDetailVo } from "@/types/project";
import {
  PROJECT_STATUS_MAP,
  STAGE_COMPLETED_STATUSES,
} from "@/types/project";
import type { ProjectStatus, ProjectCategory } from "@/types";

const statusStyles: Record<ProjectStatus, string> = {
  进行中: "bg-blue-100 text-blue-600",
  待验收: "bg-amber-100 text-amber-600",
  已完成: "bg-emerald-100 text-emerald-600",
  未开始: "bg-slate-100 text-slate-500",
};

const categoryStyles: Record<ProjectCategory, string> = {
  A类: "bg-blue-50 text-blue-600",
  B类: "bg-emerald-50 text-emerald-600",
  C类: "bg-amber-50 text-amber-600",
  D类: "bg-purple-50 text-purple-600",
  E类: "bg-rose-50 text-rose-600",
};

const stageStatusLabels: Record<number, string> = {
  0: "未开始",
  1: "进行中",
  2: "待验收",
  3: "已验收",
  4: "已驳回",
  5: "待分配",
  6: "已完成",
};

const stageStatusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-500",
  1: "bg-blue-100 text-blue-600",
  2: "bg-amber-100 text-amber-600",
  3: "bg-emerald-100 text-emerald-600",
  4: "bg-rose-100 text-rose-600",
  5: "bg-purple-100 text-purple-600",
  6: "bg-emerald-100 text-emerald-600",
};

function getStatusLabel(status: number): ProjectStatus {
  return (PROJECT_STATUS_MAP[status] ?? "未开始") as ProjectStatus;
}

function getCategoryLabel(typeCode: string): ProjectCategory {
  if (typeCode.startsWith("A")) return "A类";
  if (typeCode.startsWith("B")) return "B类";
  if (typeCode.startsWith("C")) return "C类";
  if (typeCode.startsWith("D")) return "D类";
  if (typeCode.startsWith("E")) return "E类";
  return "A类";
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return dateStr.slice(0, 10);
}

function formatAmount(amount: number): string {
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(1)}万`;
  return `¥${amount.toLocaleString()}`;
}

interface ProjectDetailDialogProps {
  projectId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStageChange?: () => void;
}

export function ProjectDetailDialog({
  projectId,
  open,
  onOpenChange,
  onStageChange,
}: ProjectDetailDialogProps) {
  const [detail, setDetail] = useState<ProjectDetailVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [stageActionLoading, setStageActionLoading] = useState(false);

  const reloadDetail = async () => {
    if (!projectId) return;
    try {
      const response = await getProjectDetail(projectId);
      if (response.code === ResponseCode.SUCCESS && response.data) {
        setDetail(response.data);
      }
    } catch {
      // 静默
    }
  };

  // 启动阶段
  const handleStartStages = async (stageIds: string[]) => {
    setStageActionLoading(true);
    try {
      const res = await startStages(stageIds);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("阶段启动成功");
        await reloadDetail();
        onStageChange?.();
      } else {
        toast.error(res.msg || "启动失败");
      }
    } catch {
      toast.error("操作失败");
    } finally {
      setStageActionLoading(false);
    }
  };

  // 重启已驳回阶段
  const handleRestartStage = async (stageId: string) => {
    setStageActionLoading(true);
    try {
      const res = await restartStage(stageId);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("阶段已重新启动");
        await reloadDetail();
        onStageChange?.();
      } else {
        toast.error(res.msg || "重启失败");
      }
    } catch {
      toast.error("操作失败");
    } finally {
      setStageActionLoading(false);
    }
  };

  useEffect(() => {
    if (!open || !projectId) {
      setDetail(null);
      setError("");
      return;
    }

    let cancelled = false;

    async function fetchDetail() {
      setLoading(true);
      setError("");
      try {
        const response = await getProjectDetail(projectId!);
        if (cancelled) return;
        if (response.code === ResponseCode.SUCCESS && response.data) {
          setDetail(response.data);
        } else {
          setError(response.msg || "获取项目详情失败");
        }
      } catch {
        if (!cancelled) setError("网络异常，请稍后重试");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    fetchDetail();
    return () => {
      cancelled = true;
    };
  }, [open, projectId]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        {/* 加载态 */}
        {loading && (
          <>
            <DialogHeader>
              <DialogTitle>项目详情</DialogTitle>
            </DialogHeader>
            <DialogSkeleton />
          </>
        )}

        {/* 错误态 */}
        {!loading && error && (
          <>
            <DialogHeader>
              <DialogTitle>项目详情</DialogTitle>
            </DialogHeader>
            <div className="py-12 text-center">
              <p className="text-sm text-rose-500">{error}</p>
            </div>
          </>
        )}

        {/* 详情内容 */}
        {!loading && detail && (
          <ProjectDetailContent
            detail={detail}
            stageActionLoading={stageActionLoading}
            onStartStages={handleStartStages}
            onRestartStage={handleRestartStage}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

/** 详情内容 */
function ProjectDetailContent({
  detail,
  stageActionLoading,
  onStartStages,
  onRestartStage,
}: {
  detail: ProjectDetailVo;
  stageActionLoading: boolean;
  onStartStages: (stageIds: string[]) => void;
  onRestartStage: (stageId: string) => void;
}) {
  const statusLabel = getStatusLabel(detail.projectStatus);
  const category = getCategoryLabel(detail.projectType?.projectTypeCode ?? "");
  const typeName = detail.projectType?.projectTypeName ?? "";
  const stages = detail.projectStages ?? [];
  const completedStages = stages.filter((s) =>
    STAGE_COMPLETED_STATUSES.includes(s.stageStatus)
  ).length;
  const members = (detail.projectMemberList ?? []).filter((m) => m.realName);
  const contract = detail.contract;
  const startDate = contract?.preStartDate ?? detail.preStartTime;
  const endDate = contract?.preEndDate ?? detail.preEndTime;

  return (
    <>
      <DialogHeader>
        <div className="flex items-center gap-2 mb-1">
          <span
            className={cn(
              "text-xs px-2 py-0.5 rounded-md font-medium",
              categoryStyles[category]
            )}
          >
            {category}
            {typeName ? ` · ${typeName}` : ""}
          </span>
          <Badge
            variant="secondary"
            className={cn("text-xs", statusStyles[statusLabel])}
          >
            {statusLabel}
          </Badge>
        </div>
        <DialogTitle>{detail.projectName}</DialogTitle>
        <DialogDescription>{detail.projectCode}</DialogDescription>
      </DialogHeader>

      <div className="mt-4 space-y-5">
        {/* 基本信息 */}
        <section>
          <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
            <Info className="w-4 h-4 text-slate-400" /> 基本信息
          </h4>
          <div className="grid grid-cols-2 gap-3">
            <InfoItem label="项目编码" value={detail.projectCode} />
            <InfoItem label="项目状态" value={statusLabel} />
            <InfoItem label="开始日期" value={formatDate(startDate)} />
            <InfoItem label="结束日期" value={formatDate(endDate)} />
          </div>
        </section>

        {/* 合同信息 */}
        {contract && (
          <section>
            <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
              <Banknote className="w-4 h-4 text-slate-400" /> 合同信息
            </h4>
            <div className="grid grid-cols-2 gap-3">
              <InfoItem
                label="合同金额"
                value={formatAmount(contract.contractAmount)}
                highlight
              />
              <InfoItem
                label="合同类型"
                value={
                  contract.contractType === 0 ? "基本收费" : "基本+效益"
                }
              />
              {contract.baseAmount > 0 && (
                <InfoItem
                  label="基本金额"
                  value={formatAmount(contract.baseAmount)}
                />
              )}
              {contract.signingDate && (
                <InfoItem
                  label="签订日期"
                  value={formatDate(contract.signingDate)}
                />
              )}
            </div>
          </section>
        )}

        {/* 阶段进度 */}
        {stages.length > 0 && (() => {
          const notStartedIds = stages
            .filter((s) => s.stageStatus === 0)
            .map((s) => s.projectStageId);

          return (
            <section>
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                  <Layers className="w-4 h-4 text-slate-400" /> 阶段进度
                  <span className="text-xs font-normal text-slate-400 ml-1">
                    {completedStages}/{stages.length}
                  </span>
                </h4>
                {notStartedIds.length > 0 && (
                  <Button
                    className="h-7 text-xs bg-blue-600 hover:bg-blue-700 text-white"
                    disabled={stageActionLoading}
                    onClick={() => onStartStages(notStartedIds)}
                  >
                    {stageActionLoading ? (
                      <Loader2 className="w-3 h-3 animate-spin mr-1" />
                    ) : (
                      <Play className="w-3 h-3 mr-1" />
                    )}
                    全部启动
                  </Button>
                )}
              </div>
              <div className="flex gap-1 mb-3">
                {stages.map((s, i) => (
                  <div
                    key={i}
                    className={cn(
                      "h-2 flex-1 rounded-full",
                      s.stageStatus === 6 || s.stageStatus === 3
                        ? "bg-emerald-500"
                        : s.stageStatus === 1
                        ? "bg-blue-500"
                        : s.stageStatus === 2
                        ? "bg-amber-500"
                        : s.stageStatus === 4
                        ? "bg-rose-500"
                        : "bg-slate-200"
                    )}
                  />
                ))}
              </div>
              <div className="space-y-2">
                {stages.map((stage, idx) => (
                  <div
                    key={stage.projectStageId ?? idx}
                    className="flex items-center justify-between py-2 px-3 bg-slate-50 rounded-lg"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-slate-400 w-5">
                        {idx + 1}
                      </span>
                      <span className="text-sm text-slate-700">
                        {stage.stageName}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      {stage.stageOutput > 0 && (
                        <span className="text-xs text-slate-400">
                          产值比 {stage.stageOutput}%
                        </span>
                      )}
                      <span
                        className={cn(
                          "text-xs px-1.5 py-0.5 rounded",
                          stageStatusStyles[stage.stageStatus] ??
                            "bg-slate-100 text-slate-500"
                        )}
                      >
                        {stageStatusLabels[stage.stageStatus] ?? "未知"}
                      </span>
                      {/* 未开始 → 可单独启动 */}
                      {stage.stageStatus === 0 && (
                        <button
                          disabled={stageActionLoading}
                          onClick={() => onStartStages([stage.projectStageId])}
                          className="text-xs text-blue-600 hover:text-blue-700 font-medium disabled:opacity-50"
                        >
                          启动
                        </button>
                      )}
                      {/* 已驳回 → 可重启 */}
                      {stage.stageStatus === 4 && (
                        <button
                          disabled={stageActionLoading}
                          onClick={() => onRestartStage(stage.projectStageId)}
                          className="text-xs text-amber-600 hover:text-amber-700 font-medium disabled:opacity-50 flex items-center gap-0.5"
                        >
                          <RotateCcw className="w-3 h-3" /> 重启
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          );
        })()}

        {/* 项目成员 */}
        {members.length > 0 && (
          <section>
            <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
              <Users className="w-4 h-4 text-slate-400" /> 项目成员
              <span className="text-xs font-normal text-slate-400 ml-1">
                {members.length}人
              </span>
            </h4>
            <div className="flex flex-wrap gap-2">
              {members.map((member) => (
                <div
                  key={member.userId}
                  className="flex items-center gap-2 py-1.5 px-3 bg-slate-50 rounded-lg"
                >
                  <div className="w-6 h-6 rounded-full bg-blue-100 flex items-center justify-center text-xs font-medium text-blue-600">
                    {member.realName[0]}
                  </div>
                  <span className="text-sm text-slate-700">
                    {member.realName}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 合同附件 */}
        {contract?.contractFile && (
          <section>
            <h4 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5 mb-3">
              <FileText className="w-4 h-4 text-slate-400" /> 合同附件
            </h4>
            <p className="text-xs text-slate-400">
              附件 ID: {contract.contractFile}
            </p>
          </section>
        )}
      </div>
    </>
  );
}

/** 信息展示项 */
function InfoItem({
  label,
  value,
  highlight,
}: {
  label: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <div className="py-2 px-3 bg-slate-50 rounded-lg">
      <p className="text-xs text-slate-400 mb-0.5">{label}</p>
      <p
        className={cn(
          "text-sm font-medium",
          highlight ? "text-blue-600" : "text-slate-800"
        )}
      >
        {value}
      </p>
    </div>
  );
}
