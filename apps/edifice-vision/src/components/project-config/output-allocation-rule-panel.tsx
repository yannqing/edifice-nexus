"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Save } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { isAbortError } from "@/lib/request";
import { getOutputAllocationRule, saveOutputAllocationRule } from "@/services/output-allocation-rule";
import type { ProjectTypeVo } from "@/services/project-type";
import { ResponseCode } from "@/types/api";
import type { OutputAllocationRule } from "@/types/output-allocation-rule";

const WORK_TYPES = [0, 1, 2] as const;
const WORK_LABELS: Record<number, string> = {
  0: "管理工作",
  1: "基础工作",
  2: "智励工作",
};

interface OutputAllocationRulePanelProps {
  projectTypes: ProjectTypeVo[];
}

export function OutputAllocationRulePanel({ projectTypes }: OutputAllocationRulePanelProps) {
  const [projectTypeId, setProjectTypeId] = useState("");
  const [rule, setRule] = useState<OutputAllocationRule | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!projectTypeId && projectTypes.length > 0) {
      setProjectTypeId(projectTypes[0].projectTypeId);
    }
  }, [projectTypeId, projectTypes]);

  useEffect(() => {
    if (!projectTypeId) return;
    const controller = new AbortController();
    setLoading(true);
    getOutputAllocationRule(projectTypeId, controller.signal)
      .then((res) => {
        if (res.code === ResponseCode.SUCCESS && res.data) setRule(res.data);
        else setRule(null);
      })
      .catch((error) => {
        if (!isAbortError(error)) setRule(null);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [projectTypeId]);

  const managementCap = rule?.stages[0]?.workRules.find((item) => item.workType === 0)?.projectCapRate ?? 4;
  const wisdomCap = rule?.stages[0]?.workRules.find((item) => item.workType === 2)?.projectCapRate ?? 4;

  const weightedSummary = useMemo(() => {
    if (!rule) return [];
    return WORK_TYPES.map((workType) => {
      let grossRate = 0;
      let projectRate = 0;
      rule.stages.forEach((stage) => {
        const item = stage.workRules.find((workRule) => workRule.workType === workType);
        if (!item) return;
        const stageOutput = Number(stage.stageOutput) || 0;
        const stageGrossRate = rule.employeePoolRate * item.workWeight / 100;
        const stageProjectRate = item.projectCapRate == null
          ? stageGrossRate
          : Math.min(stageGrossRate, item.projectCapRate);
        grossRate += stageOutput * stageGrossRate / 100;
        projectRate += stageOutput * stageProjectRate / 100;
      });
      return {
        workType,
        grossRate,
        projectRate,
        companyRate: grossRate - projectRate,
      };
    });
  }, [rule]);

  const updatePoolRate = (employeePoolRate: number) => {
    if (!rule) return;
    setRule({
      ...rule,
      employeePoolRate,
      companyBaseRate: Math.max(0, 100 - employeePoolRate),
    });
  };

  const updateCap = (workType: number, value: number) => {
    if (!rule) return;
    setRule({
      ...rule,
      stages: rule.stages.map((stage) => ({
        ...stage,
        workRules: stage.workRules.map((item) => item.workType === workType
          ? { ...item, projectCapRate: value }
          : item),
      })),
    });
  };

  const updateWeight = (stageName: string, workType: number, value: number) => {
    if (!rule) return;
    setRule({
      ...rule,
      stages: rule.stages.map((stage) => stage.stageName === stageName
        ? {
          ...stage,
          workRules: stage.workRules.map((item) => item.workType === workType
            ? { ...item, workWeight: value }
            : item),
        }
        : stage),
    });
  };

  const handleSave = async () => {
    if (!rule) return;
    if (rule.employeePoolRate < 0 || rule.employeePoolRate > 100) {
      toast.error("名义员工池比例应在0-100之间");
      return;
    }
    for (const stage of rule.stages) {
      const total = stage.workRules.reduce((sum, item) => sum + Number(item.workWeight || 0), 0);
      if (Math.abs(total - 100) > 0.01) {
        toast.error(`${stage.stageName}三类工作权重合计应为100%，当前为${total.toFixed(2)}%`);
        return;
      }
    }

    setSaving(true);
    try {
      const res = await saveOutputAllocationRule(projectTypeId, {
        employeePoolRate: rule.employeePoolRate,
        companyBaseRate: rule.companyBaseRate,
        stages: rule.stages.map((stage) => ({
          stageName: stage.stageName,
          stageOrder: stage.stageOrder,
          workRules: stage.workRules.map((item) => ({
            workType: item.workType,
            workWeight: item.workWeight,
            projectCapRate: item.projectCapRate,
          })),
        })),
      });
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setRule(res.data);
        toast.success(`分配规则 v${res.data.versionNo} 已生效`);
      } else {
        toast.error(res.msg || "保存失败");
      }
    } catch {
      toast.error("保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <select
          value={projectTypeId}
          onChange={(event) => setProjectTypeId(event.target.value)}
          className="px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm"
        >
          {projectTypes.map((item) => (
            <option key={item.projectTypeId} value={item.projectTypeId}>
              {item.projectTypeCode} - {item.projectTypeName}
            </option>
          ))}
        </select>
        {rule && (
          <span className="text-xs text-slate-500">
            {rule.versionNo > 0
              ? `当前版本 v${rule.versionNo} · ${rule.effectiveTime?.replace("T", " ").slice(0, 16)}`
              : "尚未生效，保存后生成 v1"}
          </span>
        )}
        <div className="flex-1" />
        <Button onClick={handleSave} disabled={!rule || saving}>
          {saving ? <Loader2 className="w-4 h-4 mr-1 animate-spin" /> : <Save className="w-4 h-4 mr-1" />}
          保存为新版本
        </Button>
      </div>

      {loading && (
        <div className="h-48 flex items-center justify-center text-slate-400">
          <Loader2 className="w-5 h-5 animate-spin mr-2" /> 加载规则
        </div>
      )}

      {!loading && !rule && (
        <div className="py-16 text-center text-sm text-slate-400">当前项目类型尚未配置产值分配规则</div>
      )}

      {!loading && rule && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <NumberField label="名义员工池比例" value={rule.employeePoolRate} onChange={updatePoolRate} suffix="%" />
            <NumberField label="公司基础留存" value={rule.companyBaseRate} readOnly suffix="%" />
            <NumberField label="管理工作项目上限" value={managementCap} onChange={(value) => updateCap(0, value)} suffix="%" />
            <NumberField label="智励工作项目上限" value={wisdomCap} onChange={(value) => updateCap(2, value)} suffix="%" />
          </div>

          <div className="border border-slate-200 rounded-lg overflow-x-auto bg-white">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500">
                <tr>
                  <th className="text-left py-3 px-4 font-medium">阶段</th>
                  <th className="text-right py-3 px-4 font-medium">阶段产值比例</th>
                  {WORK_TYPES.map((workType) => (
                    <th key={workType} className="text-center py-3 px-4 font-medium">{WORK_LABELS[workType]}权重</th>
                  ))}
                  <th className="text-right py-3 px-4 font-medium">合计</th>
                </tr>
              </thead>
              <tbody>
                {rule.stages.map((stage) => {
                  const total = stage.workRules.reduce((sum, item) => sum + Number(item.workWeight || 0), 0);
                  return (
                    <tr key={stage.stageName} className="border-t border-slate-100">
                      <td className="py-3 px-4 font-medium text-slate-700">{stage.stageOrder}. {stage.stageName}</td>
                      <td className="py-3 px-4 text-right text-slate-500">{stage.stageOutput ?? 0}%</td>
                      {WORK_TYPES.map((workType) => {
                        const item = stage.workRules.find((workRule) => workRule.workType === workType);
                        return (
                          <td key={workType} className="py-2 px-4">
                            <input
                              type="number"
                              min={0}
                              max={100}
                              step={0.01}
                              value={item?.workWeight ?? 0}
                              onChange={(event) => updateWeight(stage.stageName, workType, Number(event.target.value))}
                              className="w-full px-2 py-1.5 border border-slate-200 rounded text-center"
                            />
                          </td>
                        );
                      })}
                      <td className={`py-3 px-4 text-right font-semibold ${Math.abs(total - 100) > 0.01 ? "text-rose-600" : "text-emerald-600"}`}>
                        {total.toFixed(2)}%
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="border border-slate-200 rounded-lg overflow-x-auto bg-white">
            <div className="px-4 py-3 bg-slate-50 text-sm font-semibold text-slate-700">按当前阶段产值比例加权汇总</div>
            <table className="w-full text-sm">
              <thead className="text-xs text-slate-500">
                <tr>
                  <th className="text-left py-2 px-4 font-medium">工作类型</th>
                  <th className="text-right py-2 px-4 font-medium">占总收入比例</th>
                  <th className="text-right py-2 px-4 font-medium">项目人员分配</th>
                  <th className="text-right py-2 px-4 font-medium">转公司</th>
                </tr>
              </thead>
              <tbody>
                {weightedSummary.map((item) => (
                  <tr key={item.workType} className="border-t border-slate-100">
                    <td className="py-2 px-4 text-slate-700">{WORK_LABELS[item.workType]}</td>
                    <td className="py-2 px-4 text-right">{item.grossRate.toFixed(2)}%</td>
                    <td className="py-2 px-4 text-right text-blue-700">{item.projectRate.toFixed(2)}%</td>
                    <td className="py-2 px-4 text-right text-amber-700">{item.companyRate.toFixed(2)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function NumberField({
  label,
  value,
  onChange,
  readOnly = false,
  suffix,
}: {
  label: string;
  value: number;
  onChange?: (value: number) => void;
  readOnly?: boolean;
  suffix?: string;
}) {
  return (
    <label className="block">
      <span className="block text-xs font-medium text-slate-600 mb-1">{label}</span>
      <div className="relative">
        <input
          type="number"
          min={0}
          max={100}
          step={0.01}
          value={value}
          readOnly={readOnly}
          onChange={(event) => onChange?.(Number(event.target.value))}
          className="w-full px-3 py-2 pr-8 border border-slate-200 rounded-lg text-sm disabled:bg-slate-50 read-only:bg-slate-50"
        />
        {suffix && <span className="absolute right-3 top-2 text-sm text-slate-400">{suffix}</span>}
      </div>
    </label>
  );
}
