"use client";

import { useEffect, useState } from "react";
import {
  Download,
  LayoutGrid,
  List,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  Upload,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { CreateInspectionDialog } from "@/components/inspection/create-inspection-dialog";
import { CreateProjectDialog } from "@/components/project/create-project-dialog";
import { useUploadBusinessRefresh } from "@/components/file/upload-workspace";
import { EditProjectDialog } from "@/components/project/edit-project-dialog";
import { ImportProjectDialog } from "@/components/project/import-project-dialog";
import { ProjectDetailDialog } from "@/components/project/project-detail-dialog";
import {
  ProjectTreeView,
  TreePagination,
  projectMoney,
} from "@/components/project/project-tree-view";
import { useProjectTree } from "@/hooks/use-project-tree";
import { useAuth } from "@/store/auth-context";
import { getAccessToken } from "@/lib/token";
import { cn } from "@/lib/utils";
import {
  deleteProject,
  getExportUrl,
  getProjectTypes,
} from "@/services/project";
import { ResponseCode } from "@/types/api";
import type {
  ProjectListVo,
  ProjectTreeQuery,
  ProjectTreeScope,
} from "@/types/project";

const statuses = [
  { key: "all", label: "全部" },
  { key: "1", label: "进行中" },
  { key: "2", label: "待验收" },
  { key: "4", label: "已完成" },
  { key: "0", label: "未开始" },
  { key: "archived", label: "已归档" },
];
const fieldClass =
  "h-10 min-w-0 max-w-full rounded-md border border-slate-200 bg-white px-3 text-sm";

export function ProjectBrowser({ scope }: { scope: ProjectTreeScope }) {
  const mine = scope === "mine";
  const { user } = useAuth();
  const [search, setSearch] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("all");
  const [level, setLevel] = useState("all");
  const [type, setType] = useState("all");
  const [page, setPage] = useState(1);
  const [mode, setMode] = useState<"card" | "list">(mine ? "card" : "list");
  const [selected, setSelected] = useState<string[]>([]);
  const [types, setTypes] = useState<
    {
      projectTypeId: number;
      projectTypeName: string;
      projectTypeCode: string;
    }[]
  >([]);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [inspection, setInspection] = useState<ProjectListVo | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProjectListVo | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const pageSize = mine ? 9 : 10;
  const query: ProjectTreeQuery = {
    keywords: keyword || undefined,
    projectStatus:
      status === "all" || status === "archived" ? undefined : Number(status),
    projectType: type === "all" ? undefined : Number(type),
    projectLevel: level === "all" ? undefined : Number(level),
    archiveStatus:
      status === "all" && mine ? undefined : status === "archived" ? 1 : 0,
    current: page,
    pageSize,
  };
  const {
    nodes,
    total,
    loading,
    error,
    statistics,
    revision,
    refresh,
    queryKey,
  } = useProjectTree(scope, query);
  useUploadBusinessRefresh(refresh);
  useEffect(() => {
    if (search.trim() === keyword) return;
    const timer = window.setTimeout(() => {
      setKeyword(search.trim());
      setPage(1);
      setSelected([]);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search, keyword]);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const id = params.get("projectId") || params.get("detailId");
    if (id && /^\d+$/.test(id)) {
      setDetailId(id);
      setDetailOpen(true);
    }
  }, []);
  useEffect(() => {
    if (mine) return;
    let active = true;
    getProjectTypes()
      .then((response) => {
        if (active && response.code === ResponseCode.SUCCESS)
          setTypes(response.data ?? []);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [mine]);
  useEffect(() => {
    if (!loading && !error && page > 1 && !nodes.length)
      setPage(Math.max(1, Math.ceil(total / pageSize)));
  }, [loading, error, page, nodes.length, total, pageSize]);

  const resetPage = () => {
    setPage(1);
    setSelected([]);
  };
  const reset = () => {
    setSearch("");
    setStatus("all");
    setLevel("all");
    setType("all");
    resetPage();
  };
  const view = (id: string) => {
    setDetailId(id);
    setDetailOpen(true);
  };
  const isManager = (project: ProjectListVo) =>
    !mine ||
    !!project.projectMemberList?.some(
      (member) =>
        String(member.userId) === String(user?.userId) &&
        Number(member.projectRoleId) === 101,
    );
  const edit = (project: ProjectListVo) => {
    setEditId(project.projectId);
    setEditOpen(true);
  };
  const exportProjects = (ids?: string[]) => {
    const url = getExportUrl(ids),
      token = getAccessToken();
    const separator = url.includes("?") ? "&" : "?";
    window.open(token ? `${url}${separator}token=${token}` : url, "_blank");
  };
  const remove = async () => {
    if (!deleteTarget || deletingId) return;
    const id = deleteTarget.projectId;
    setDeletingId(id);
    try {
      const response = await deleteProject(id);
      if (response.code === ResponseCode.SUCCESS) {
        toast.success("项目已删除");
        setDeleteTarget(null);
        setSelected((previous) => previous.filter((value) => value !== id));
        refresh();
      } else toast.error(response.msg || "删除失败");
    } catch {
      toast.error("删除失败，请稍后重试");
    } finally {
      setDeletingId(null);
    }
  };
  const count = (key: string) => {
    if (!statistics) return "-";
    return (
      {
        all: statistics.allCount,
        "1": statistics.processingCount,
        "2": statistics.pendingAcceptanceCount,
        "4": statistics.completedCount,
        "0": statistics.notStartedCount,
        archived: statistics.archivedCount,
      } as Record<string, number>
    )[key];
  };
  const summary = statistics?.summary;
  const stats = [
    { label: "项目组数", value: summary?.totalCount },
    { label: "子项目数", value: summary?.childCount },
    { label: "进行中项目组", value: summary?.processingCount },
    { label: "待验收项目组", value: summary?.pendingAcceptanceCount },
    { label: "已完成项目组", value: summary?.completedCount },
  ];

  return (
    <div className="min-w-0 space-y-5 p-4 md:p-8">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-900">
          {mine ? "我的项目" : "全部项目"}
        </h1>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setImportOpen(true)}>
            <Upload className="h-4 w-4" />
            导入
          </Button>
          <Button variant="outline" onClick={() => exportProjects()}>
            <Download className="h-4 w-4" />
            导出
          </Button>
          {mine && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" />
              新建项目
            </Button>
          )}
        </div>
      </header>
      {!mine && (
        <section
          aria-label="项目统计"
          className="grid grid-cols-2 gap-3 border-y border-slate-200 py-4 md:grid-cols-3 xl:grid-cols-6"
        >
          {stats.map((item) => (
            <div key={item.label} className="min-w-0">
              <p className="text-xs text-slate-500">{item.label}</p>
              <p className="mt-1 text-xl font-semibold tabular-nums text-slate-800">
                {item.value ?? "-"}
              </p>
            </div>
          ))}
          <div className="min-w-0 text-xs text-slate-500">
            <p>匹配主项目/独立项目合同额</p>
            <p className="mt-1 break-all font-semibold tabular-nums text-slate-800">
              {summary ? projectMoney(summary.rootContractAmount) : "-"}
            </p>
            <p className="mt-1">子项目合同额</p>
            <p className="break-all tabular-nums">
              {summary ? projectMoney(summary.childContractAmount) : "-"}
            </p>
          </div>
        </section>
      )}
      <div className="flex flex-wrap items-center gap-3">
        {mine && (
          <div
            className="flex max-w-full flex-wrap gap-1"
            role="group"
            aria-label="项目状态"
          >
            {statuses.map((item) => (
              <button
                type="button"
                key={item.key}
                aria-pressed={status === item.key}
                onClick={() => {
                  setStatus(item.key);
                  resetPage();
                }}
                title={`${item.label}项目组（含匹配的子项目）`}
                className={cn(
                  "inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm",
                  status === item.key
                    ? "bg-blue-600 text-white"
                    : "text-slate-600 hover:bg-slate-100",
                )}
              >
                {item.label}
                <span className="text-xs tabular-nums opacity-80">
                  {count(item.key)}
                </span>
              </button>
            ))}
          </div>
        )}
        <div className="relative min-w-40 flex-1 sm:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            aria-label="搜索项目"
            placeholder="搜索项目名称或编号..."
            className={cn(fieldClass, "w-full pl-10")}
            maxLength={255}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              resetPage();
            }}
          />
        </div>
        {!mine && (
          <select
            aria-label="项目分类"
            className={fieldClass}
            value={type}
            onChange={(event) => {
              setType(event.target.value);
              resetPage();
            }}
          >
            <option value="all">全部分类</option>
            {types.map((item) => (
              <option key={item.projectTypeId} value={item.projectTypeId}>
                {item.projectTypeCode}类 · {item.projectTypeName}
              </option>
            ))}
          </select>
        )}
        <select
          aria-label="项目层级"
          className={fieldClass}
          value={level}
          onChange={(event) => {
            setLevel(event.target.value);
            resetPage();
          }}
        >
          <option value="all">全部项目层级</option>
          <option value="0">仅主项目及独立项目</option>
          <option value="1">结算子项目</option>
        </select>
        {!mine && (
          <select
            aria-label="项目状态"
            className={fieldClass}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              resetPage();
            }}
          >
            {statuses.map((item) => (
              <option key={item.key} value={item.key}>
                {item.key === "all" ? "全部状态" : item.label}
              </option>
            ))}
          </select>
        )}
        {mine && (
          <div
            className="flex rounded-md border border-slate-200 p-1"
            role="group"
            aria-label="项目视图"
          >
            {(
              [
                { value: "card", label: "卡片视图", Icon: LayoutGrid },
                { value: "list", label: "列表视图", Icon: List },
              ] as const
            ).map(({ value, label, Icon }) => (
              <button
                key={value}
                type="button"
                title={label}
                aria-label={label}
                aria-pressed={mode === value}
                onClick={() => setMode(value)}
                className={cn(
                  "inline-flex h-8 w-8 items-center justify-center rounded-md",
                  mode === value
                    ? "bg-blue-600 text-white"
                    : "text-slate-500 hover:bg-slate-100",
                )}
              >
                <Icon className="h-4 w-4" />
              </button>
            ))}
          </div>
        )}
        <button
          type="button"
          title="清除筛选"
          aria-label="清除筛选"
          onClick={reset}
          className="inline-flex h-9 w-9 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100"
        >
          <RefreshCw className="h-4 w-4" />
        </button>
      </div>
      {!mine && selected.length > 0 && (
        <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600">
          <span>已选择 {selected.length} 个项目</span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => exportProjects(selected)}
          >
            <Download className="h-4 w-4" />
            批量导出
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected([])}>
            取消选择
          </Button>
        </div>
      )}
      <ProjectTreeView
        key={queryKey}
        scope={scope}
        nodes={nodes}
        query={query}
        revision={revision}
        mode={mode}
        loading={loading}
        error={error}
        onRetry={refresh}
        onView={view}
        canEdit={isManager}
        onEdit={edit}
        onInspection={mine ? setInspection : undefined}
        onDelete={!mine ? setDeleteTarget : undefined}
        deletingId={deletingId}
        selected={selected}
        onSelectionChange={!mine ? setSelected : undefined}
      />
      {!loading && !error && total > 0 && (
        <TreePagination
          page={page}
          total={total}
          pageSize={pageSize}
          onChange={(value) => {
            setPage(value);
            setSelected([]);
          }}
        />
      )}
      {mine && !loading && summary && (
        <p className="text-xs text-slate-500">
          项目组 {summary.totalCount} 个 · 子项目 {summary.childCount}{" "}
          个
        </p>
      )}
      <ImportProjectDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={refresh}
      />
      <EditProjectDialog
        projectId={editId}
        open={editOpen}
        onOpenChange={setEditOpen}
        onSuccess={refresh}
      />
      {mine && (
        <CreateProjectDialog
          open={createOpen}
          onOpenChange={setCreateOpen}
          onSuccess={refresh}
        />
      )}
      {mine && (
        <CreateInspectionDialog
          open={!!inspection}
          onOpenChange={(open) => {
            if (!open) setInspection(null);
          }}
          initialProject={inspection}
          onSuccess={refresh}
        />
      )}
      <ProjectDetailDialog
        projectId={detailId}
        open={detailOpen}
        onOpenChange={setDetailOpen}
        onStageChange={refresh}
      />
      <Dialog
        open={!!deleteTarget}
        onOpenChange={(open) => {
          if (!open && !deletingId) setDeleteTarget(null);
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>删除项目</DialogTitle>
            <DialogDescription>
              此操作不可挽回，请确定是否需要删除。
            </DialogDescription>
          </DialogHeader>
          {deleteTarget && (
            <div className="rounded-md border border-rose-100 bg-rose-50 p-3 text-sm text-rose-700">
              <p className="break-words font-medium">
                {deleteTarget.projectName}
              </p>
              <p className="mt-2 text-xs">
                删除后将同时逻辑删除该项目下的合同、项目文件、验工、审批、产值、工时、回款等业务数据。
              </p>
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              disabled={!!deletingId}
              onClick={() => setDeleteTarget(null)}
            >
              取消
            </Button>
            <Button
              className="bg-rose-600 hover:bg-rose-700"
              disabled={!!deletingId}
              onClick={remove}
            >
              {deletingId && <Loader2 className="h-4 w-4 animate-spin" />}
              {deletingId ? "删除中..." : "确认删除"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
