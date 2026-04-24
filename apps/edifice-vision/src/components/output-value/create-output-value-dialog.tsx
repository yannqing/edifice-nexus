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
  const [totalAmount, setTotalAmount] = useState<string>("");
  const [subsidyAmount, setSubsidyAmount] = useState<string>("");
  const [rows, setRows] = useState<DistRow[]>([newRow()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const reset = useCallback(() => {
    setProjectId("");
    setProjectDetail(null);
    setStages([]);
    setStageId("");
    setQuarter(currentQuarter());
    setTotalAmount("");
    setSubsidyAmount("");
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
          setStages(res.data.projectStages ?? []);
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

  // 计算派生数据
  const { totalNum, subsidyNum, companyReserve, personalPool, sumAlloc, sumActual, leaderExtra, otherAmount } = useMemo(() => {
    const t = Number(totalAmount) || 0;
    const s = Number(subsidyAmount) || 0;
    const reserve = Math.round(t * 0.4 * 100) / 100;
    const pool = Math.round(t * 0.6 * 100) / 100;

    let allocSum = 0;
    let actual = 0;
    let leader = 0;
    let other = 0;
    rows.forEach((r) => {
      const alloc = Number(r.allocRatio) || 0;
      const comp = Number(r.completionRatio) || 0;
      const isActive = r.isActive !== 0;
      allocSum += alloc;
      const planned = Math.round(pool * (alloc / 100) * 100) / 100;
      if (!isActive) {
        other += planned;
      } else if (comp < 100) {
        const a = Math.round(planned * (comp / 100) * 100) / 100;
        actual += a;
        leader += planned - a;
      } else {
        actual += planned;
      }
    });
    return {
      totalNum: t,
      subsidyNum: s,
      companyReserve: reserve,
      personalPool: pool,
      sumAlloc: allocSum,
      sumActual: Math.round(actual * 100) / 100,
      leaderExtra: Math.round(leader * 100) / 100,
      otherAmount: Math.round(other * 100) / 100,
    };
  }, [totalAmount, subsidyAmount, rows]);

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
    if (!quarter) return setError("请选择季度");
    if (!(totalNum > 0)) return setError("产值总额必须大于 0");
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
        totalAmount: totalNum,
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
            公式：公司留存 40% + 个人池 60%（分配比例 × 完成比例），降档差额归领导兜底，离职成员归入其他金额。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5 mt-4">
          {/* 基本信息 */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">项目</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
              >
                <option value="">请选择项目</option>
                {projects.map((p) => (
                  <option key={p.projectId} value={p.projectId}>
                    {p.projectName} ({p.projectCode})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-600 mb-1 block">项目阶段</label>
              <select
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white disabled:bg-slate-50"
                value={stageId}
                onChange={(e) => setStageId(e.target.value)}
                disabled={!projectId || stages.length === 0}
              >
                <option value="">请选择阶段</option>
                {stages.map((s) => (
                  <option key={s.projectStageId} value={s.projectStageId}>
                    {s.stageName} (产值比例 {s.stageOutput}%)
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
                产值总额（元）
              </label>
              <input
                type="number"
                min={0}
                step={0.01}
                placeholder="例如 100000"
                value={totalAmount}
                onChange={(e) => setTotalAmount(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
              />
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

          {/* 派生统计 */}
          <div className="grid grid-cols-4 gap-3 text-xs">
            <MetricBox label="公司留存 (40%)" value={companyReserve} tone="slate" />
            <MetricBox label="个人池 (60%)" value={personalPool} tone="blue" />
            <MetricBox label="领导兜底" value={leaderExtra} tone="amber" />
            <MetricBox label="其他金额（离职）" value={otherAmount} tone="rose" />
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
                  {rows.map((r, idx) => {
                    const alloc = Number(r.allocRatio) || 0;
                    const comp = Number(r.completionRatio) || 0;
                    const planned = Math.round(personalPool * (alloc / 100) * 100) / 100;
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

          {/* 守恒预览 */}
          <div className="p-3 bg-slate-50 rounded-lg text-xs text-slate-600 leading-relaxed">
            守恒预览：公司留存 ¥{companyReserve.toLocaleString()} + 员工实得 ¥
            {sumActual.toLocaleString()} + 领导兜底 ¥{leaderExtra.toLocaleString()} + 其他金额 ¥
            {otherAmount.toLocaleString()} ={" "}
            <span className="font-semibold text-slate-800">
              ¥{(companyReserve + sumActual + leaderExtra + otherAmount).toLocaleString()}
            </span>{" "}
            / 目标 ¥{totalNum.toLocaleString()}
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
