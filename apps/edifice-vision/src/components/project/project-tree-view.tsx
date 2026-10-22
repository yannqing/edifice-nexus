"use client";

import { Fragment, useEffect, useState, type ReactNode } from "react";
import {
  Calendar,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Eye,
  FolderOpen,
  Loader2,
  LockKeyhole,
  Paperclip,
  Pencil,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { CardPageSkeleton, TablePageSkeleton } from "@/components/ui/skeleton";
import { getProjectTreeChildren } from "@/services/project";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import { ResponseCode } from "@/types/api";
import {
  PROJECT_STATUS_MAP,
  STAGE_COMPLETED_STATUSES,
  type PageResult,
  type ProjectListVo,
  type ProjectTreeNode,
  type ProjectTreeQuery,
  type ProjectTreeScope,
} from "@/types/project";

const CHILD_SIZE = 5;
export const projectMoney = (value?: number) =>
  `¥${Number(value ?? 0).toLocaleString("zh-CN", { maximumFractionDigits: 2 })}`;
const statusStyles: Record<number, string> = {
  0: "bg-slate-100 text-slate-600",
  1: "bg-blue-50 text-blue-700",
  2: "bg-amber-50 text-amber-700",
  3: "bg-amber-50 text-amber-700",
  4: "bg-emerald-50 text-emerald-700",
};
const iconButton =
  "inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-blue-700 disabled:opacity-50";

type Props = {
  scope: ProjectTreeScope;
  nodes: ProjectTreeNode[];
  query: ProjectTreeQuery;
  revision: number;
  mode: "card" | "list";
  loading: boolean;
  error?: string;
  onRetry: () => void;
  onView: (id: string) => void;
  canEdit: (project: ProjectListVo) => boolean;
  onEdit: (project: ProjectListVo) => void;
  onInspection?: (project: ProjectListVo) => void;
  onDelete?: (project: ProjectListVo) => void;
  deletingId?: string | null;
  selected?: string[];
  onSelectionChange?: (ids: string[]) => void;
};

export function TreePagination({
  page,
  total,
  pageSize,
  onChange,
  label = "项目组",
  loading = false,
}: {
  page: number;
  total: number;
  pageSize: number;
  onChange: (page: number) => void;
  label?: string;
  loading?: boolean;
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 py-3 text-xs text-slate-500">
      <span>
        共 {total} 个{label} · 第 {page}/{pages} 页
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          className={iconButton}
          title={`上一页${label}`}
          aria-label={`上一页${label}`}
          disabled={loading || page <= 1}
          onClick={() => onChange(page - 1)}
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <span className="min-w-12 text-center tabular-nums">
          {page} / {pages}
        </span>
        <button
          type="button"
          className={iconButton}
          title={`下一页${label}`}
          aria-label={`下一页${label}`}
          disabled={loading || page >= pages}
          onClick={() => onChange(page + 1)}
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function ProjectStatus({ project }: { project: ProjectListVo }) {
  return (
    <Badge
      variant="secondary"
      className={cn("shrink-0 text-xs", statusStyles[project.projectStatus])}
    >
      {project.archiveStatus === 1
        ? "已归档"
        : (PROJECT_STATUS_MAP[project.projectStatus] ?? "未开始")}
    </Badge>
  );
}

function Category({ project }: { project: ProjectListVo }) {
  const code = project.projectType?.projectTypeCode?.slice(0, 1) ?? "";
  const colors: Record<string, string> = {
    A: "border-blue-200 bg-blue-50 text-blue-700",
    B: "border-emerald-200 bg-emerald-50 text-emerald-700",
    C: "border-amber-200 bg-amber-50 text-amber-700",
    D: "border-purple-200 bg-purple-50 text-purple-700",
    E: "border-rose-200 bg-rose-50 text-rose-700",
  };
  return (
    <div className="flex min-w-0 max-w-full flex-wrap items-center gap-1.5 text-xs text-slate-500">
      {colors[code] && (
        <Badge variant="outline" className={colors[code]}>
          {code}类
        </Badge>
      )}
      <span className="min-w-0 max-w-full break-words">
        {project.projectType?.projectTypeName ?? "-"}
      </span>
    </div>
  );
}

function Progress({ project }: { project: ProjectListVo }) {
  const stages = project.projectStages ?? [];
  return (
    <div className="min-w-24 text-xs text-slate-500">
      <div className="mb-1 flex items-center gap-2">
        <div className="flex w-24 gap-0.5" aria-label="阶段进度">
          {stages.map((stage) => (
            <span
              key={stage.projectStageId}
              title={stage.stageName}
              className={cn(
                "h-1.5 min-w-0 flex-1 rounded-sm",
                STAGE_COMPLETED_STATUSES.includes(stage.stageStatus)
                  ? "bg-emerald-500"
                  : stage.stageStatus === 4
                    ? "bg-rose-400"
                    : stage.stageStatus === 2
                      ? "bg-amber-400"
                      : stage.stageStatus === 1 &&
                          (stage.completionRatio ?? 0) > 0
                        ? "bg-amber-400"
                        : stage.stageStatus === 1
                          ? "bg-blue-500"
                          : "bg-slate-200",
              )}
            />
          ))}
        </div>
        <span className="whitespace-nowrap tabular-nums">
          {
            stages.filter((stage) =>
              STAGE_COMPLETED_STATUSES.includes(stage.stageStatus),
            ).length
          }
          /{stages.length}
        </span>
      </div>
      <span>{project.projectStage?.stageName ?? "-"}</span>
    </div>
  );
}

function Members({ project }: { project: ProjectListVo }) {
  const members = project.projectMemberList ?? [];
  return (
    <div className="flex items-center -space-x-1.5">
      {members.slice(0, 3).map((member, index) => (
        <span
          key={`${member.userId}:${index}`}
          title={member.realName}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-white bg-slate-100 text-xs text-slate-600"
        >
          {member.realName?.slice(0, 1)}
        </span>
      ))}
      {members.length > 3 && (
        <span className="pl-2 text-xs text-slate-500">
          +{members.length - 3}
        </span>
      )}
      {!members.length && <span className="text-xs text-slate-400">-</span>}
    </div>
  );
}

function Actions({ project, props }: { project: ProjectListVo; props: Props }) {
  const editable = project.archiveStatus !== 1 && props.canEdit(project);
  return (
    <div
      className="flex items-center justify-end gap-1"
      onClick={(event) => event.stopPropagation()}
    >
      <button
        type="button"
        className={iconButton}
        title="查看详情"
        aria-label={`查看${project.projectName}详情`}
        onClick={() => props.onView(project.projectId)}
      >
        <Eye className="h-4 w-4" />
      </button>
      {editable && (
        <button
          type="button"
          className={iconButton}
          title="编辑项目"
          aria-label={`编辑${project.projectName}`}
          onClick={() => props.onEdit(project)}
        >
          <Pencil className="h-4 w-4" />
        </button>
      )}
      {editable && props.onInspection && project.projectStatus === 1 && (
        <button
          type="button"
          className={iconButton}
          title="发起验工"
          aria-label={`为${project.projectName}发起验工`}
          onClick={() => props.onInspection?.(project)}
        >
          <ClipboardCheck className="h-4 w-4" />
        </button>
      )}
      {editable && props.onDelete && (
        <button
          type="button"
          className={cn(iconButton, "hover:text-rose-600")}
          disabled={props.deletingId === project.projectId}
          title="删除项目"
          aria-label={`删除${project.projectName}`}
          onClick={() => props.onDelete?.(project)}
        >
          {props.deletingId === project.projectId ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Trash2 className="h-4 w-4" />
          )}
        </button>
      )}
    </div>
  );
}

function Selection({
  project,
  props,
}: {
  project: ProjectListVo;
  props: Props;
}) {
  if (!props.onSelectionChange) return null;
  return (
    <input
      type="checkbox"
      className="h-4 w-4 shrink-0 accent-blue-600"
      aria-label={`选择${project.projectName}`}
      checked={props.selected?.includes(project.projectId) ?? false}
      onClick={(event) => event.stopPropagation()}
      onChange={() =>
        props.onSelectionChange?.(
          props.selected?.includes(project.projectId)
            ? props.selected.filter((id) => id !== project.projectId)
            : [...(props.selected ?? []), project.projectId],
        )
      }
    />
  );
}

function Children({ node, props }: { node: ProjectTreeNode; props: Props }) {
  const [page, setPage] = useState(1);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<{
    key: string;
    data?: PageResult<ProjectListVo>;
    error?: boolean;
  } | null>(null);
  const query = JSON.stringify({
    ...props.query,
    current: page,
    pageSize: CHILD_SIZE,
  });
  const key = `${props.scope}:${node.projectId}:${query}:${props.revision}:${retry}`;
  const loading = result?.key !== key;
  const data = loading ? undefined : result?.data;
  const error = !loading && result?.error;
  useEffect(() => {
    const controller = new AbortController();
    getProjectTreeChildren(
      props.scope,
      node.projectId,
      JSON.parse(query),
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) return;
        if (response.code === ResponseCode.SUCCESS && response.data) {
          if (page > 1 && !response.data.records.length)
            setPage(Math.max(1, Math.ceil(response.data.total / CHILD_SIZE)));
          setResult({ key, data: response.data });
        } else setResult({ key, error: true });
      })
      .catch((cause) => {
        if (!controller.signal.aborted && !isAbortError(cause))
          setResult({ key, error: true });
      });
    return () => controller.abort();
  }, [props.scope, node.projectId, query, key, page]);
  return (
    <section
      aria-label={`${node.project?.projectName ?? "所属总项目"}的子项目`}
      aria-busy={loading}
      className="min-w-0 border-l-2 border-blue-100 pl-3 sm:pl-4"
      onClick={(event) => event.stopPropagation()}
    >
      {loading ? (
        <div className="flex min-h-24 items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          加载子项目...
        </div>
      ) : error ? (
        <div
          role="alert"
          className="flex min-h-24 items-center gap-2 text-sm text-rose-600"
        >
          子项目加载失败
          <button
            type="button"
            className={iconButton}
            aria-label="重试加载子项目"
            title="重试"
            onClick={() => setRetry((value) => value + 1)}
          >
            <RefreshCw className="h-4 w-4" />
          </button>
        </div>
      ) : !data?.records.length ? (
        <p className="py-5 text-sm text-slate-500">暂无符合条件的子项目</p>
      ) : (
        <ul className="min-w-0 divide-y divide-slate-100">
          {data.records.map((child) => (
            <li
              key={child.projectId}
              className="flex min-w-0 flex-wrap items-center gap-2 py-3 sm:gap-3"
            >
              <Selection project={child} props={props} />
              <button
                type="button"
                onClick={() => props.onView(child.projectId)}
                className="min-w-0 flex-1 basis-40 text-left hover:text-blue-700"
              >
                <div className="break-words text-sm font-medium [overflow-wrap:anywhere]">
                  {child.projectName}
                </div>
                <div className="mt-1 break-all text-xs text-slate-500">
                  {child.projectCode} · 结算子项目
                </div>
              </button>
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
                <span className="tabular-nums">
                  {projectMoney(child.contractAmount?.contractAmount)}
                </span>
                <ProjectStatus project={child} />
              </div>
              <div className="flex w-full items-center justify-between gap-3 sm:w-auto sm:flex-1">
                <Progress project={child} />
                <Actions project={child} props={props} />
              </div>
            </li>
          ))}
        </ul>
      )}
      {!error && (
        <TreePagination
          page={page}
          total={data?.total ?? node.childCount}
          pageSize={CHILD_SIZE}
          onChange={setPage}
          label="子项目"
          loading={loading}
        />
      )}
    </section>
  );
}

export function ProjectTreeView(props: Props) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const filtered = !!(
    props.query.keywords ||
    props.query.projectStatus !== undefined ||
    props.query.projectType !== undefined ||
    props.query.projectLevel === 1 ||
    props.query.archiveStatus === 1
  );
  const isOpen = (node: ProjectTreeNode) =>
    expanded[node.projectId] ?? (filtered && node.childCount > 0);
  const toggle = (node: ProjectTreeNode) =>
    setExpanded((previous) => ({
      ...previous,
      [node.projectId]: !isOpen(node),
    }));
  const expandButton = (node: ProjectTreeNode) =>
    node.childCount > 0 ? (
      <button
        type="button"
        className={iconButton}
        onClick={(event) => {
          event.stopPropagation();
          toggle(node);
        }}
        aria-expanded={isOpen(node)}
        aria-controls={`project-children-${node.projectId}`}
        aria-label={`${isOpen(node) ? "收起" : "展开"}${node.project?.projectName ?? "所属总项目"}的子项目`}
        title={`${isOpen(node) ? "收起" : "展开"}子项目`}
      >
        <ChevronDown
          className={cn(
            "h-4 w-4 transition-transform",
            !isOpen(node) && "-rotate-90",
          )}
        />
      </button>
    ) : (
      <span className="inline-block w-8 shrink-0" />
    );
  const rootTitle = (node: ProjectTreeNode): ReactNode => (
    <div className="min-w-0 flex-1">
      {node.project ? (
        <button
          type="button"
          className="text-left text-sm font-semibold text-slate-800 hover:text-blue-700"
          onClick={(event) => {
            event.stopPropagation();
            props.onView(node.projectId);
          }}
        >
          <span className="break-words [overflow-wrap:anywhere]">
            {node.project.projectName}
          </span>
        </button>
      ) : (
        <span className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600">
          <LockKeyhole className="h-3.5 w-3.5" />
          所属总项目
        </span>
      )}
      <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-xs text-slate-500">
        {node.project && (
          <span className="break-all">{node.project.projectCode}</span>
        )}
        {node.childCount > 0 && (
          <span className="text-blue-700">
            {node.childCount} 个{filtered ? "匹配" : ""}子项目
          </span>
        )}
        {!node.project && <span>仅有子项目权限</span>}
      </div>
    </div>
  );
  if (props.loading)
    return props.mode === "card" ? (
      <CardPageSkeleton cards={6} />
    ) : (
      <TablePageSkeleton columns={7} rows={5} />
    );
  if (props.error)
    return (
      <div
        role="alert"
        className="flex items-center justify-center gap-3 py-12 text-sm text-rose-600"
      >
        {props.error}
        <Button variant="outline" onClick={props.onRetry}>
          <RefreshCw className="h-4 w-4" />
          重试
        </Button>
      </div>
    );
  if (!props.nodes.length)
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-sm text-slate-500">
        <FolderOpen className="h-8 w-8 text-slate-300" />
        暂无符合条件的项目
      </div>
    );
  if (props.mode === "card")
    return (
      <div className="grid min-w-0 grid-cols-1 items-start gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {props.nodes.map((node) => (
          <article
            key={node.projectId}
            aria-label={node.project?.projectName ?? "所属总项目"}
            onClick={() => {
              if (node.project) props.onView(node.projectId);
            }}
            className={cn(
              "min-w-0 rounded-lg border border-slate-200 bg-white p-4 shadow-sm",
              node.project && "cursor-pointer",
            )}
          >
            <div className="mb-3 flex items-start gap-1">
              {expandButton(node)}
              {rootTitle(node)}
            </div>
            {node.project && (
              <>
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                  <Category project={node.project} />
                  <ProjectStatus project={node.project} />
                </div>
                <Progress project={node.project} />
                <div className="my-3 flex items-center justify-between gap-2 border-y border-slate-100 py-3 text-xs text-slate-500">
                  <div>
                    合同金额{" "}
                    <span className="font-medium tabular-nums text-slate-800">
                      {projectMoney(
                        node.project.contractAmount?.contractAmount,
                      )}
                    </span>
                  </div>
                  <span className="inline-flex items-center gap-1">
                    <Paperclip className="h-3.5 w-3.5" />
                    {node.project.fileCount ?? 0}
                  </span>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="inline-flex items-center gap-1 text-xs text-slate-500">
                    <Calendar className="h-3.5 w-3.5" />
                    {(
                      node.project.contractAmount?.preStartDate ??
                      node.project.preStartTime
                    )?.slice(0, 10) ?? "-"}{" "}
                    ~{" "}
                    {(
                      node.project.contractAmount?.preEndDate ??
                      node.project.preEndTime
                    )?.slice(0, 10) ?? "-"}
                  </span>
                  <Members project={node.project} />
                </div>
                <div className="mt-3 border-t border-slate-100 pt-2">
                  <Actions project={node.project} props={props} />
                </div>
              </>
            )}
            {node.childCount > 0 && isOpen(node) && (
              <div
                id={`project-children-${node.projectId}`}
                className="mt-3 border-t border-slate-100 pt-3"
              >
                <Children node={node} props={props} />
              </div>
            )}
          </article>
        ))}
      </div>
    );
  const rootIds = props.nodes
    .filter((node) => node.project)
    .map((node) => node.projectId);
  const allSelected =
    rootIds.length > 0 && rootIds.every((id) => props.selected?.includes(id));
  const columns = props.onSelectionChange ? 8 : 7;
  return (
    <div className="max-w-full overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="w-full min-w-[1000px] table-fixed text-left text-sm">
        <colgroup>
          {props.onSelectionChange && <col className="w-10" />}
          <col />
          <col className="w-32" />
          <col className="w-36" />
          <col className="w-20" />
          <col className="w-36" />
          <col className="w-20" />
          <col className="w-32" />
        </colgroup>
        <thead className="bg-slate-50 text-xs text-slate-500">
          <tr>
            {props.onSelectionChange && (
              <th className="w-10 p-3">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-blue-600"
                  aria-label="选择本页总项目"
                  title="仅选择本页总项目，不包含子项目"
                  checked={allSelected}
                  onChange={() =>
                    props.onSelectionChange?.(
                      allSelected
                        ? (props.selected ?? []).filter(
                            (id) => !rootIds.includes(id),
                          )
                        : [...new Set([...(props.selected ?? []), ...rootIds])],
                    )
                  }
                />
              </th>
            )}
            <th className="min-w-60 p-3 font-medium">项目信息</th>
            <th className="p-3 font-medium">分类</th>
            <th className="p-3 font-medium">合同金额</th>
            <th className="p-3 font-medium">项目成员</th>
            <th className="p-3 font-medium">阶段进度</th>
            <th className="p-3 font-medium">状态</th>
            <th className="p-3 text-right font-medium">操作</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {props.nodes.map((node) => (
            <Fragment key={node.projectId}>
              <tr
                className={cn(
                  "hover:bg-slate-50",
                  node.project && "cursor-pointer",
                )}
                onClick={() => {
                  if (node.project) props.onView(node.projectId);
                }}
              >
                {props.onSelectionChange && (
                  <td className="p-3">
                    {node.project && (
                      <Selection project={node.project} props={props} />
                    )}
                  </td>
                )}
                <td className="max-w-md p-3">
                  <div className="flex items-start gap-1">
                    {expandButton(node)}
                    {rootTitle(node)}
                  </div>
                </td>
                <td className="max-w-52 p-3">
                  {node.project ? <Category project={node.project} /> : "-"}
                </td>
                <td className="break-all p-3 tabular-nums">
                  {node.project
                    ? projectMoney(node.project.contractAmount?.contractAmount)
                    : "-"}
                  {node.project?.contractAmount && (
                    <p className="mt-1 text-xs text-slate-500">
                      {node.project.contractAmount.contractType === 0
                        ? "基本收费"
                        : "基本+效益"}
                    </p>
                  )}
                </td>
                <td className="p-3">
                  {node.project ? <Members project={node.project} /> : "-"}
                </td>
                <td className="p-3">
                  {node.project ? <Progress project={node.project} /> : "-"}
                </td>
                <td className="p-3">
                  {node.project && <ProjectStatus project={node.project} />}
                </td>
                <td className="p-3">
                  {node.project && (
                    <Actions project={node.project} props={props} />
                  )}
                </td>
              </tr>
              {node.childCount > 0 && isOpen(node) && (
                <tr>
                  <td colSpan={columns} className="bg-slate-50/40 p-3 pl-8">
                    <div id={`project-children-${node.projectId}`}>
                      <Children node={node} props={props} />
                    </div>
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
