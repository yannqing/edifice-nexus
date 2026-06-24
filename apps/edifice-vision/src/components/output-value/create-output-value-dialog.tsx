"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Loader2, Plus, Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ResponseCode } from "@/types/api";
import { getAllProjects, getProjectDetail, getUserList } from "@/services/project";
import {
  createOutputValue,
  getOutputValueList,
  getOutputValuePreview,
} from "@/services/output-value";
import type {
  ProjectListVo,
  ProjectDetailVo,
  ProjectStageVo,
  UserListItem,
} from "@/types/project";
import { STAGE_COMPLETED_STATUSES } from "@/types/project";
import {
  currentQuarter,
  generateQuarterOptions,
  WORK_TYPE_LABELS,
  type CreateDistributionItem,
  type OutputValuePreview,
  type OutputValueVo,
} from "@/types/output-value";

interface CreateOutputValueDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

interface DistRow extends CreateDistributionItem {
  _key: string;
}

const newRow = (userId = ""): DistRow => ({
  _key: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
  userId,
  workType: 1,
  allocRatio: 0,
  completionRatio: 100,
});

function isCompletedStage(stage: ProjectStageVo): boolean {
  if (STAGE_COMPLETED_STATUSES.includes(stage.stageStatus)) return true;
  // 部分完成（进行中但已有审批通过的完成比例）
  if (stage.stageStatus === 1 && (stage.completionRatio ?? 0) > 0) return true;
  return false;
}

function getStageStatusLabel(status: number): string {
  const labels: Record<number, string> = {
    0: "未开始",
    1: "进行中",
    2: "待验收",
    3: "已验收",
    4: "已驳回",
    5: "待分配",
    6: "已完成",
  };
  return labels[status] ?? "未知";
}

const EMPTY_PREVIEW: OutputValuePreview = {
  baseAmount: 0,
  benefitAmount: 0,
  baseRatio: 0,
  benefitRatio: 0,
  basePart: 0,
  benefitPart: 0,
  currentStageAmount: 0,
  adjustmentAmount: 0,
  thisPeriodTotal: 0,
  adjustmentDetails: [],
};

export function CreateOutputValueDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateOutputValueDialogProps) {
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [outputValues, setOutputValues] = useState<OutputValueVo[]>([]);
  const [projectId, setProjectId] = useState<string>("");
  const [projectDetail, setProjectDetail] = useState<ProjectDetailVo | null>(null);
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [preview, setPreview] = useState<OutputValuePreview>(EMPTY_PREVIEW);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [stageId, setStageId] = useState<string>("");
  const [quarter, setQuarter] = useState<string>(currentQuarter());
  const [confirmUserId, setConfirmUserId] = useState<string>("");
  const [subsidyAmount, setSubsidyAmount] = useState<string>("");
  const [rows, setRows] = useState<DistRow[]>([newRow()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const reset = useCallback(() => {
    setProjectId("");
    setProjectDetail(null);
    setStages([]);
    setPreview(EMPTY_PREVIEW);
    setPreviewLoading(false);
    setStageId("");
    setQuarter(currentQuarter());
    setConfirmUserId("");
    setSubsidyAmount("");
    setRows([newRow()]);
    setError("");
  }, []);

  const fetchOptions = useCallback(async () => {
    try {
      const [projectsRes, usersRes, outputValuesRes] = await Promise.all([
        getAllProjects({ pageSize: 200 }),
        getUserList(),
        getOutputValueList(),
      ]);
      if (projectsRes.code === ResponseCode.SUCCESS && projectsRes.data) {
        // 已归档项目不能创建产值分配
        setProjects((projectsRes.data.records ?? []).filter((p) => p.archiveStatus !== 1));
      }
      if (usersRes.code === ResponseCode.SUCCESS && usersRes.data) {
        setUsers(usersRes.data.records ?? []);
      }
      if (outputValuesRes.code === ResponseCode.SUCCESS && outputValuesRes.data) {
        setOutputValues(outputValuesRes.data ?? []);
      }
    } catch {
      /* 静默 */
    }
  }, []);

  const confirmedStageIds = useMemo(() => {
    return new Set(
      outputValues
        .filter((item) => item.status >= 1)
        .map((item) => item.projectStageId),
    );
  }, [outputValues]);

  const isStageAvailable = useCallback(
    (stage: ProjectStageVo) => {
      if (!isCompletedStage(stage)) return false;
      if (!confirmedStageIds.has(stage.projectStageId)) return true;
      // 已有产值分配但阶段部分完成（<100%），仍可继续分配剩余部分
      const cr = stage.completionRatio ?? (stage.stageStatus === 6 ? 100 : 0);
      return cr > 0 && cr < 100;
    },
    [confirmedStageIds],
  );

  useEffect(() => {
    if (open) {
      reset();
      fetchOptions();
    }
  }, [open, fetchOptions, reset]);

  // 选择项目后加载阶段和成员（依赖 isStageAvailable 确保 outputValues 加载后重新筛选）
  useEffect(() => {
    if (!projectId) {
      setProjectDetail(null);
      setStages([]);
      setStageId("");
      setPreview(EMPTY_PREVIEW);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await getProjectDetail(projectId);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setProjectDetail(res.data);
          setStages((res.data.projectStages ?? []).filter(isStageAvailable));
          setStageId("");
        }
      } catch {
        /* 静默 */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId, isStageAvailable]);

  useEffect(() => {
    if (!projectId || !stageId) {
      setPreview(EMPTY_PREVIEW);
      setPreviewLoading(false);
      return;
    }
    let cancelled = false;
    setPreviewLoading(true);
    setError("");
    (async () => {
      try {
        const res = await getOutputValuePreview(projectId, stageId);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setPreview(res.data);
        } else {
          setPreview(EMPTY_PREVIEW);
          setError(res.msg || "产值预览计算失败");
        }
      } catch {
        if (!cancelled) {
          setPreview(EMPTY_PREVIEW);
          setError("产值预览计算失败，请稍后重试");
        }
      } finally {
        if (!cancelled) setPreviewLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId, stageId]);

  // 候选分配人员：优先项目成员，否则全量
  const memberOptions = useMemo(() => {
    const memberIds = new Set(
      (projectDetail?.projectMemberList ?? []).map((m) => m.userId),
    );
    if (memberIds.size === 0) return users;
    return users.filter((u) => memberIds.has(u.userId));
  }, [users, projectDetail]);

  const eligibleProjects = useMemo(() => {
    return projects.filter((project) =>
      (project.projectStages ?? []).some(isStageAvailable),
    );
  }, [projects, isStageAvailable]);

  // 当前选中阶段
  const selectedStage = useMemo(
    () => stages.find((s) => s.projectStageId === stageId) ?? null,
    [stages, stageId],
  );

  const hasBenefit = preview.benefitAmount !== 0 || preview.benefitRatio !== 0;

  // 派生数据（员工池 / 公司账 / 实得汇总等）
  const { totalNum, subsidyNum, companyMain, employeePool, sumAlloc, sumActual, downgradeDelta, otherAmount } = useMemo(() => {
    const t = preview.thisPeriodTotal;
    const s = Number(subsidyAmount) || 0;
    const cmpMain = Math.round(t * 0.6 * 100) / 100;
    const empPool = Math.round(t * 0.4 * 100) / 100;

    let allocSum = 0;
    let actual = 0;
    let downgrade = 0;
    let other = 0;
    rows.forEach((r) => {
      const alloc = Number(r.allocRatio) || 0;
      const comp = Number(r.completionRatio) || 0;
      const isActive = r.isActive !== 0;
      allocSum += alloc;
      const planned = Math.round(empPool * (alloc / 100) * 100) / 100;
      if (!isActive) {
        other += planned;
      } else if (comp < 100) {
        const a = Math.round(planned * (comp / 100) * 100) / 100;
        actual += a;
        downgrade += planned - a;
      } else {
        actual += planned;
      }
    });
    return {
      totalNum: t,
      subsidyNum: s,
      companyMain: cmpMain,
      employeePool: empPool,
      sumAlloc: allocSum,
      sumActual: Math.round(actual * 100) / 100,
      downgradeDelta: Math.round(downgrade * 100) / 100,
      otherAmount: Math.round(other * 100) / 100,
    };
  }, [preview, subsidyAmount, rows]);

  const companyAccount = Math.round((companyMain + downgradeDelta + otherAmount) * 100) / 100;

  const updateRow = (key: string, patch: Partial<DistRow>) => {
    setRows((prev) => prev.map((r) => (r._key === key ? { ...r, ...patch } : r)));
  };

  const addRow = () => setRows((prev) => [...prev, newRow()]);
  const removeRow = (key: string) =>
    setRows((prev) => (prev.length > 1 ? prev.filter((r) => r._key !== key) : prev));

  const quarterOptions = useMemo(() => generateQuarterOptions(8), []);

  const handleSubmit = async () => {
    setError("");
    if (!projectId) return setError("请选择项目");
    if (!stageId) return setError("请选择阶段");
    if (!selectedStage || !isCompletedStage(selectedStage)) {
      return setError("只能为已完成阶段创建产值分配单");
    }
    if (!quarter) return setError("请选择季度");
    if (!confirmUserId) return setError("请选择确认人");
    if (previewLoading) return setError("产值预览正在计算，请稍后再提交");
    if (preview.thisPeriodTotal === 0) {
      return setError(
        "本次产值为 0：请先检查合同金额、预计效益金额、阶段产值比例或历史补差",
      );
    }
    if (Math.abs(sumAlloc - 100) > 0.01)
      return setError(`分配比例合计应为 100%，当前为 ${sumAlloc}%`);
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      if (!r.userId) return setError(`第 ${i + 1} 行未选择分配人员`);
      if (r.allocRatio < 0 || r.allocRatio > 100)
        return setError(`第 ${i + 1} 行分配比例应在 0-100 之间`);
      if (r.completionRatio < 0 || r.completionRatio > 100)
        return setError(`第 ${i + 1} 行完成比例应在 0-100 之间`);
    }

    setSubmitting(true);
    try {
      const res = await createOutputValue({
        projectId,
        projectStageId: stageId,
        quarter,
        confirmUserId,
        // totalAmount 由后端计算，不传
        subsidyAmount: subsidyNum || undefined,
        distributions: rows.map((r) => ({
          userId: r.userId,
          workType: r.workType,
          allocRatio: Number(r.allocRatio) || 0,
          completionRatio: Number(r.completionRatio) || 0,
          isActive: r.isActive,
        })),
      });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("产值分配单已创建");
        onOpenChange(false);
        onSuccess();
      } else {
        setError(res.msg || "创建失败");
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
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>新建产值分配单</DialogTitle>
          <DialogDescription>
            阶段产值由系统按合同、阶段比例和历史补差自动计算；员工池 40%、公司账 60%（含降档差额、离职兜底）。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5 mt-4">
          {/* 基本信息 */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">项目</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
              >
                <option value="">请选择项目</option>
                {eligibleProjects.map((p) => (
                  <option key={p.projectId} value={p.projectId}>
                    {p.projectName} ({p.projectCode})
                  </option>
                ))}
              </select>
              {projects.length > 0 && eligibleProjects.length === 0 && (
                <p className="text-xs text-amber-600 mt-1">
                  暂无可分配产值的项目，请先完成项目阶段；已确认产值的阶段不会重复显示。
                </p>
              )}
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">项目阶段</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                value={stageId}
                onChange={(e) => setStageId(e.target.value)}
                disabled={!projectId || stages.length === 0}
              >
                <option value="">
                  {projectId && stages.length === 0 ? "该项目暂无可创建产值的阶段" : "请选择已完成阶段"}
                </option>
                {stages.map((s) => (
                  <option key={s.projectStageId} value={s.projectStageId}>
                    {s.stageName} (产值比例 {s.stageOutput}% · {getStageStatusLabel(s.stageStatus)})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">所属季度</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={quarter}
                onChange={(e) => setQuarter(e.target.value)}
              >
                {quarterOptions.map((q) => (
                  <option key={q} value={q}>
                    {q}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                确认人 <span className="text-rose-500">*</span>
              </label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={confirmUserId}
                onChange={(e) => setConfirmUserId(e.target.value)}
              >
                <option value="">请选择确认人</option>
                {users.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.realName || u.username}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                公司补贴（元，只记录不计入产值）
              </label>
              <input
                type="number"
                min={0}
                step={0.01}
                placeholder="可为空"
                value={subsidyAmount}
                onChange={(e) => setSubsidyAmount(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              />
            </div>
          </div>

          {/* v0.4 阶段产值预览：系统按合同 + 阶段比例自动算 */}
          <div className="rounded-xl border border-blue-100 bg-blue-50/50 p-4 space-y-2 text-xs">
            <p className="text-sm font-semibold text-slate-700">
              产值预览（系统计算）
              <span className="ml-2 text-xs text-slate-400 font-normal">
                {hasBenefit
                  ? "基本+效益 · 本阶段产值 = 基本部分 + 效益部分"
                  : "基本收费 · 本阶段产值 = 合同金额 × 阶段比例"}
              </span>
              {previewLoading && (
                <Loader2 className="inline-block w-3.5 h-3.5 animate-spin ml-2 text-blue-500" />
              )}
            </p>
            <div className={cn("grid grid-cols-1 gap-2 text-slate-600", hasBenefit && "sm:grid-cols-2")}>
              <div>
                基本部分：¥{preview.baseAmount.toLocaleString()} × {preview.baseRatio}%
                {selectedStage && (selectedStage.completionRatio ?? (selectedStage.stageStatus === 6 ? 100 : 0)) > 0
                  && (selectedStage.completionRatio ?? (selectedStage.stageStatus === 6 ? 100 : 0)) < 100 && (
                  <span> × {selectedStage.completionRatio ?? 100}%（完成比例）</span>
                )}
                <span className="font-semibold text-slate-800 ml-1">
                  = ¥{preview.basePart.toLocaleString()}
                </span>
              </div>
              {hasBenefit && (
                <div>
                  效益部分：¥{preview.benefitAmount.toLocaleString()} × {preview.benefitRatio}%
                  {selectedStage && (selectedStage.completionRatio ?? (selectedStage.stageStatus === 6 ? 100 : 0)) > 0
                    && (selectedStage.completionRatio ?? (selectedStage.stageStatus === 6 ? 100 : 0)) < 100 && (
                    <span> × {selectedStage.completionRatio ?? 100}%（完成比例）</span>
                  )}
                  <span className="font-semibold text-slate-800 ml-1">
                    = ¥{preview.benefitPart.toLocaleString()}
                  </span>
                </div>
              )}
            </div>
            <div className="pt-2 border-t border-blue-100 text-slate-700">
              当前阶段产值：
              <span className="text-base font-bold text-blue-700 ml-2">
                ¥{preview.currentStageAmount.toLocaleString()}
              </span>
            </div>
            <div className="pt-2 border-t border-blue-100 text-slate-700">
              历史补差合计：
              <span
                className={cn(
                  "text-base font-bold ml-2",
                  preview.adjustmentAmount < 0 ? "text-rose-600" : "text-emerald-700",
                )}
              >
                ¥{preview.adjustmentAmount.toLocaleString()}
              </span>
              <span className="text-slate-400 ml-2">
                （补差为正，扣回为负）
              </span>
            </div>
            {preview.adjustmentDetails.length > 0 && (
              <div className="overflow-x-auto border border-blue-100 rounded-lg bg-white">
                <table className="w-full text-xs">
                  <thead className="bg-blue-50 text-slate-500">
                    <tr>
                      <th className="text-left py-2 px-3 font-medium">历史阶段</th>
                      <th className="text-right py-2 px-3 font-medium">原阶段金额</th>
                      <th className="text-right py-2 px-3 font-medium">重算金额</th>
                      <th className="text-right py-2 px-3 font-medium">已补/扣</th>
                      <th className="text-right py-2 px-3 font-medium">本次补/扣</th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.adjustmentDetails.map((detail) => (
                      <tr key={detail.sourceOutputValueId} className="border-t border-blue-50">
                        <td className="py-2 px-3 text-slate-700">
                          {detail.sourceStageName || "-"}
                          <span className="ml-1 text-slate-400">
                            {detail.sourceBaseRatio}%
                          </span>
                        </td>
                        <td className="py-2 px-3 text-right text-slate-600">
                          ¥{detail.oldStageAmount.toLocaleString()}
                        </td>
                        <td className="py-2 px-3 text-right text-slate-600">
                          ¥{detail.newStageAmount.toLocaleString()}
                        </td>
                        <td className="py-2 px-3 text-right text-slate-600">
                          ¥{detail.alreadyAdjustedAmount.toLocaleString()}
                        </td>
                        <td
                          className={cn(
                            "py-2 px-3 text-right font-semibold",
                            detail.adjustmentAmount < 0 ? "text-rose-600" : "text-emerald-700",
                          )}
                        >
                          ¥{detail.adjustmentAmount.toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="pt-2 border-t border-blue-100 text-slate-700">
              本次计入分配总额：
              <span className="text-lg font-bold text-blue-700 ml-2">
                ¥{preview.thisPeriodTotal.toLocaleString()}
              </span>
            </div>
          </div>

          {/* 派生统计（v0.4：员工 40% / 公司 60%） */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
            <MetricBox label="员工池 (40%)" value={employeePool} tone="blue" />
            <MetricBox label="公司账主体 (60%)" value={companyMain} tone="slate" />
            <MetricBox label="降档归公司" value={downgradeDelta} tone="amber" />
            <MetricBox label="离职归公司" value={otherAmount} tone="rose" />
          </div>

          {/* 分配明细 */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-semibold text-slate-700">分配明细</span>
              <Button variant="outline" size="sm" onClick={addRow}>
                <Plus className="w-4 h-4 mr-1" /> 添加一行
              </Button>
            </div>
            <div className="overflow-x-auto border border-slate-200 rounded-xl">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-xs text-slate-500">
                  <tr>
                    <th className="text-left py-2 px-3 font-medium">分配人员</th>
                    <th className="text-left py-2 px-3 font-medium">工作类型</th>
                    <th className="text-center py-2 px-3 font-medium">分配比例%</th>
                    <th className="text-center py-2 px-3 font-medium">完成比例%</th>
                    <th className="text-center py-2 px-3 font-medium">在职</th>
                    <th className="text-right py-2 px-3 font-medium">预计实得</th>
                    <th className="w-10"></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => {
                    const alloc = Number(r.allocRatio) || 0;
                    const comp = Number(r.completionRatio) || 0;
                    const planned = Math.round(employeePool * (alloc / 100) * 100) / 100;
                    const isActive = r.isActive !== 0;
                    const actual = !isActive
                      ? 0
                      : Math.round(planned * (comp / 100) * 100) / 100;
                    return (
                      <tr key={r._key} className="border-t border-slate-100">
                        <td className="py-2 px-3">
                          <select
                            className="w-full px-2 py-1 rounded border border-slate-200 text-sm bg-white"
                            value={r.userId}
                            onChange={(e) => updateRow(r._key, { userId: e.target.value })}
                          >
                            <option value="">请选择</option>
                            {memberOptions.map((u) => (
                              <option key={u.userId} value={u.userId}>
                                {u.realName || u.username}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td className="py-2 px-3">
                          <select
                            className="w-full px-2 py-1 rounded border border-slate-200 text-sm bg-white"
                            value={r.workType}
                            onChange={(e) =>
                              updateRow(r._key, { workType: Number(e.target.value) })
                            }
                          >
                            {Object.entries(WORK_TYPE_LABELS).map(([k, v]) => (
                              <option key={k} value={k}>
                                {v}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td className="py-2 px-3">
                          <input
                            type="number"
                            min={0}
                            max={100}
                            step={0.01}
                            value={r.allocRatio}
                            onChange={(e) =>
                              updateRow(r._key, { allocRatio: Number(e.target.value) })
                            }
                            className="w-full px-2 py-1 rounded border border-slate-200 text-sm text-center"
                          />
                        </td>
                        <td className="py-2 px-3">
                          <input
                            type="number"
                            min={0}
                            max={100}
                            step={0.01}
                            value={r.completionRatio}
                            onChange={(e) =>
                              updateRow(r._key, { completionRatio: Number(e.target.value) })
                            }
                            className="w-full px-2 py-1 rounded border border-slate-200 text-sm text-center"
                          />
                        </td>
                        <td className="py-2 px-3 text-center">
                          <input
                            type="checkbox"
                            checked={isActive}
                            onChange={(e) =>
                              updateRow(r._key, { isActive: e.target.checked ? 1 : 0 })
                            }
                          />
                        </td>
                        <td className="py-2 px-3 text-right font-semibold text-slate-700">
                          ¥{actual.toLocaleString()}
                        </td>
                        <td className="py-2 px-2 text-center">
                          <button
                            type="button"
                            onClick={() => removeRow(r._key)}
                            className="text-slate-400 hover:text-rose-500"
                            disabled={rows.length <= 1}
                            title={rows.length <= 1 ? "至少保留一行" : "删除此行"}
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="flex justify-between text-xs text-slate-500 mt-2">
              <span>
                分配比例合计:
                <span
                  className={cn(
                    "ml-1 font-semibold",
                    Math.abs(sumAlloc - 100) > 0.01 ? "text-rose-500" : "text-emerald-600",
                  )}
                >
                  {sumAlloc.toFixed(2)}%
                </span>
                （应等于 100%）
              </span>
              <span>
                员工实得合计:
                <span className="ml-1 font-semibold text-slate-700">
                  ¥{sumActual.toLocaleString()}
                </span>
              </span>
            </div>
          </div>

          {/* v0.4 守恒预览 */}
          <div className="p-3 bg-slate-50 rounded-lg text-xs text-slate-600 leading-relaxed">
            守恒预览：公司账 ¥{companyAccount.toLocaleString()} + 员工实得 ¥
            {sumActual.toLocaleString()} ={" "}
            <span className="font-semibold text-slate-800">
              ¥{(companyAccount + sumActual).toLocaleString()}
            </span>{" "}
            / 本期产值 ¥{totalNum.toLocaleString()}
            <span className="ml-2 text-slate-400">
              （公司账 = 60% 主体 ¥{companyMain.toLocaleString()} + 降档 ¥
              {downgradeDelta.toLocaleString()} + 离职 ¥{otherAmount.toLocaleString()}）
            </span>
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
            disabled={submitting || previewLoading}
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin mr-1" />} 创建
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function MetricBox({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: "slate" | "blue" | "amber" | "rose";
}) {
  const toneMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-700",
    blue: "bg-blue-50 text-blue-700",
    amber: "bg-amber-50 text-amber-700",
    rose: "bg-rose-50 text-rose-700",
  };
  return (
    <div className={cn("rounded-lg px-3 py-2", toneMap[tone])}>
      <p className="text-[11px] opacity-75">{label}</p>
      <p className="text-sm font-semibold">¥{value.toLocaleString()}</p>
    </div>
  );
}
