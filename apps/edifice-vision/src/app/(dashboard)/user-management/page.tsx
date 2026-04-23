"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Plus,
  Search,
  Upload,
  Users,
  Pencil,
  Trash2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { isAbortError } from "@/lib/request";
import { TablePageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  EMPLOYMENT_STATUS_MAP,
  GENDER_MAP,
} from "@/types/auth";
import {
  createUser,
  deleteUser,
  getUserList,
  updateUser,
  type CreateUserParams,
  type SysUserListItem,
  type UpdateUserParams,
} from "@/services/user";
import { ImportUserDialog } from "@/components/user/import-user-dialog";

type EmploymentTab = "all" | "active" | "resigned";

const employmentFilterMap: Record<EmploymentTab, number | undefined> = {
  all: undefined,
  active: 1,
  resigned: 0,
};

const employmentBadgeStyles: Record<number, string> = {
  0: "bg-slate-200 text-slate-600",
  1: "bg-emerald-100 text-emerald-700",
};

const statusBadgeStyles: Record<number, string> = {
  0: "bg-rose-100 text-rose-700",
  1: "bg-blue-100 text-blue-700",
};

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.slice(0, 10);
}

const PAGE_SIZE = 10;

export default function UserManagementPage() {
  const [activeTab, setActiveTab] = useState<EmploymentTab>("all");
  const [searchText, setSearchText] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [items, setItems] = useState<SysUserListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<SysUserListItem | null>(null);
  const [importOpen, setImportOpen] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 300);
    return () => clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, debouncedSearch]);

  const fetchList = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      // 后端 keywords 做 OR 匹配 用户名 / 姓名 / 工号 / 手机号
      const res = await getUserList(
        {
          keywords: debouncedSearch || undefined,
          employmentStatus: employmentFilterMap[activeTab],
          current: currentPage,
          pageSize: PAGE_SIZE,
        },
        signal
      );
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setItems(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      }
      setLoading(false);
    } catch (err) {
      if (isAbortError(err)) return;
      setItems([]);
      setTotal(0);
      setLoading(false);
    }
  }, [activeTab, debouncedSearch, currentPage]);

  useEffect(() => {
    const controller = new AbortController();
    fetchList(controller.signal);
    return () => controller.abort();
  }, [fetchList]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const handleDelete = async (id: string) => {
    if (!confirm("确定删除该用户吗？此操作不可撤销。")) return;
    setActionLoading(id);
    try {
      const res = await deleteUser(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("删除成功");
        fetchList();
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setActionLoading(null); }
  };

  const tabs: { key: EmploymentTab; label: string }[] = [
    { key: "all", label: "全部" },
    { key: "active", label: "在职" },
    { key: "resigned", label: "离职" },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">用户管理</h1>
          <p className="text-slate-500 text-sm mt-1">
            管理系统用户，维护花名册信息（新建默认初始密码 12345678）
          </p>
        </div>
        <div className="flex gap-3">
          <Button
            variant="outline"
            onClick={() => setImportOpen(true)}
            className="flex items-center gap-2"
          >
            <Upload className="w-4 h-4" /> 导入
          </Button>
          <Button
            onClick={() => { setEditing(null); setFormOpen(true); }}
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
          >
            <Plus className="w-4 h-4" /> 新建用户
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex bg-white rounded-xl p-1 shadow-sm border border-slate-100">
          {tabs.map((item) => (
            <button
              key={item.key}
              onClick={() => setActiveTab(item.key)}
              className={cn(
                "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                activeTab === item.key ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 hover:text-slate-700"
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="搜索用户名 / 姓名 / 工号 / 手机号..."
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm w-80 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {loading && <TablePageSkeleton columns={5} rows={5} />}

      {!loading && items.length > 0 && (
        <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-slate-50/50">
              <tr className="text-slate-500 text-xs uppercase tracking-wider">
                <th className="text-left py-4 px-6 font-semibold">用户</th>
                <th className="text-left py-4 px-4 font-semibold">工号</th>
                <th className="text-left py-4 px-4 font-semibold">职务 / 职称</th>
                <th className="text-left py-4 px-4 font-semibold">联系方式</th>
                <th className="text-left py-4 px-4 font-semibold">入职日期</th>
                <th className="text-center py-4 px-4 font-semibold">在职状态</th>
                <th className="text-center py-4 px-4 font-semibold">账号</th>
                <th className="text-right py-4 px-6 font-semibold">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => {
                const isBusy = actionLoading === item.userId;
                const genderLabel = item.gender !== null && item.gender !== undefined
                  ? GENDER_MAP[item.gender] : null;
                return (
                  <tr key={item.userId} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-4 px-6">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center text-sm font-semibold shrink-0 overflow-hidden">
                          {item.avatar ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={item.avatar} alt={item.realName ?? item.username} className="w-full h-full object-cover" />
                          ) : (
                            (item.realName ?? item.username ?? "U").charAt(0)
                          )}
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-slate-800 truncate">
                            {item.realName || item.username}
                            {genderLabel && <span className="ml-2 text-xs text-slate-400 font-normal">{genderLabel}</span>}
                          </p>
                          <p className="text-xs text-slate-400 truncate">@{item.username}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-4 text-sm text-slate-600">{item.employeeNo ?? "-"}</td>
                    <td className="py-4 px-4">
                      <p className="text-sm text-slate-700">{item.position ?? "-"}</p>
                      {item.professionalTitle && (
                        <p className="text-xs text-slate-400">{item.professionalTitle}</p>
                      )}
                    </td>
                    <td className="py-4 px-4">
                      <p className="text-sm text-slate-600">{item.phone ?? "-"}</p>
                      {item.email && <p className="text-xs text-slate-400 truncate max-w-[180px]">{item.email}</p>}
                    </td>
                    <td className="py-4 px-4 text-sm text-slate-500">{formatDate(item.entryDate)}</td>
                    <td className="py-4 px-4 text-center">
                      {item.employmentStatus !== null && item.employmentStatus !== undefined && (
                        <Badge variant="secondary" className={cn("text-xs", employmentBadgeStyles[item.employmentStatus])}>
                          {EMPLOYMENT_STATUS_MAP[item.employmentStatus] ?? "-"}
                        </Badge>
                      )}
                    </td>
                    <td className="py-4 px-4 text-center">
                      <Badge variant="secondary" className={cn("text-xs", statusBadgeStyles[item.status])}>
                        {item.status === 1 ? "启用" : "禁用"}
                      </Badge>
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => { setEditing(item); setFormOpen(true); }}
                          disabled={isBusy}
                          title="编辑"
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors disabled:opacity-50"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDelete(item.userId)}
                          disabled={isBusy}
                          title="删除"
                          className="p-1.5 text-slate-400 hover:text-rose-500 hover:bg-rose-50 rounded-lg transition-colors disabled:opacity-50"
                        >
                          {isBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {!loading && items.length === 0 && (
        <div className="glass-card rounded-2xl py-16 text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Users className="w-8 h-8 text-slate-400" />
          </div>
          <h3 className="text-lg font-semibold text-slate-800 mb-2">暂无用户</h3>
          <p className="text-sm text-slate-500">当前筛选条件下没有找到用户</p>
        </div>
      )}

      {!loading && total > 0 && (
        <div className="flex justify-between items-center pt-2">
          <p className="text-sm text-slate-500">
            共 <span className="font-semibold text-slate-800">{total}</span> 条
          </p>
          <div className="flex items-center gap-2">
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => p - 1)}
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <button
                key={page}
                onClick={() => setCurrentPage(page)}
                className={cn(
                  "px-3 py-1.5 text-sm font-medium rounded-lg",
                  page === currentPage ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-100"
                )}
              >
                {page}
              </button>
            ))}
            <button
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
              disabled={currentPage >= totalPages}
              onClick={() => setCurrentPage((p) => p + 1)}
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      <UserFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        editing={editing}
        onSuccess={fetchList}
      />

      <ImportUserDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={fetchList}
      />
    </div>
  );
}

// ==================== 新建 / 编辑弹窗 ====================

type FormState = Omit<CreateUserParams, "username"> & { username: string };

const INITIAL_FORM: FormState = { username: "" };

function UserFormDialog({
  open,
  onOpenChange,
  editing,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  editing: SysUserListItem | null;
  onSuccess: () => void;
}) {
  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [status, setStatus] = useState<number>(1);
  const [employmentStatus, setEmploymentStatus] = useState<number>(1);
  const [resignDate, setResignDate] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      setForm({
        username: editing.username ?? "",
        employeeNo: editing.employeeNo ?? "",
        realName: editing.realName ?? "",
        gender: editing.gender ?? undefined,
        email: editing.email ?? "",
        phone: editing.phone ?? "",
        avatar: editing.avatar ?? "",
        position: editing.position ?? "",
        professionalTitle: editing.professionalTitle ?? "",
        entryDate: editing.entryDate ?? "",
      });
      setStatus(editing.status);
      setEmploymentStatus(editing.employmentStatus ?? 1);
      setResignDate("");
    } else {
      setForm(INITIAL_FORM);
      setStatus(1);
      setEmploymentStatus(1);
      setResignDate("");
    }
  }, [open, editing]);

  const patch = (p: Partial<FormState>) => setForm((prev) => ({ ...prev, ...p }));

  const handleSubmit = async () => {
    if (!form.username.trim()) { toast.error("请输入用户名"); return; }

    setSubmitting(true);
    try {
      // 过滤空字符串字段，避免把空值写入
      const cleaned: Record<string, unknown> = {};
      Object.entries(form).forEach(([k, v]) => {
        if (v !== undefined && v !== "") cleaned[k] = v;
      });

      if (editing) {
        const payload: UpdateUserParams = {
          ...(cleaned as Partial<CreateUserParams>),
          userId: editing.userId,
          employmentStatus,
          status,
          resignDate: employmentStatus === 0 && resignDate ? resignDate : undefined,
        };
        const res = await updateUser(payload);
        if (res.code === ResponseCode.SUCCESS) {
          toast.success("更新成功");
          onOpenChange(false);
          onSuccess();
        }
      } else {
        const payload: CreateUserParams = {
          ...(cleaned as Partial<CreateUserParams>),
          username: form.username.trim(),
          employmentStatus,
        };
        const res = await createUser(payload);
        if (res.code === ResponseCode.SUCCESS) {
          toast.success("创建成功，初始密码 12345678");
          onOpenChange(false);
          onSuccess();
        }
      }
    } catch { /* 由 request.ts 提示 */ }
    finally { setSubmitting(false); }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-2xl overflow-hidden flex flex-col max-h-[90vh]">
        <div className="p-6 border-b border-slate-100 flex items-center justify-between shrink-0">
          <h2 className="text-xl font-bold text-slate-800">
            {editing ? "编辑用户" : "新建用户"}
          </h2>
          <button
            onClick={() => onOpenChange(false)}
            className="text-slate-400 hover:text-slate-600"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 space-y-5 overflow-y-auto flex-1">
          <section>
            <h3 className="text-sm font-semibold text-slate-700 mb-3">账号信息</h3>
            <div className="grid grid-cols-2 gap-4">
              <Field label="用户名" required>
                <input
                  className="form-input"
                  value={form.username}
                  onChange={(e) => patch({ username: e.target.value })}
                  disabled={!!editing}
                  placeholder={editing ? "" : "登录用户名"}
                />
              </Field>
              <Field label="员工编号">
                <input
                  className="form-input"
                  value={form.employeeNo ?? ""}
                  onChange={(e) => patch({ employeeNo: e.target.value })}
                />
              </Field>
            </div>
          </section>

          <section>
            <h3 className="text-sm font-semibold text-slate-700 mb-3">基本信息</h3>
            <div className="grid grid-cols-2 gap-4">
              <Field label="真实姓名">
                <input
                  className="form-input"
                  value={form.realName ?? ""}
                  onChange={(e) => patch({ realName: e.target.value })}
                />
              </Field>
              <Field label="性别">
                <select
                  className="form-input"
                  value={form.gender ?? ""}
                  onChange={(e) => patch({ gender: e.target.value === "" ? undefined : Number(e.target.value) })}
                >
                  <option value="">请选择</option>
                  <option value="0">男</option>
                  <option value="1">女</option>
                  <option value="2">其他</option>
                </select>
              </Field>
              <Field label="邮箱">
                <input
                  type="email"
                  className="form-input"
                  value={form.email ?? ""}
                  onChange={(e) => patch({ email: e.target.value })}
                />
              </Field>
              <Field label="手机号">
                <input
                  className="form-input"
                  value={form.phone ?? ""}
                  onChange={(e) => patch({ phone: e.target.value })}
                />
              </Field>
            </div>
          </section>

          <section>
            <h3 className="text-sm font-semibold text-slate-700 mb-3">岗位</h3>
            <div className="grid grid-cols-2 gap-4">
              <Field label="职务">
                <input
                  className="form-input"
                  value={form.position ?? ""}
                  onChange={(e) => patch({ position: e.target.value })}
                />
              </Field>
              <Field label="职称">
                <input
                  className="form-input"
                  value={form.professionalTitle ?? ""}
                  onChange={(e) => patch({ professionalTitle: e.target.value })}
                />
              </Field>
              <Field label="入职时间">
                <input
                  type="date"
                  className="form-input"
                  value={form.entryDate ?? ""}
                  onChange={(e) => patch({ entryDate: e.target.value })}
                />
              </Field>
              <Field label="在职状态">
                <select
                  className="form-input"
                  value={employmentStatus}
                  onChange={(e) => setEmploymentStatus(Number(e.target.value))}
                >
                  <option value={1}>在职</option>
                  <option value={0}>离职</option>
                </select>
              </Field>
              {editing && employmentStatus === 0 && (
                <Field label="离职时间">
                  <input
                    type="date"
                    className="form-input"
                    value={resignDate}
                    onChange={(e) => setResignDate(e.target.value)}
                  />
                </Field>
              )}
              {editing && (
                <Field label="账号状态">
                  <select
                    className="form-input"
                    value={status}
                    onChange={(e) => setStatus(Number(e.target.value))}
                  >
                    <option value={1}>启用</option>
                    <option value={0}>禁用</option>
                  </select>
                </Field>
              )}
            </div>
          </section>

          {!editing && (
            <p className="text-xs text-slate-400">
              新建用户默认初始密码 <span className="font-mono">12345678</span>，请提醒用户首次登录后修改密码。
              更完整的身份证、出生日期、学历、合同等信息可在用户登录后进入"个人中心"补充。
            </p>
          )}
        </div>

        <div className="p-6 border-t border-slate-100 flex justify-end gap-3 shrink-0">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={submitting}
            className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-2"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {editing ? "保存" : "创建"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-1.5">
        {label} {required && <span className="text-rose-500">*</span>}
      </label>
      {children}
    </div>
  );
}
