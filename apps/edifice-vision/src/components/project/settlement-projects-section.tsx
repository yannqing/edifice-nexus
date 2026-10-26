"use client";

import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, Eye, FolderTree, Loader2, Plus, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CreateProjectDialog } from "@/components/project/create-project-dialog";
import { useUploadBusinessRefresh } from "@/components/file/upload-workspace";
import { getSettlementProjects } from "@/services/project";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import { PROJECT_STATUS_MAP, STAGE_COMPLETED_STATUSES, type ProjectDetailVo, type SettlementProjectPage } from "@/types/project";

const PAGE_SIZE = 5;
const money = (value: number) => `¥${Number(value).toLocaleString("zh-CN", { maximumFractionDigits: 2 })}`;

export function SettlementProjectsSection({ project, onSelect, onChange }: {
  project: ProjectDetailVo;
  onSelect: (id: string) => void;
  onChange?: () => void;
}) {
  const [search, setSearch] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{ key: string; data: SettlementProjectPage | null; error: boolean } | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const requestKey = `${project.projectId}:${keyword}:${page}:${revision}`;
  const data = result?.data ?? null;
  const loading = result?.key !== requestKey;
  const error = !loading && !!result?.error;
  useUploadBusinessRefresh(() => setRevision((value) => value + 1));

  useEffect(() => {
    const timer = window.setTimeout(() => setKeyword(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    const controller = new AbortController();
    getSettlementProjects(project.projectId, { keywords: keyword, current: page, pageSize: PAGE_SIZE }, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setResult({ key: requestKey, data: response.data ?? null,
          error: response.code !== ResponseCode.SUCCESS || !response.data });
      })
      .catch((cause) => { if (!controller.signal.aborted && !isAbortError(cause)) setResult({ key: requestKey, data: null, error: true }); });
    return () => controller.abort();
  }, [project.projectId, keyword, page, revision, requestKey]);

  const pages = Math.max(1, Math.ceil((data?.projects.total ?? 0) / PAGE_SIZE));
  return (
    <section aria-label="结算子项目">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <h4 className="flex items-center gap-1.5 text-sm font-semibold text-slate-700">
          <FolderTree className="h-4 w-4 text-slate-400" />结算子项目
        </h4>
        {data?.canCreate && !error && (
          <Button size="sm" onClick={() => setCreateOpen(true)}><Plus className="h-3.5 w-3.5 mr-1" />新增结算子项目</Button>
        )}
      </div>
      {data && !error && (
        <dl className="grid grid-cols-2 gap-3 pb-3 text-xs sm:grid-cols-4">
          <div><dt className="text-slate-500">可见子项目</dt><dd className="mt-1 font-medium">{data.summary.projectCount} 个</dd></div>
          <div><dt className="text-slate-500">已完成</dt><dd className="mt-1 font-medium">{data.summary.completedCount} 个</dd></div>
          <div className="min-w-0"><dt className="text-slate-500">子项目合同合计</dt><dd className="mt-1 break-all font-medium">{money(data.summary.contractAmount)}</dd></div>
          <div className="min-w-0"><dt className="text-slate-500">子项目效益合计</dt><dd className="mt-1 break-all font-medium">{money(data.summary.benefitAmount)}</dd></div>
        </dl>
      )}
      <div className="relative mb-3">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <input aria-label="搜索结算子项目" placeholder="搜索子项目名称或编号" value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(1); }}
          className="w-full rounded-md border border-slate-200 py-2 pl-10 pr-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div className="min-h-24" aria-busy={loading}>
        {loading ? <div className="flex justify-center py-8"><Loader2 aria-label="子项目加载中" className="h-5 w-5 animate-spin text-slate-400" /></div>
          : error ? <div className="py-4 text-center text-sm text-rose-600">加载失败 <button className="underline" onClick={() => setRevision((value) => value + 1)}>重试</button></div>
          : !data?.projects.records.length ? <p className="py-6 text-center text-sm text-slate-400">{keyword ? "未找到匹配的结算子项目" : "暂无可查看的结算子项目"}</p>
          : <ul className="divide-y divide-slate-100">
            {data.projects.records.map((child) => {
              const stages = child.projectStages ?? [];
              const completed = stages.filter((stage) => STAGE_COMPLETED_STATUSES.includes(stage.stageStatus)).length;
              return <li key={child.projectId}>
                <button onClick={() => onSelect(child.projectId)} className="flex w-full items-center gap-3 py-3 text-left hover:bg-slate-50">
                  <div className="min-w-0 flex-1">
                    <div className="break-words text-sm font-medium text-slate-800">{child.projectName}</div>
                    <div className="mt-1 break-all text-xs text-slate-400">{child.projectCode}</div>
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
                      <span>{child.archiveStatus === 1 ? "已归档" : PROJECT_STATUS_MAP[child.projectStatus]}</span>
                      <span>阶段 {completed}/{stages.length}</span>
                      <span>合同 {money(child.contractAmount?.contractAmount ?? 0)}</span>
                    </div>
                  </div>
                  <Eye className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                </button>
              </li>;
            })}
          </ul>}
      </div>
      <div className="mt-2 flex items-center justify-between gap-2 text-xs text-slate-500">
        <span>共 {data?.projects.total ?? 0} 个 · 第 {page}/{pages} 页</span>
        <div className="flex gap-1">
          <Button size="icon" variant="ghost" className="h-8 w-8" title="上一页子项目" aria-label="上一页子项目" disabled={loading || page <= 1} onClick={() => setPage((value) => value - 1)}><ChevronLeft className="h-4 w-4" /></Button>
          <Button size="icon" variant="ghost" className="h-8 w-8" title="下一页子项目" aria-label="下一页子项目" disabled={loading || page >= pages} onClick={() => setPage((value) => value + 1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
      </div>
      <CreateProjectDialog open={createOpen} onOpenChange={setCreateOpen} parentProject={project} onSuccess={() => {
        setPage(1); setSearch(""); setRevision((value) => value + 1); onChange?.();
      }} />
    </section>
  );
}
