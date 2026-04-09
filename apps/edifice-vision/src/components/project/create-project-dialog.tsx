"use client";

import { useState, useEffect, useCallback } from "react";
import { toast } from "sonner";
import { Loader2, Upload, X, FileText } from "lucide-react";
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
  createProject,
  getProjectTypes,
  getStageTemplates,
  getUserList,
  uploadDocument,
} from "@/services/project";
import { ResponseCode } from "@/types/api";
import type {
  CreateProjectParams,
  ProjectStageTemplate,
  UserListItem,
  FilesVo,
} from "@/types/project";

interface CreateProjectDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

interface ProjectTypeOption {
  projectTypeId: number;
  projectTypeName: string;
  projectTypeCode: string;
}

const initialForm: CreateProjectParams = {
  projectName: "",
  projectCode: "",
  projectType: 0,
  contractType: 0,
  contractAmount: 0,
  baseAmount: 0,
  benefitRule: "",
  projectCharges: [],
  projectMembers: [],
  signingTime: "",
  preStartTime: "",
  preEndTime: "",
};

export function CreateProjectDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateProjectDialogProps) {
  const [form, setForm] = useState<CreateProjectParams>({ ...initialForm });
  const [projectTypes, setProjectTypes] = useState<ProjectTypeOption[]>([]);
  const [stageTemplates, setStageTemplates] = useState<ProjectStageTemplate[]>([]);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [step, setStep] = useState(1); // 1=基本信息, 2=合同信息, 3=成员分配
  const [contractMainFile, setContractMainFile] = useState<FilesVo | null>(null);
  const [contractAttachments, setContractAttachments] = useState<FilesVo[]>([]);
  const [uploading, setUploading] = useState(false);

  // 加载下拉数据
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

  useEffect(() => {
    if (open) {
      setForm({ ...initialForm });
      setStep(1);
      setError("");
      setContractMainFile(null);
      setContractAttachments([]);
      setStageTemplates([]);
      fetchOptions();
    }
  }, [open, fetchOptions]);

  // 项目类型变化时加载对应阶段模板
  useEffect(() => {
    if (!form.projectType) {
      setStageTemplates([]);
      return;
    }
    async function fetchTemplates() {
      try {
        const res = await getStageTemplates(form.projectType);
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setStageTemplates(res.data);
        }
      } catch {
        // 静默
      }
    }
    fetchTemplates();
  }, [form.projectType]);

  const updateField = <K extends keyof CreateProjectParams>(
    key: K,
    value: CreateProjectParams[K]
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

  const handleFileUpload = async (
    file: File,
    type: "main" | "attachment"
  ) => {
    setUploading(true);
    setError("");
    try {
      const res = await uploadDocument(file);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        if (type === "main") {
          setContractMainFile(res.data);
          updateField("contractFile", Number(res.data.fileId));
        } else {
          setContractAttachments((prev) => [...prev, res.data]);
          updateField("contractOtherFiles", [
            ...(form.contractOtherFiles ?? []),
            Number(res.data.fileId),
          ]);
        }
      } else {
        setError(res.msg || "文件上传失败");
      }
    } catch {
      setError("文件上传失败，请重试");
    } finally {
      setUploading(false);
    }
  };

  const removeAttachment = (fileId: string) => {
    setContractAttachments((prev) => prev.filter((f) => f.fileId !== fileId));
    updateField(
      "contractOtherFiles",
      (form.contractOtherFiles ?? []).filter((id) => id !== Number(fileId))
    );
  };

  const validate = (): string => {
    if (!form.projectName.trim()) return "请输入项目名称";
    if (!form.projectType) return "请选择项目类型";
    if (form.contractAmount <= 0) return "请输入合同金额";
    if (!form.contractFile) return "请上传合同主文件";
    if (form.projectCharges.length === 0) return "请至少选择一位项目经理";
    return "";
  };

  const handleSubmit = async () => {
    const msg = validate();
    if (msg) {
      toast.error(msg);
      // 自动跳转到对应步骤
      if (!form.projectName.trim() || !form.projectType) setStep(1);
      else if (form.contractAmount <= 0 || !form.contractFile) setStep(2);
      else if (form.projectCharges.length === 0) setStep(3);
      setError(msg);
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      // 转换日期格式
      const params: CreateProjectParams = {
        ...form,
        signingTime: form.signingTime ? `${form.signingTime}T00:00:00` : undefined,
        preStartTime: form.preStartTime ? `${form.preStartTime}T00:00:00` : undefined,
        preEndTime: form.preEndTime ? `${form.preEndTime}T00:00:00` : undefined,
      };
      const res = await createProject(params);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("项目创建成功");
        onOpenChange(false);
        onSuccess();
      } else {
        toast.error(res.msg || "创建失败");
        setError(res.msg || "创建失败");
      }
    } catch {
      toast.error("网络异常，请稍后重试");
      setError("网络异常，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>新建项目</DialogTitle>
          <DialogDescription>
            填写项目基本信息、合同信息和成员分配
          </DialogDescription>
        </DialogHeader>

        {/* Steps indicator */}
        <div className="flex items-center gap-2 my-4">
          {[
            { n: 1, label: "基本信息" },
            { n: 2, label: "合同信息" },
            { n: 3, label: "成员分配" },
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

        {/* Step 1: 基本信息 */}
        {step === 1 && (
          <div className="space-y-4">
            <FormField label="项目名称" required>
              <input
                type="text"
                value={form.projectName}
                onChange={(e) => updateField("projectName", e.target.value)}
                placeholder="请输入项目名称"
                className="form-input"
              />
            </FormField>

            <FormField label="项目编码">
              <input
                type="text"
                value={form.projectCode ?? ""}
                onChange={(e) => updateField("projectCode", e.target.value)}
                placeholder="留空则自动生成"
                className="form-input"
              />
            </FormField>

            <FormField label="项目类型" required>
              <select
                value={form.projectType}
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

            {/* 阶段模板预览 */}
            {stageTemplates.length > 0 && (
              <div>
                <p className="text-sm font-medium text-slate-700 mb-2">
                  项目阶段（将从模板自动创建）
                </p>
                <div className="flex flex-wrap gap-2">
                  {stageTemplates.map((t, idx) => (
                    <span
                      key={t.stageId ?? idx}
                      className="text-xs px-2 py-1 bg-slate-100 text-slate-600 rounded-md"
                    >
                      {t.stageName}
                      {t.stageOutput > 0 && ` (${t.stageOutput}%)`}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Step 2: 合同信息 */}
        {step === 2 && (
          <div className="space-y-4">
            <FormField label="合同类型" required>
              <select
                value={form.contractType}
                onChange={(e) => updateField("contractType", Number(e.target.value))}
                className="form-input"
              >
                <option value={0}>基本收费</option>
                <option value={1}>基本+效益</option>
              </select>
            </FormField>

            <FormField label="合同金额（元）" required>
              <input
                type="number"
                value={form.contractAmount || ""}
                onChange={(e) => updateField("contractAmount", Number(e.target.value))}
                placeholder="请输入合同金额"
                className="form-input"
                min={0}
              />
            </FormField>

            {form.contractType === 1 && (
              <>
                <FormField label="基础金额（元）">
                  <input
                    type="number"
                    value={form.baseAmount || ""}
                    onChange={(e) => updateField("baseAmount", Number(e.target.value))}
                    placeholder="请输入基础金额"
                    className="form-input"
                    min={0}
                  />
                </FormField>
                <FormField label="效益规则">
                  <input
                    type="text"
                    value={form.benefitRule ?? ""}
                    onChange={(e) => updateField("benefitRule", e.target.value)}
                    placeholder="请输入效益规则说明"
                    className="form-input"
                  />
                </FormField>
              </>
            )}

            <FormField label="签订日期">
              <input
                type="date"
                value={form.signingTime ?? ""}
                onChange={(e) => updateField("signingTime", e.target.value)}
                className="form-input"
              />
            </FormField>

            {/* 合同主文件 */}
            <FormField label="合同文件">
              {contractMainFile ? (
                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-xl">
                  <FileText className="w-4 h-4 text-blue-500 shrink-0" />
                  <span className="text-sm text-slate-700 truncate flex-1">
                    {contractMainFile.displayName}
                  </span>
                  <button
                    type="button"
                    onClick={() => {
                      setContractMainFile(null);
                      updateField("contractFile", undefined);
                    }}
                    className="p-1 text-slate-400 hover:text-rose-500 transition-colors"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ) : (
                <label className="flex items-center justify-center gap-2 p-4 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors">
                  {uploading ? (
                    <Loader2 className="w-4 h-4 text-blue-500 animate-spin" />
                  ) : (
                    <Upload className="w-4 h-4 text-slate-400" />
                  )}
                  <span className="text-sm text-slate-500">
                    {uploading ? "上传中..." : "点击上传合同主文件"}
                  </span>
                  <input
                    type="file"
                    className="hidden"
                    accept=".pdf,.doc,.docx,.xls,.xlsx"
                    disabled={uploading}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) handleFileUpload(file, "main");
                      e.target.value = "";
                    }}
                  />
                </label>
              )}
            </FormField>

            {/* 合同附件 */}
            <FormField label="其他附件">
              {contractAttachments.length > 0 && (
                <div className="space-y-2 mb-2">
                  {contractAttachments.map((f) => (
                    <div
                      key={f.fileId}
                      className="flex items-center gap-2 p-2 bg-slate-50 rounded-lg"
                    >
                      <FileText className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span className="text-xs text-slate-600 truncate flex-1">
                        {f.displayName}
                      </span>
                      <button
                        type="button"
                        onClick={() => removeAttachment(f.fileId)}
                        className="p-0.5 text-slate-400 hover:text-rose-500 transition-colors"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
              <label className="flex items-center justify-center gap-2 p-3 border border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors">
                {uploading ? (
                  <Loader2 className="w-4 h-4 text-blue-500 animate-spin" />
                ) : (
                  <Upload className="w-3.5 h-3.5 text-slate-400" />
                )}
                <span className="text-xs text-slate-500">
                  {uploading ? "上传中..." : "添加附件"}
                </span>
                <input
                  type="file"
                  className="hidden"
                  accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt"
                  disabled={uploading}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleFileUpload(file, "attachment");
                    e.target.value = "";
                  }}
                />
              </label>
            </FormField>
          </div>
        )}

        {/* Step 3: 成员分配 */}
        {step === 3 && (
          <div className="space-y-4">
            <FormField label="项目经理" required>
              <p className="text-xs text-slate-400 mb-2">
                已选 {form.projectCharges.length} 人
              </p>
              <UserSelector
                users={users}
                selected={form.projectCharges}
                onToggle={(id) => toggleUserInList("projectCharges", id)}
              />
            </FormField>

            <FormField label="项目成员">
              <p className="text-xs text-slate-400 mb-2">
                已选 {(form.projectMembers ?? []).length} 人（不含项目经理）
              </p>
              <UserSelector
                users={users.filter(
                  (u) => !form.projectCharges.includes(u.userId)
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
              <Button
                variant="outline"
                onClick={() => setStep(step - 1)}
              >
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
                    创建中...
                  </>
                ) : (
                  "创建项目"
                )}
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** 表单字段容器 */
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

/** 用户多选组件 */
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
