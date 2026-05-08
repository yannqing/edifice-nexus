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
import { createOutputValue } from "@/services/output-value";
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
  return STAGE_COMPLETED_STATUSES.includes(stage.stageStatus);
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

export function CreateOutputValueDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateOutputValueDialogProps) {
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [projectId, setProjectId] = useState<string>("");
  const [projectDetail, setProjectDetail] = useState<ProjectDetailVo | null>(null);
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [stageId, setStageId] = useState<string>("");
  const [quarter, setQuarter] = useState<string>(currentQuarter());
  const [subsidyAmount, setSubsidyAmount] = useState<string>("");
  const [allowNegative, setAllowNegative] = useState<boolean>(false);
  const [rows, setRows] = useState<DistRow[]>([newRow()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const reset = useCallback(() => {
    setProjectId("");
    setProjectDetail(null);
    setStages([]);
    setStageId("");
    setQuarter(currentQuarter());
    setSubsidyAmount("");
    setAllowNegative(false);
    setRows([newRow()]);
    setError("");
  }, []);

  const fetchOptions = useCallback(async () => {
    try {
      const [projectsRes, usersRes] = await Promise.all([
        getAllProjects({ pageSize: 200 }),
        getUserList(),
      ]);
      if (projectsRes.code === ResponseCode.SUCCESS && projectsRes.data) {
        setProjects(projectsRes.data.records ?? []);
      }
      if (usersRes.code === ResponseCode.SUCCESS && usersRes.data) {
        setUsers(usersRes.data.records ?? []);
      }
    } catch {
      /* 静默 */
    }
  }, []);

  useEffect(() => {
    if (open) {
      reset();
      fetchOptions();
    }
  }, [open, fetchOptions, reset]);

  // 选择项目后加载阶段和成员
  useEffect(() => {
    if (!projectId) {
      setProjectDetail(null);
      setStages([]);
      setStageId("");
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await getProjectDetail(projectId);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setProjectDetail(res.data);
          setStages((res.data.projectStages ?? []).filter(isCompletedStage));
          setStageId("");
        }
      } catch {
        /* 静默 */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId]);

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
      (project.projectStages ?? []).some(isCompletedStage),
    );
  }, [projects]);

  // 当前选中阶段
  const selectedStage = useMemo(
    () => stages.find((s) => s.projectStageId === stageId) ?? null,
    [stages, stageId],
  );

  // v0.4 预览：基于 contract + stage 自动算累计与本期产值（不含历史累计校准，落库时由后端再算）
  const preview = useMemo(() => {
    const contract = projectDetail?.contract;
    const baseAmt = contract?.baseAmount ?? contract?.contractAmount ?? 0;
    const benefitAmt = contract?.benefitAmount ?? 0;
    const baseRatio = selectedStage?.stageOutput ?? 0;
    const benefitRatio = selectedStage?.benefitInclusionRatio ?? 0;
    const basePart = Math.round(baseAmt * (baseRatio / 100) * 100) / 100;
    const benefitPart = Math.round(benefitAmt * (benefitRatio / 100) * 100) / 100;
    const currentCumulative = basePart + benefitPart;
    return {
      baseAmount: baseAmt,
      benefitAmount: benefitAmt,
      baseRatio,
      benefitRatio,
      basePart,
      benefitPart,
      currentCumulative,
      contractType: contract?.contractType ?? 0,
    };
  }, [projectDetail, selectedStage]);

  // 派生数据（员工池 / 公司账 / 实得汇总等）
  const { totalNum, subsidyNum, companyMain, employeePool, sumAlloc, sumActual, downgradeDelta, otherAmount } = useMemo(() => {
    // 注意：前端预览不知道历史累计，先按当前累计估算"本期产值上限"。
    //       真实本期 = current - previous（由后端落库时算）；如果上次累计不为 0 则前端预览偏大
    const t = preview.currentCumulative;
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
    if (preview.currentCumulative <= 0) {
      return setError(
        "本阶段累计应得为 0：请先在合同里录入预计效益金额、或在阶段编辑里设置基本/效益累计计入比例",
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
        // totalAmount 由后端计算，不传
        subsidyAmount: subsidyNum || undefined,
        allowNegative: allowNegative || undefined,
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
          <DialogTitle>新建产值分配单（v0.4）</DialogTitle>
          <DialogDescription>
            阶段产值由系统按合同（基本+效益）和阶段累计比例自动计算；员工池 40%、公司账 60%（含降档差额、离职兜底）。
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
                  暂无可分配产值的项目，请先完成项目阶段。
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
                  {projectId && stages.length === 0 ? "该项目暂无已完成阶段" : "请选择已完成阶段"}
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

          {/* v0.4 累计预览：系统按合同 + 阶段比例自动算 */}
          <div className="rounded-xl border border-blue-100 bg-blue-50/50 p-4 space-y-2 text-xs">
            <p className="text-sm font-semibold text-slate-700">
              本阶段累计应得（系统计算）
              <span className="ml-2 text-xs text-slate-400 font-normal">
                本期产值 = 当前累计 − 历史累计（由后端落库时校准）
              </span>
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-slate-600">
              <div>
                基本部分：¥{preview.baseAmount.toLocaleString()} × {preview.baseRatio}%
                <span className="font-semibold text-slate-800 ml-1">
                  = ¥{preview.basePart.toLocaleString()}
                </span>
              </div>
              <div>
                效益部分：¥{preview.benefitAmount.toLocaleString()} × {preview.benefitRatio}%
                <span className="font-semibold text-slate-800 ml-1">
                  = ¥{preview.benefitPart.toLocaleString()}
                </span>
              </div>
            </div>
            <div className="pt-2 border-t border-blue-100 text-slate-700">
              当前阶段累计应得：
              <span className="text-base font-bold text-blue-700 ml-2">
                ¥{preview.currentCumulative.toLocaleString()}
              </span>
              <span className="ml-3 text-xs text-slate-400">
                如本阶段为首单则等于本期产值；否则后端会减去历史累计
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

          {totalNum < 0 && (
            <label className="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-700 cursor-pointer">
              <input
                type="checkbox"
                checked={allowNegative}
                onChange={(e) => setAllowNegative(e.target.checked)}
              />
              <span>本期产值为负（效益值下调），勾选确认后允许创建</span>
            </label>
          )}

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
            disabled={submitting}
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
