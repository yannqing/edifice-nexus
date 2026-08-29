"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Loader2, Plus, RefreshCw, Trash2, UserRoundSearch } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { UserPickerDialog } from "@/components/user/user-picker-dialog";
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
  type BenefitAdjustmentVo,
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

const newRow = (workType: number, userId = ""): DistRow => ({
  _key: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
  userId,
  workType,
  roleAllocRatio: 0,
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
  alreadyAllocated: 0,
  incrementalRatio: 0,
  workPools: [],
  adjustmentDetails: [],
  benefitAdjustments: [],
};

const money = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100;

interface SettledBenefitAdjustment extends BenefitAdjustmentVo {
  appliedAmount: number;
  remainingAmount: number;
}

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
  const [confirmUserName, setConfirmUserName] = useState<string>("");
  const [confirmUserPickerOpen, setConfirmUserPickerOpen] = useState(false);
  const [subsidyAmount, setSubsidyAmount] = useState<string>("");
  const [coefficient, setCoefficient] = useState<string>("1");
  const [rows, setRows] = useState<DistRow[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [dataReady, setDataReady] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [reloadVersion, setReloadVersion] = useState(0);
  const [projectLoading, setProjectLoading] = useState(false);

  const reset = useCallback(() => {
    setProjectId("");
    setProjectDetail(null);
    setStages([]);
    setPreview(EMPTY_PREVIEW);
    setPreviewLoading(false);
    setStageId("");
    setQuarter(currentQuarter());
    setConfirmUserId("");
    setConfirmUserName("");
    setConfirmUserPickerOpen(false);
    setSubsidyAmount("");
    setCoefficient("1");
    setRows([]);
    setError("");
  }, []);

  const fetchOptions = useCallback(async () => {
    const [projectsRes, usersRes, outputValuesRes] = await Promise.all([
      getAllProjects({ pageSize: 200 }),
      getUserList(),
      getOutputValueList(),
    ]);
    if (
      projectsRes.code !== ResponseCode.SUCCESS || !projectsRes.data ||
      usersRes.code !== ResponseCode.SUCCESS || !usersRes.data ||
      outputValuesRes.code !== ResponseCode.SUCCESS || !outputValuesRes.data
    ) {
      throw new Error("Failed to load output value form data");
    }

    return {
      projects: (projectsRes.data.records ?? []).filter((project) => project.archiveStatus !== 1),
      users: usersRes.data.records ?? [],
      outputValues: outputValuesRes.data ?? [],
    };
  }, []);

  // 记录每个阶段已确认的最大分配完成比例
  const confirmedStageMaxRatio = useMemo(() => {
    const map = new Map<string, number>();
    outputValues
      .filter((item) => item.status >= 1)
      .forEach((item) => {
        const ratio = item.stageCompletionRatio ?? 100;
        const prev = map.get(item.projectStageId) ?? 0;
        if (ratio > prev) map.set(item.projectStageId, ratio);
      });
    return map;
  }, [outputValues]);

  const isStageAvailable = useCallback(
    (stage: ProjectStageVo) => {
      if (!isCompletedStage(stage)) return false;
      const maxAllocated = confirmedStageMaxRatio.get(stage.projectStageId);
      // 阶段没有已确认的产值分配 → 可选
      if (!maxAllocated) return true;
      // 阶段已有分配：只有当前完成比例 > 已分配比例时才有新增可分配部分
      const currentRatio = stage.completionRatio ?? (stage.stageStatus === 6 ? 100 : 0);
      return currentRatio > maxAllocated;
    },
    [confirmedStageMaxRatio],
  );

  useEffect(() => {
    let cancelled = false;

    if (!open) {
      reset();
      setProjects([]);
      setUsers([]);
      setOutputValues([]);
      setDataReady(false);
      setLoadError("");
      setProjectLoading(false);
      return;
    }

    reset();
    setProjects([]);
    setUsers([]);
    setOutputValues([]);
    setDataReady(false);
    setLoadError("");
    setProjectLoading(false);

    async function loadInitialData() {
      try {
        const options = await fetchOptions();
        if (cancelled) return;
        setProjects(options.projects);
        setUsers(options.users);
        setOutputValues(options.outputValues);
        setDataReady(true);
      } catch {
        if (cancelled) return;
        setLoadError("产值分配数据加载失败，请稍后重试");
      }
    }

    loadInitialData();
    return () => {
      cancelled = true;
    };
  }, [open, fetchOptions, reset, reloadVersion]);

  // 选择项目后加载阶段和成员（依赖 isStageAvailable 确保 outputValues 加载后重新筛选）
  useEffect(() => {
    if (!projectId) {
      setProjectDetail(null);
      setStages([]);
      setStageId("");
      setPreview(EMPTY_PREVIEW);
      setProjectLoading(false);
      setError("");
      return;
    }
    let cancelled = false;
    setError("");
    setProjectLoading(true);
    setProjectDetail(null);
    setStages([]);
    setStageId("");
    setPreview(EMPTY_PREVIEW);
    (async () => {
      try {
        const res = await getProjectDetail(projectId);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setProjectDetail(res.data);
          setStages((res.data.projectStages ?? []).filter(isStageAvailable));
          setStageId("");
        } else {
          setError(res.msg || "项目数据加载失败");
        }
      } catch {
        if (!cancelled) setError("项目数据加载失败，请重新选择项目");
      } finally {
        if (!cancelled) setProjectLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId, isStageAvailable]);

  // 选择阶段时，自动填入阶段的默认系数
  useEffect(() => {
    if (!stageId) {
      setRows([]);
      return;
    }
    setRows([]);
    const stage = stages.find((s) => s.projectStageId === stageId);
    if (stage?.coefficient != null && stage.coefficient > 0) {
      setCoefficient(String(stage.coefficient));
    } else {
      setCoefficient("1");
    }
  }, [stageId, stages]);

  useEffect(() => {
    if (!projectId || !stageId) {
      setPreview(EMPTY_PREVIEW);
      setPreviewLoading(false);
      return;
    }
    let cancelled = false;
    setPreviewLoading(true);
    setError("");
    const coeff = Number(coefficient) || 1;
    (async () => {
      try {
        const res = await getOutputValuePreview(projectId, stageId, coeff);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setPreview(res.data);
          setRows((current) => current.length > 0
            ? current
            : (res.data?.workPools ?? [])
              .filter((pool) => Math.abs(pool.projectAmount) > 0.001)
              .map((pool) => newRow(pool.workType)));
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
  }, [projectId, stageId, coefficient]);

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
  const previewCoefficient = preview.coefficient ?? 1;
  const hasCoefficient = previewCoefficient !== 1;

  const normalRowAmounts = useMemo(() => {
    const amounts = new Map<string, { planned: number; actual: number }>();
    (preview.workPools ?? []).forEach((pool) => {
      const roleRows = rows.filter((row) => row.workType === pool.workType);
      let groupPlanned = 0;
      roleRows.forEach((row, index) => {
        const planned = index === roleRows.length - 1
          ? money(pool.projectAmount - groupPlanned)
          : money(pool.projectAmount * ((Number(row.roleAllocRatio) || 0) / 100));
        groupPlanned = money(groupPlanned + planned);
        const actual = row.isActive === 0
          ? 0
          : money(planned * ((Number(row.completionRatio) || 0) / 100));
        amounts.set(row._key, { planned, actual });
      });
    });
    return amounts;
  }, [preview.workPools, rows]);

  const normalAmountsByUser = useMemo(() => {
    const amounts = new Map<string, number>();
    rows.filter((row) => row.userId).forEach((row) => {
      const actual = normalRowAmounts.get(row._key)?.actual ?? 0;
      amounts.set(row.userId, money((amounts.get(row.userId) ?? 0) + actual));
    });
    return amounts;
  }, [normalRowAmounts, rows]);

  const benefitSettlement = useMemo(() => {
    const available = new Map(normalAmountsByUser);
    const source = preview.benefitAdjustments ?? [];
    const ordered = [
      ...source.filter((item) => item.pendingAmount > 0),
      ...source.filter((item) => item.pendingAmount < 0),
    ];
    const adjustments: SettledBenefitAdjustment[] = ordered.map((item) => {
      const pending = money(item.pendingAmount);
      const currentAvailable = available.get(item.userId) ?? 0;
      const applied = pending > 0
        ? pending
        : Math.max(pending, -currentAvailable);
      const remaining = money(pending - applied);
      available.set(item.userId, money(currentAvailable + applied));
      return { ...item, appliedAmount: money(applied), remainingAmount: remaining };
    });
    const personApplied = money(adjustments.reduce((sum, item) => sum + item.appliedAmount, 0));
    const personRemaining = money(adjustments.reduce((sum, item) => sum + item.remainingAmount, 0));
    const companyApplied = money(preview.companyAdjustmentAmount ?? 0);
    return {
      adjustments,
      personApplied,
      personRemaining,
      companyApplied,
      adjustmentTotal: money(personApplied + companyApplied),
      total: money(preview.currentStageAmount + personApplied + companyApplied),
    };
  }, [normalAmountsByUser, preview.benefitAdjustments, preview.companyAdjustmentAmount, preview.currentStageAmount]);

  const stageAdjustmentDetails = useMemo(() => (
    (preview.adjustmentDetails ?? []).map((detail) => {
      const personRows = benefitSettlement.adjustments.filter(
        (item) => item.sourceOutputValueId === detail.sourceOutputValueId,
      );
      const personAdjustmentAmount = money(
        personRows.reduce((sum, item) => sum + item.appliedAmount, 0),
      );
      const remainingPersonAdjustmentAmount = money(
        personRows.reduce((sum, item) => sum + item.remainingAmount, 0),
      );
      return {
        ...detail,
        personAdjustmentAmount,
        remainingPersonAdjustmentAmount,
        adjustmentAmount: money(
          personAdjustmentAmount + (detail.companyAdjustmentAmount ?? 0),
        ),
      };
    })
  ), [preview.adjustmentDetails, benefitSettlement.adjustments]);

  // 每个工作类型独立分配，人员可跨工作类型出现。
  const { subsidyNum, companyMain, employeePool, projectPool, sumActual } = useMemo(() => {
    const t = preview.currentStageAmount;
    const s = Number(subsidyAmount) || 0;
    const cmpMain = preview.companyBaseAmount ?? Math.round(t * 0.6 * 100) / 100;
    const empPool = preview.employeePoolAmount ?? Math.round(t * 0.4 * 100) / 100;
    const actual = Array.from(normalRowAmounts.values())
      .reduce((sum, item) => sum + item.actual, 0);
    return {
      subsidyNum: s,
      companyMain: cmpMain,
      employeePool: empPool,
      projectPool: preview.projectPoolAmount ?? 0,
      sumActual: money(actual + benefitSettlement.personApplied),
    };
  }, [preview, subsidyAmount, normalRowAmounts, benefitSettlement.personApplied]);

  const personnelSummary = useMemo(() => {
    const userMap = new Map(memberOptions.map((user) => [user.userId, user]));
    const summary = new Map<string, { userName: string; amounts: Record<number, number>; total: number }>();
    rows.filter((row) => row.userId).forEach((row) => {
      const actual = normalRowAmounts.get(row._key)?.actual ?? 0;
      const user = userMap.get(row.userId);
      const item = summary.get(row.userId) ?? {
        userName: user?.realName || user?.username || row.userId,
        amounts: {},
        total: 0,
      };
      item.amounts[row.workType] = Math.round(((item.amounts[row.workType] ?? 0) + actual) * 100) / 100;
      item.total = Math.round((item.total + actual) * 100) / 100;
      summary.set(row.userId, item);
    });
    benefitSettlement.adjustments
      .filter((adjustment) => adjustment.appliedAmount !== 0)
      .forEach((adjustment) => {
        const item = summary.get(adjustment.userId) ?? {
          userName: adjustment.userName || adjustment.userId,
          amounts: {},
          total: 0,
        };
        item.amounts[adjustment.workType] = money(
          (item.amounts[adjustment.workType] ?? 0) + adjustment.appliedAmount,
        );
        item.total = money(item.total + adjustment.appliedAmount);
        summary.set(adjustment.userId, item);
      });
    return Array.from(summary.entries()).map(([userId, item]) => ({ userId, ...item }));
  }, [memberOptions, normalRowAmounts, rows, benefitSettlement.adjustments]);

  const updateRow = (key: string, patch: Partial<DistRow>) => {
    setRows((prev) => prev.map((r) => (r._key === key ? { ...r, ...patch } : r)));
  };

  const addRow = (workType: number) => setRows((prev) => [...prev, newRow(workType)]);
  const removeRow = (key: string, workType: number) =>
    setRows((prev) => prev.filter((r) => r._key !== key || r.workType !== workType));

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
    if (benefitSettlement.total === 0) {
      return setError(
        "本次产值为 0：请先检查合同金额、预计效益金额、阶段产值比例或历史补差",
      );
    }
    for (const pool of preview.workPools ?? []) {
      const roleRows = rows.filter((row) => row.workType === pool.workType);
      if (Math.abs(pool.projectAmount) <= 0.001) {
        if (roleRows.length > 0) return setError(`${pool.workTypeName}当前没有可分配金额`);
        continue;
      }
      if (roleRows.length === 0) return setError(`请添加${pool.workTypeName}分配人员`);
      const roleSum = roleRows.reduce((sum, row) => sum + (Number(row.roleAllocRatio) || 0), 0);
      if (Math.abs(roleSum - 100) > 0.01) {
        return setError(`${pool.workTypeName}人员比例合计应为 100%，当前为 ${roleSum.toFixed(2)}%`);
      }
      const roleUsers = roleRows.map((row) => row.userId).filter(Boolean);
      if (new Set(roleUsers).size !== roleUsers.length) {
        return setError(`${pool.workTypeName}中存在重复人员`);
      }
    }
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      if (!r.userId) return setError(`第 ${i + 1} 行未选择分配人员`);
      if (r.roleAllocRatio < 0 || r.roleAllocRatio > 100)
        return setError(`第 ${i + 1} 行角色内分配比例应在 0-100 之间`);
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
        coefficient: Number(coefficient) || 1,
        distributions: rows.map((r) => ({
          userId: r.userId,
          workType: r.workType,
          roleAllocRatio: Number(r.roleAllocRatio) || 0,
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
    <>
      <Dialog
        open={open}
        onOpenChange={(v) => {
          if (!v) reset();
          onOpenChange(v);
        }}
      >
      <DialogContent className="max-w-4xl max-h-[90vh] min-h-[460px] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>新建产值分配单</DialogTitle>
          <DialogDescription>
            阶段产值由系统按合同、阶段比例和历史补差自动计算，再按工作类型资金池分配。
          </DialogDescription>
        </DialogHeader>

        {!dataReady && !loadError && (
          <div className="flex min-h-[340px] flex-col items-center justify-center gap-3 text-slate-500">
            <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
            <p className="text-sm">正在加载产值分配数据...</p>
          </div>
        )}

        {loadError && (
          <div className="flex min-h-[340px] flex-col items-center justify-center gap-4 text-center">
            <p className="text-sm text-slate-500">{loadError}</p>
            <Button variant="outline" onClick={() => setReloadVersion((value) => value + 1)}>
              <RefreshCw className="mr-1.5 h-4 w-4" />
              重新加载
            </Button>
          </div>
        )}

        <div className={dataReady && !loadError ? "space-y-5 mt-4" : "hidden"}>
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
                disabled={!projectId || projectLoading || stages.length === 0}
              >
                <option value="">
                  {projectLoading
                    ? "项目阶段加载中..."
                    : projectId && stages.length === 0
                      ? "该项目暂无可创建产值的阶段"
                      : "请选择已完成阶段"}
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
              <button
                type="button"
                className="flex w-full items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-left text-sm disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"
                disabled={!projectId}
                aria-haspopup="dialog"
                onClick={() => setConfirmUserPickerOpen(true)}
              >
                <span className={confirmUserId ? "truncate text-slate-700" : "truncate text-slate-400"}>
                  {!projectId ? "请先选择项目" : confirmUserName || "请选择确认人"}
                </span>
                <UserRoundSearch className="h-4 w-4 shrink-0 text-slate-400" />
              </button>
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
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">
                阶段系数
              </label>
              <input
                type="number"
                min={0.01}
                max={99.99}
                step={0.01}
                value={coefficient}
                onChange={(e) => setCoefficient(e.target.value)}
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
                  ? hasCoefficient
                    ? "基本+效益 · 本阶段产值 =（基本部分 + 效益部分）× 系数"
                    : "基本+效益 · 本阶段产值 = 基本部分 + 效益部分"
                  : hasCoefficient
                    ? "基本收费 · 本阶段产值 = 基本部分 × 系数"
                    : "基本收费 · 本阶段产值 = 合同金额 × 阶段比例"}
              </span>
              {previewLoading && (
                <Loader2 className="inline-block w-3.5 h-3.5 animate-spin ml-2 text-blue-500" />
              )}
            </p>
            <div className={cn("grid grid-cols-1 gap-2 text-slate-600", hasBenefit && "sm:grid-cols-2")}>
              <div>
                基本部分：¥{preview.baseAmount.toLocaleString()} × {preview.baseRatio}%
                {(preview.incrementalRatio ?? 0) > 0 && (preview.incrementalRatio ?? 0) < 100 && (
                  <span> × {preview.incrementalRatio}%（增量）</span>
                )}
                <span className="font-semibold text-slate-800 ml-1">
                  = ¥{preview.basePart.toLocaleString()}
                </span>
              </div>
              {hasBenefit && (
                <div>
                  效益部分：¥{preview.benefitAmount.toLocaleString()} × {preview.benefitRatio}%
                  {(preview.incrementalRatio ?? 0) > 0 && (preview.incrementalRatio ?? 0) < 100 && (
                    <span> × {preview.incrementalRatio}%（增量）</span>
                  )}
                  <span className="font-semibold text-slate-800 ml-1">
                    = ¥{preview.benefitPart.toLocaleString()}
                  </span>
                </div>
              )}
            </div>
            {hasCoefficient && (
              <div className="pt-2 border-t border-blue-100 text-slate-700">
                系数调整：
                <span className="font-semibold text-slate-800 ml-1">
                  {hasBenefit && "("}¥{preview.basePart.toLocaleString()}
                  {hasBenefit && ` + ¥${preview.benefitPart.toLocaleString()})`}
                  {" × "}{previewCoefficient} = ¥{preview.currentStageAmount.toLocaleString()}
                </span>
              </div>
            )}
            <div className="pt-2 border-t border-blue-100 text-slate-700">
              当前阶段产值：
              <span className="text-base font-bold text-blue-700 ml-2">
                ¥{preview.currentStageAmount.toLocaleString()}
              </span>
              {(preview.alreadyAllocated ?? 0) > 0 && (
                <span className="text-xs text-slate-400 ml-2">
                  （累计应得 ¥{(preview.currentStageAmount + (preview.alreadyAllocated ?? 0)).toLocaleString()} − 已分配 ¥{(preview.alreadyAllocated ?? 0).toLocaleString()}）
                </span>
              )}
            </div>
            <div className="pt-2 border-t border-blue-100 text-slate-700">
              历史补差合计：
              <span
                className={cn(
                  "text-base font-bold ml-2",
                  benefitSettlement.adjustmentTotal < 0 ? "text-rose-600" : "text-emerald-700",
                )}
              >
                ¥{benefitSettlement.adjustmentTotal.toLocaleString()}
              </span>
              <span className="text-slate-400 ml-2">
                （补差为正，扣回为负）
              </span>
            </div>
            {stageAdjustmentDetails.length > 0 && (
              <div className="overflow-x-auto border border-blue-100 rounded-lg bg-white">
                <table className="w-full text-xs">
                  <thead className="bg-blue-50 text-slate-500">
                    <tr>
                      <th className="text-left py-2 px-3 font-medium">历史阶段</th>
                      <th className="text-right py-2 px-3 font-medium">原阶段金额</th>
                      <th className="text-right py-2 px-3 font-medium">重算金额</th>
                      <th className="text-right py-2 px-3 font-medium">已补/扣</th>
                      <th className="text-right py-2 px-3 font-medium">人员补/扣</th>
                      <th className="text-right py-2 px-3 font-medium">剩余待扣</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stageAdjustmentDetails.map((detail) => (
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
                            (detail.personAdjustmentAmount ?? 0) < 0 ? "text-rose-600" : "text-emerald-700",
                          )}
                        >
                          ¥{(detail.personAdjustmentAmount ?? 0).toLocaleString()}
                        </td>
                        <td className="py-2 px-3 text-right font-semibold text-rose-600">
                          ¥{(detail.remainingPersonAdjustmentAmount ?? 0).toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {benefitSettlement.adjustments.length > 0 && (
              <div className="overflow-x-auto border border-blue-100 rounded-lg bg-white">
                <div className="px-3 py-2 bg-blue-50 text-sm font-semibold text-slate-700">
                  历史效益个人补差/扣回
                </div>
                <table className="w-full min-w-[760px] text-xs">
                  <thead className="text-slate-500">
                    <tr>
                      <th className="text-left py-2 px-3 font-medium">人员</th>
                      <th className="text-left py-2 px-3 font-medium">来源阶段</th>
                      <th className="text-left py-2 px-3 font-medium">工作类型</th>
                      <th className="text-right py-2 px-3 font-medium">待补/扣</th>
                      <th className="text-right py-2 px-3 font-medium">本次补/扣</th>
                      <th className="text-right py-2 px-3 font-medium">剩余待扣</th>
                    </tr>
                  </thead>
                  <tbody>
                    {benefitSettlement.adjustments.map((adjustment) => (
                      <tr key={adjustment.sourceDistributionId} className="border-t border-slate-100">
                        <td className="py-2 px-3 text-slate-700">{adjustment.userName || "-"}</td>
                        <td className="py-2 px-3 text-slate-600">{adjustment.sourceStageName || "-"}</td>
                        <td className="py-2 px-3 text-slate-600">
                          {WORK_TYPE_LABELS[adjustment.workType] ?? "-"}
                        </td>
                        <td className="py-2 px-3 text-right text-slate-600">
                          ¥{adjustment.pendingAmount.toLocaleString()}
                        </td>
                        <td className={cn(
                          "py-2 px-3 text-right font-semibold",
                          adjustment.appliedAmount < 0 ? "text-rose-600" : "text-emerald-700",
                        )}>
                          ¥{adjustment.appliedAmount.toLocaleString()}
                        </td>
                        <td className="py-2 px-3 text-right font-semibold text-rose-600">
                          ¥{adjustment.remainingAmount.toLocaleString()}
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
                ¥{benefitSettlement.total.toLocaleString()}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <MetricBox label={`名义员工池 (${preview.employeePoolRate ?? 40}%)`} value={employeePool} tone="blue" />
            <MetricBox label="项目人员可分配" value={projectPool} tone="emerald" />
            <MetricBox label={`公司基础留存 (${preview.companyBaseRate ?? 60}%)`} value={companyMain} tone="slate" />
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold text-slate-700">分配明细</span>
              <span className="text-xs text-slate-400">
                规则版本 v{preview.allocationRuleVersionNo ?? "-"} · 各工作类型内比例分别合计100%
              </span>
            </div>
            {(preview.workPools ?? []).map((pool) => {
              const roleRows = rows.filter((row) => row.workType === pool.workType);
              const roleSum = roleRows.reduce(
                (sum, row) => sum + (Number(row.roleAllocRatio) || 0),
                0,
              );
              const isZeroPool = Math.abs(pool.projectAmount) <= 0.001;
              return (
                <section key={pool.workType} className="border border-slate-200 rounded-lg overflow-hidden">
                  <div className="flex flex-wrap items-center gap-3 bg-slate-50 px-3 py-2">
                    <span className="text-sm font-semibold text-slate-700">{pool.workTypeName}</span>
                    <span className="text-xs text-slate-500">阶段工作权重 {pool.workWeight}%</span>
                    <span className="text-xs font-medium text-blue-700">
                      项目人员占本阶段 {pool.projectRate}% · ¥{pool.projectAmount.toLocaleString()}
                    </span>
                    <div className="flex-1" />
                    {!isZeroPool && (
                      <Button variant="outline" size="sm" onClick={() => addRow(pool.workType)}>
                        <Plus className="w-4 h-4 mr-1" /> 添加人员
                      </Button>
                    )}
                  </div>
                  {isZeroPool ? (
                    <div className="px-3 py-4 text-xs text-slate-400">本阶段该工作类型无项目人员可分配金额</div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full min-w-[720px] text-sm">
                        <thead className="text-xs text-slate-500">
                          <tr>
                            <th className="text-left py-2 px-3 font-medium">分配人员</th>
                            <th className="text-center py-2 px-3 font-medium">角色内比例</th>
                            <th className="text-right py-2 px-3 font-medium">计划金额</th>
                            <th className="text-center py-2 px-3 font-medium">兑现比例</th>
                            <th className="text-center py-2 px-3 font-medium">在职</th>
                            <th className="text-right py-2 px-3 font-medium">预计实得</th>
                            <th className="w-10" />
                          </tr>
                        </thead>
                        <tbody>
                          {roleRows.map((row) => {
                            const { planned, actual } = normalRowAmounts.get(row._key)
                              ?? { planned: 0, actual: 0 };
                            return (
                              <tr key={row._key} className="border-t border-slate-100">
                                <td className="py-2 px-3">
                                  <select
                                    className="w-full px-2 py-1 rounded border border-slate-200 text-sm bg-white"
                                    value={row.userId}
                                    onChange={(event) => updateRow(row._key, { userId: event.target.value })}
                                  >
                                    <option value="">请选择</option>
                                    {memberOptions.map((user) => (
                                      <option key={user.userId} value={user.userId}>
                                        {user.realName || user.username}
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
                                    value={row.roleAllocRatio}
                                    onChange={(event) => updateRow(row._key, {
                                      roleAllocRatio: Number(event.target.value),
                                    })}
                                    className="w-full px-2 py-1 rounded border border-slate-200 text-sm text-center"
                                  />
                                </td>
                                <td className="py-2 px-3 text-right text-slate-600">¥{planned.toLocaleString()}</td>
                                <td className="py-2 px-3">
                                  <input
                                    type="number"
                                    min={0}
                                    max={100}
                                    step={0.01}
                                    value={row.completionRatio}
                                    onChange={(event) => updateRow(row._key, {
                                      completionRatio: Number(event.target.value),
                                    })}
                                    className="w-full px-2 py-1 rounded border border-slate-200 text-sm text-center"
                                  />
                                </td>
                                <td className="py-2 px-3 text-center">
                                  <input
                                    type="checkbox"
                                    checked={row.isActive !== 0}
                                    onChange={(event) => updateRow(row._key, {
                                      isActive: event.target.checked ? 1 : 0,
                                    })}
                                  />
                                </td>
                                <td className="py-2 px-3 text-right font-semibold text-slate-700">¥{actual.toLocaleString()}</td>
                                <td className="py-2 px-2 text-center">
                                  <button
                                    type="button"
                                    onClick={() => removeRow(row._key, pool.workType)}
                                    className="text-slate-400 hover:text-rose-500 disabled:opacity-30"
                                    disabled={roleRows.length <= 1}
                                    title={roleRows.length <= 1 ? "至少保留一名分配人员" : "删除此行"}
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
                  )}
                  {!isZeroPool && (
                    <div className="flex justify-between border-t border-slate-100 px-3 py-2 text-xs text-slate-500">
                      <span>
                        角色内比例合计
                        <strong className={cn(
                          "ml-1",
                          Math.abs(roleSum - 100) > 0.01 ? "text-rose-600" : "text-emerald-600",
                        )}>
                          {roleSum.toFixed(2)}%
                        </strong>
                      </span>
                      <span>应等于100%</span>
                    </div>
                  )}
                </section>
              );
            })}
          </div>

          {personnelSummary.length > 0 && (
            <div className="border border-slate-200 rounded-lg overflow-x-auto">
              <div className="px-3 py-2 bg-slate-50 text-sm font-semibold text-slate-700">按人员汇总</div>
              <table className="w-full min-w-[620px] text-sm">
                <thead className="text-xs text-slate-500">
                  <tr>
                    <th className="text-left py-2 px-3 font-medium">人员</th>
                    {Object.entries(WORK_TYPE_LABELS).map(([workType, label]) => (
                      <th key={workType} className="text-right py-2 px-3 font-medium">{label}</th>
                    ))}
                    <th className="text-right py-2 px-3 font-medium">实得合计</th>
                  </tr>
                </thead>
                <tbody>
                  {personnelSummary.map((item) => (
                    <tr key={item.userId} className="border-t border-slate-100">
                      <td className="py-2 px-3 text-slate-700">{item.userName}</td>
                      {[0, 1, 2].map((workType) => (
                        <td key={workType} className="py-2 px-3 text-right text-slate-500">
                          ¥{(item.amounts[workType] ?? 0).toLocaleString()}
                        </td>
                      ))}
                      <td className="py-2 px-3 text-right font-semibold text-slate-800">¥{item.total.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="p-3 bg-slate-50 rounded-lg text-xs text-slate-600 leading-relaxed">
            人员分配预览：当前阶段计划分配 ¥{projectPool.toLocaleString()}，历史人员补/扣 ¥
            {benefitSettlement.personApplied.toLocaleString()}，本次人员预计实得 ¥{sumActual.toLocaleString()}。
            {benefitSettlement.personRemaining < 0 && (
              <span className="ml-1 text-rose-600">
                仍有 ¥{Math.abs(benefitSettlement.personRemaining).toLocaleString()} 待后续阶段扣回。
              </span>
            )}
          </div>

          {error && (
            <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-600 text-sm">
              {error}
            </div>
          )}
        </div>

        <div className={dataReady && !loadError
          ? "flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100"
          : "hidden"}
        >
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

      <UserPickerDialog
        open={confirmUserPickerOpen}
        onOpenChange={setConfirmUserPickerOpen}
        value={confirmUserId}
        title="选择确认人"
        onSelect={(user) => {
          setConfirmUserId(String(user.userId));
          setConfirmUserName(user.realName || user.username);
        }}
      />
    </>
  );
}

function MetricBox({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: "slate" | "blue" | "emerald" | "amber" | "rose";
}) {
  const toneMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-700",
    blue: "bg-blue-50 text-blue-700",
    emerald: "bg-emerald-50 text-emerald-700",
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
