"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Pencil, Trash2, Tag, Search } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { isAbortError } from "@/lib/request";
import { cn } from "@/lib/utils";
import {
  getProjectTypeList,
  createProjectType,
  updateProjectType,
  deleteProjectType,
} from "@/services/project-type";
import type { ProjectTypeVo } from "@/services/project-type";
import { ResponseCode } from "@/types/api";

const PAGE_SIZE = 10;

function formatTime(value?: string | null) {
  return value?.replace("T", " ").slice(0, 16) || "-";
}

export default function ProjectTypeConfigPage() {
  const [items, setItems] = useState<ProjectTypeVo[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");

  // 弹窗状态
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectTypeVo | null>(null);
  const [formCode, setFormCode] = useState("");
  const [formName, setFormName] = useState("");
  const [formStatus, setFormStatus] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    setCurrentPage(1);
  }, [statusFilter]);

  const fetchList = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      try {
        const res = await getProjectTypeList(
          {
            keyword: debouncedKeyword || undefined,
            status: statusFilter === "all" ? undefined : Number(statusFilter),
            current: currentPage,
            pageSize: PAGE_SIZE,
          },
          signal
        );
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setItems(res.data.records ?? []);
          setTotal(res.data.total ?? 0);
        }
      } catch (err) {
        if (!isAbortError(err)) {
          setItems([]);
          setTotal(0);
        }
      } finally {
        setLoading(false);
      }
    },
    [debouncedKeyword, statusFilter, currentPage]
  );

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const openCreate = () => {
    setEditing(null);
    setFormCode("");
    setFormName("");
    setFormStatus(1);
    setFormOpen(true);
  };

  const openEdit = (item: ProjectTypeVo) => {
    setEditing(item);
    setFormCode(item.projectTypeCode);
    setFormName(item.projectTypeName);
    setFormStatus(item.projectTypeStatus);
    setFormOpen(true);
  };

  const handleSubmit = async () => {
    if (!formCode.trim() || !formName.trim()) {
      toast.error("编码和名称不能为空");
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        projectTypeCode: formCode.trim().toUpperCase(),
        projectTypeName: formName.trim(),
        projectTypeStatus: formStatus,
      };
      const res = editing
        ? await updateProjectType({ ...payload, projectTypeId: editing.projectTypeId })
        : await createProjectType(payload);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(editing ? "修改成功" : "创建成功");
        setFormOpen(false);
        fetchList();
      } else {
        toast.error(res.msg || "操作失败");
      }
    } catch {
      toast.error("操作失败");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    try {
      const res = await deleteProjectType(deleteId);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("删除成功");
        setDeleteId(null);
        fetchList();
      } else {
        toast.error(res.msg || "删除失败");
      }
    } catch {
      toast.error("删除失败");
    }
  };

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="p-4 md:p-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">项目类型管理</h1>
          <p className="text-slate-500 text-sm mt-1">配置项目的分类类型（如 A类、B类 等）。</p>
        </div>
        <Button onClick={openCreate} className="gap-2">
          <Plus className="w-4 h-4" /> 新建类型
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="搜索编码或名称..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="all">全部状态</option>
          <option value="1">启用</option>
          <option value="0">禁用</option>
        </select>
      </div>

      {/* Table */}
      {loading && <TablePageSkeleton columns={5} rows={5} />}

      {!loading && items.length === 0 && (
        <div className="flex flex-col items-center justify-center py-20 text-slate-400">
          <Tag className="w-12 h-12 mb-3" />
          <p className="text-sm">暂无项目类型数据</p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-x-auto">
          <table className="w-full">
            <thead className="bg-slate-50/50">
              <tr className="text-slate-500 text-xs uppercase tracking-wider">
                <th className="text-left py-4 px-6 font-semibold">类型编码</th>
                <th className="text-left py-4 px-4 font-semibold">类型名称</th>
                <th className="text-center py-4 px-4 font-semibold">状态</th>
                <th className="text-left py-4 px-4 font-semibold">创建时间</th>
                <th className="text-right py-4 px-6 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              {items.map((item) => (
                <tr key={item.projectTypeId} className="hover:bg-slate-50/50">
                  <td className="py-3 px-6">
                    <span className="inline-flex items-center gap-1.5 font-mono font-medium text-slate-800">
                      <span className={cn(
                        "w-2 h-2 rounded-full",
                        item.projectTypeStatus === 1 ? "bg-emerald-500" : "bg-slate-300"
                      )} />
                      {item.projectTypeCode}
                    </span>
                  </td>
                  <td className="py-3 px-4 font-medium text-slate-800">{item.projectTypeName}</td>
                  <td className="py-3 px-4 text-center">
                    <Badge variant={item.projectTypeStatus === 1 ? "default" : "secondary"}>
                      {item.projectTypeStatus === 1 ? "启用" : "禁用"}
                    </Badge>
                  </td>
                  <td className="py-3 px-4 text-slate-500">{formatTime(item.createdTime)}</td>
                  <td className="py-3 px-6 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => openEdit(item)}
                        className="p-2 rounded-lg text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-colors"
                        title="编辑"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => setDeleteId(item.projectTypeId)}
                        className="p-2 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                        title="删除"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-500">
          <span>共 {total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => p - 1)}
            >
              上一页
            </Button>
            <span>
              {currentPage} / {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage >= totalPages}
              onClick={() => setCurrentPage((p) => p + 1)}
            >
              下一页
            </Button>
          </div>
        </div>
      )}

      {/* Create/Edit Dialog */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6 space-y-5">
            <h2 className="text-lg font-bold text-slate-900">
              {editing ? "编辑项目类型" : "新建项目类型"}
            </h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  类型编码 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={formCode}
                  onChange={(e) => setFormCode(e.target.value)}
                  placeholder="如 A、B、C"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  类型名称 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  placeholder="如 全程结算、全过程"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">状态</label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setFormStatus(formStatus === 1 ? 0 : 1)}
                    className={cn(
                      "relative inline-flex h-6 w-11 items-center rounded-full transition-colors",
                      formStatus === 1 ? "bg-blue-600" : "bg-slate-300"
                    )}
                  >
                    <span
                      className={cn(
                        "inline-block h-4 w-4 transform rounded-full bg-white transition-transform",
                        formStatus === 1 ? "translate-x-6" : "translate-x-1"
                      )}
                    />
                  </button>
                  <span className="text-sm text-slate-600">
                    {formStatus === 1 ? "启用" : "禁用"}
                  </span>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <Button variant="outline" onClick={() => setFormOpen(false)} disabled={submitting}>
                取消
              </Button>
              <Button onClick={handleSubmit} disabled={submitting}>
                {submitting ? "提交中..." : editing ? "保存" : "创建"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirm Dialog */}
      {deleteId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-6 space-y-4">
            <h2 className="text-lg font-bold text-slate-900">确认删除</h2>
            <p className="text-sm text-slate-500">
              删除后不可恢复，已有项目引用该类型时可能影响显示。确定要删除吗？
            </p>
            <div className="flex justify-end gap-3">
              <Button variant="outline" onClick={() => setDeleteId(null)}>
                取消
              </Button>
              <Button variant="destructive" onClick={handleDelete}>
                删除
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
