"use client";

import { useState, useEffect, useCallback } from "react";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  getProjectDetail,
  getProjectTypes,
  getUserList,
  updateProject,
} from "@/services/project";
import { ResponseCode } from "@/types/api";
import type { UpdateProjectParams, UserListItem } from "@/types/project";
import { PROJECT_STATUS_MAP } from "@/types/project";

interface EditProjectDialogProps {
  projectId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

interface ProjectTypeOption {
  projectTypeId: number;
  projectTypeName: string;
  projectTypeCode: string;
}

export function EditProjectDialog({
  projectId,
  open,
  onOpenChange,
  onSuccess,
}: EditProjectDialogProps) {
  const [form, setForm] = useState<UpdateProjectParams>({ projectId: "" });
  const [projectTypes, setProjectTypes] = useState<ProjectTypeOption[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [step, setStep] = useState(1);

  const fetchOptions = useCallback(async () => {
    try {
      const [typesRes, usersRes] = await Promise.all([
        getProjectTypes(),
        getUserList(),
      ]);
      if (typesRes.code === ResponseCode.SUCCESS && typesRes.data) {
        setProjectTypes(typesRes.data);
      }
      if (usersRes.code === ResponseCode.SUCCESS && usersRes.data) {
        setUsers(usersRes.data.records ?? []);
      }
    } catch {
      // 静默
    }
  }, []);

  // 加载项目详情并填充表单
  useEffect(() => {
    if (!open || !projectId) return;
    setStep(1);
    setError("");

    let cancelled = false;

    async function load() {
      setLoading(true);
      await fetchOptions();

      try {
        const res = await getProjectDetail(projectId!);
        if (cancelled) return;
        if (res.code === ResponseCode.SUCCESS && res.data) {
          const d = res.data;
          const chargeIds: string[] = [];
          const memberIds: string[] = [];
          (d.projectMemberList ?? []).forEach((m) => {
            const uid = String(m.userId);
            if (Number(m.projectRoleId) === 101) {
              chargeIds.push(uid);
            } else {
              memberIds.push(uid);
            }
          });

          setForm({
            projectId: d.projectId,
            projectName: d.projectName,
            projectCode: d.projectCode,
            projectType: d.projectType ? Number(d.projectType.projectTypeId) : undefined,
            projectStatus: d.projectStatus,
            contractType: d.contract?.contractType,
            contractAmount: d.contract?.contractAmount,
            baseAmount: d.contract?.baseAmount,
            benefitRule: d.contract?.benefitRules,
            preStartTime: d.contract?.preStartDate?.slice(0, 10) ?? d.preStartTime?.slice(0, 10),
            preEndTime: d.contract?.preEndDate?.slice(0, 10) ?? d.preEndTime?.slice(0, 10),
            projectCharges: chargeIds,
            projectMembers: memberIds,
          });
        } else {
          setError(res.msg || "获取项目信息失败");
        }
      } catch {
        if (!cancelled) setError("网络异常");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => { cancelled = true; };
  }, [open, projectId, fetchOptions]);

  const updateField = <K extends keyof UpdateProjectParams>(
    key: K,
    value: UpdateProjectParams[K]
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const toggleUserInList = (
    key: "projectCharges" | "projectMembers",
    userId: string
  ) => {
    setForm((prev) => {
      const list = prev[key] ?? [];
      const next = list.includes(userId)
        ? list.filter((id) => id !== userId)
        : [...list, userId];
      return { ...prev, [key]: next };
    });
  };

  const handleSubmit = async () => {
    if (!form.projectName?.trim()) {
      setError("项目名称不能为空");
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      const params: UpdateProjectParams = {
        ...form,
        preStartTime: form.preStartTime ? `${form.preStartTime}T00:00:00` : undefined,
        preEndTime: form.preEndTime ? `${form.preEndTime}T00:00:00` : undefined,
      };
      const res = await updateProject(params);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("项目更新成功");
        onOpenChange(false);
        onSuccess();
      } else {
        // toast 由 request.ts 统一提示；这里仅同步到表单错误态
        setError(res.msg || "更新失败");
      }
    } catch {
      // 网络错误也由 request.ts 提示
      setError("网络异常，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>编辑项目</DialogTitle>
          <DialogDescription>修改项目信息、合同信息和成员</DialogDescription>
        </DialogHeader>

        {loading && (
          <div className="flex justify-center py-12">
            <Loader2 className="w-6 h-6 text-blue-500 animate-spin" />
          </div>
        )}

        {!loading && error && !form.projectName && (
          <div className="py-12 text-center">
            <p className="text-sm text-rose-500">{error}</p>
          </div>
        )}

        {!loading && form.projectName && (
          <>
            {/* Steps */}
            <div className="flex items-center gap-2 my-4">
              {[
                { n: 1, label: "基本信息" },
                { n: 2, label: "合同信息" },
                { n: 3, label: "成员管理" },
              ].map(({ n, label }) => (
                <button
                  key={n}
                  onClick={() => setStep(n)}
                  className={cn(
                    "flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors",
                    step === n
                      ? "bg-blue-600 text-white"
                      : "bg-slate-100 text-slate-500 hover:bg-slate-200"
                  )}
                >
                  <span
                    className={cn(
                      "w-5 h-5 rounded-full flex items-center justify-center text-xs",
                      step === n ? "bg-blue-500 text-white" : "bg-slate-200 text-slate-500"
                    )}
                  >
                    {n}
                  </span>
                  {label}
                </button>
              ))}
            </div>

            {error && (
              <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-sm text-rose-600">
                {error}
              </div>
            )}

            {/* Step 1 */}
            {step === 1 && (
              <div className="space-y-4">
                <FormField label="项目名称" required>
                  <input
                    type="text"
                    value={form.projectName ?? ""}
                    onChange={(e) => updateField("projectName", e.target.value)}
                    className="form-input"
                  />
                </FormField>

                <FormField label="项目编码">
                  <input
                    type="text"
                    value={form.projectCode ?? ""}
                    onChange={(e) => updateField("projectCode", e.target.value)}
                    className="form-input"
                  />
                </FormField>

                <FormField label="项目类型">
                  <select
                    value={form.projectType ?? 0}
                    onChange={(e) => updateField("projectType", Number(e.target.value))}
                    className="form-input"
                  >
                    <option value={0}>请选择项目类型</option>
                    {projectTypes.map((t) => (
                      <option key={t.projectTypeId} value={t.projectTypeId}>
                        {t.projectTypeCode} · {t.projectTypeName}
                      </option>
                    ))}
                  </select>
                </FormField>

                <FormField label="项目状态">
                  <select
                    value={form.projectStatus ?? 0}
                    onChange={(e) => updateField("projectStatus", Number(e.target.value))}
                    className="form-input"
                  >
                    {Object.entries(PROJECT_STATUS_MAP).map(([val, label]) => (
                      <option key={val} value={val}>
                        {label}
                      </option>
                    ))}
                  </select>
                </FormField>

                <div className="grid grid-cols-2 gap-4">
                  <FormField label="预计开始日期">
                    <input
                      type="date"
                      value={form.preStartTime ?? ""}
                      onChange={(e) => updateField("preStartTime", e.target.value)}
                      className="form-input"
                    />
                  </FormField>
                  <FormField label="预计结束日期">
                    <input
                      type="date"
                      value={form.preEndTime ?? ""}
                      onChange={(e) => updateField("preEndTime", e.target.value)}
                      className="form-input"
                    />
                  </FormField>
                </div>
              </div>
            )}

            {/* Step 2 */}
            {step === 2 && (
              <div className="space-y-4">
                <FormField label="合同类型">
                  <select
                    value={form.contractType ?? 0}
                    onChange={(e) => updateField("contractType", Number(e.target.value))}
                    className="form-input"
                  >
                    <option value={0}>基本收费</option>
                    <option value={1}>基本+效益</option>
                  </select>
                </FormField>

                <FormField label="合同金额（元）">
                  <input
                    type="number"
                    value={form.contractAmount ?? ""}
                    onChange={(e) => updateField("contractAmount", Number(e.target.value))}
                    className="form-input"
                    min={0}
                  />
                </FormField>

                {form.contractType === 1 && (
                  <>
                    <FormField label="基础金额（元）">
                      <input
                        type="number"
                        value={form.baseAmount ?? ""}
                        onChange={(e) => updateField("baseAmount", Number(e.target.value))}
                        className="form-input"
                        min={0}
                      />
                    </FormField>
                    <FormField label="效益规则">
                      <input
                        type="text"
                        value={form.benefitRule ?? ""}
                        onChange={(e) => updateField("benefitRule", e.target.value)}
                        className="form-input"
                      />
                    </FormField>
                  </>
                )}
              </div>
            )}

            {/* Step 3 */}
            {step === 3 && (
              <div className="space-y-4">
                <FormField label="项目经理" required>
                  <p className="text-xs text-slate-400 mb-2">
                    已选 {(form.projectCharges ?? []).length} 人
                  </p>
                  <UserSelector
                    users={users}
                    selected={form.projectCharges ?? []}
                    onToggle={(id) => toggleUserInList("projectCharges", id)}
                  />
                </FormField>

                <FormField label="项目成员">
                  <p className="text-xs text-slate-400 mb-2">
                    已选 {(form.projectMembers ?? []).length} 人
                  </p>
                  <UserSelector
                    users={users.filter(
                      (u) => !(form.projectCharges ?? []).includes(u.userId)
                    )}
                    selected={form.projectMembers ?? []}
                    onToggle={(id) => toggleUserInList("projectMembers", id)}
                  />
                </FormField>
              </div>
            )}

            {/* Actions */}
            <div className="flex justify-between pt-4 border-t border-slate-100 mt-4">
              <div>
                {step > 1 && (
                  <Button variant="outline" onClick={() => setStep(step - 1)}>
                    上一步
                  </Button>
                )}
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => onOpenChange(false)}>
                  取消
                </Button>
                {step < 3 ? (
                  <Button
                    className="bg-blue-600 hover:bg-blue-700 text-white"
                    onClick={() => setStep(step + 1)}
                  >
                    下一步
                  </Button>
                ) : (
                  <Button
                    className="bg-blue-600 hover:bg-blue-700 text-white"
                    disabled={submitting}
                    onClick={handleSubmit}
                  >
                    {submitting ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin mr-1" />
                        保存中...
                      </>
                    ) : (
                      "保存修改"
                    )}
                  </Button>
                )}
              </div>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function FormField({
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
        {label}
        {required && <span className="text-rose-500 ml-0.5">*</span>}
      </label>
      {children}
    </div>
  );
}

function UserSelector({
  users,
  selected,
  onToggle,
}: {
  users: UserListItem[];
  selected: string[];
  onToggle: (userId: string) => void;
}) {
  if (users.length === 0) {
    return <p className="text-sm text-slate-400">暂无可选用户</p>;
  }

  return (
    <div className="max-h-48 overflow-y-auto border border-slate-200 rounded-xl p-2 space-y-1">
      {users.map((user) => (
        <label
          key={user.userId}
          className={cn(
            "flex items-center gap-3 px-3 py-2 rounded-lg cursor-pointer transition-colors",
            selected.includes(user.userId)
              ? "bg-blue-50"
              : "hover:bg-slate-50"
          )}
        >
          <input
            type="checkbox"
            checked={selected.includes(user.userId)}
            onChange={() => onToggle(user.userId)}
            className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
          />
          <div className="w-6 h-6 rounded-full bg-blue-100 flex items-center justify-center text-xs font-medium text-blue-600">
            {(user.realName ?? user.username)?.[0] ?? "?"}
          </div>
          <div>
            <span className="text-sm text-slate-700">
              {user.realName || user.username}
            </span>
            {user.realName && (
              <span className="text-xs text-slate-400 ml-2">
                {user.username}
              </span>
            )}
          </div>
        </label>
      ))}
    </div>
  );
}
