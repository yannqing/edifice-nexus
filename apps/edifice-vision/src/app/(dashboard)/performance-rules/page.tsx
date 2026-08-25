"use client";

import { useEffect, useState } from "react";
import { TriangleAlert } from "lucide-react";
import { OutputAllocationRulePanel } from "@/components/project-config/output-allocation-rule-panel";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { getAllProjectTypes } from "@/services/project-type";
import type { ProjectTypeVo } from "@/services/project-type";
import { ResponseCode } from "@/types/api";

export default function PerformanceRulesPage() {
  const [projectTypes, setProjectTypes] = useState<ProjectTypeVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let active = true;
    getAllProjectTypes()
      .then((res) => {
        if (!active) return;
        if (res.code === ResponseCode.SUCCESS) {
          setProjectTypes(res.data ?? []);
          setLoadError(false);
        } else {
          setLoadError(true);
        }
      })
      .catch(() => {
        if (active) setLoadError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="space-y-6 p-4 md:p-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">绩效规则</h1>
      </div>

      {loading && <TablePageSkeleton columns={6} rows={5} />}

      {!loading && loadError && (
        <div className="border border-rose-200 bg-rose-50 px-4 py-8 text-center text-sm text-rose-600">
          项目类型加载失败，请刷新后重试
        </div>
      )}

      {!loading && !loadError && projectTypes.length === 0 && (
        <div className="border border-slate-200 bg-white px-4 py-12 text-center text-sm text-slate-500">
          暂无可配置的项目类型
        </div>
      )}

      {!loading && !loadError && projectTypes.length > 0 && (
        <>
          <div className="flex items-center gap-2 border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <TriangleAlert className="h-4 w-4 shrink-0" />
            已生成产值单的项目阶段保留原比例，不随新规则覆盖。
          </div>
          <OutputAllocationRulePanel
            projectTypes={projectTypes}
            editableStageOutput
            showAggregateRates={false}
          />
        </>
      )}
    </div>
  );
}
