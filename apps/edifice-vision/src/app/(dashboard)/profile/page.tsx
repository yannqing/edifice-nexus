"use client";

import { useCallback, useEffect, useState } from "react";
import {
  User,
  Mail,
  Phone,
  IdCard,
  MapPin,
  GraduationCap,
  Briefcase,
  CalendarDays,
  Award,
  Pencil,
  Loader2,
  Save,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { CardPageSkeleton } from "@/components/ui/skeleton";
import { ResponseCode } from "@/types/api";
import {
  EMPLOYMENT_STATUS_MAP,
  GENDER_MAP,
  type SysUser,
} from "@/types/auth";
import { getProfile, updateProfile, type UpdateProfileParams } from "@/services/user";
import { useAuth } from "@/store/auth-context";

type FormState = UpdateProfileParams;

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.slice(0, 10);
}

const INITIAL_FORM: FormState = {};

export default function ProfilePage() {
  const { updateUser } = useAuth();
  const [profile, setProfile] = useState<SysUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);

  const fetchProfile = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getProfile();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setProfile(res.data);
      }
      setLoading(false);
    } catch {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchProfile(); }, [fetchProfile]);

  const startEdit = () => {
    if (!profile) return;
    setForm({
      realName: profile.realName ?? "",
      gender: profile.gender ?? undefined,
      ethnicity: profile.ethnicity ?? "",
      birthDate: profile.birthDate ?? "",
      email: profile.email ?? "",
      phone: profile.phone ?? "",
      avatar: profile.avatar ?? "",
      education: profile.education ?? "",
      school: profile.school ?? "",
      major: profile.major ?? "",
      certificates: profile.certificates ?? "",
      domicile: profile.domicile ?? "",
      address: profile.address ?? "",
      remark: profile.remark ?? "",
    });
    setEditing(true);
  };

  const cancelEdit = () => {
    setEditing(false);
    setForm(INITIAL_FORM);
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      // 把空字符串转成 undefined，避免误清空后端已有值
      const payload: UpdateProfileParams = {};
      (Object.keys(form) as (keyof UpdateProfileParams)[]).forEach((k) => {
        const v = form[k];
        if (v !== undefined && v !== "") {
          (payload as Record<string, unknown>)[k] = v;
        }
      });

      const res = await updateProfile(payload);
      if (res.code === ResponseCode.SUCCESS && res.data) {
        toast.success("资料已更新");
        setProfile(res.data);
        updateUser(res.data);
        setEditing(false);
      }
    } catch {
      /* 由 request.ts 统一提示 */
    } finally {
      setSubmitting(false);
    }
  };

  const displayName = profile?.realName || profile?.username || "用户";

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">个人中心</h1>
          <p className="text-slate-500 text-sm mt-1">查看并维护您的个人资料</p>
        </div>
        {!editing && !loading && profile && (
          <Button
            onClick={startEdit}
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
          >
            <Pencil className="w-4 h-4" /> 编辑资料
          </Button>
        )}
        {editing && (
          <div className="flex gap-2">
            <Button variant="outline" onClick={cancelEdit} disabled={submitting}>
              <X className="w-4 h-4 mr-1" /> 取消
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={submitting}
              className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
            >
              {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              保存
            </Button>
          </div>
        )}
      </div>

      {loading && <CardPageSkeleton cards={3} />}

      {!loading && profile && (
        <>
          {/* Summary */}
          <div className="glass-card rounded-2xl p-6 shadow-sm">
            <div className="flex items-center gap-5">
              <div className="w-20 h-20 rounded-full bg-blue-100 flex items-center justify-center text-3xl font-bold text-blue-600 shrink-0 overflow-hidden">
                {profile.avatar ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={profile.avatar} alt={displayName} className="w-full h-full object-cover" />
                ) : (
                  displayName.charAt(0)
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <h2 className="text-xl font-bold text-slate-900">{displayName}</h2>
                  {profile.employmentStatus !== undefined && profile.employmentStatus !== null && (
                    <Badge
                      variant="secondary"
                      className={cn(
                        "text-xs",
                        profile.employmentStatus === 1
                          ? "bg-emerald-100 text-emerald-700"
                          : "bg-slate-200 text-slate-600"
                      )}
                    >
                      {EMPLOYMENT_STATUS_MAP[profile.employmentStatus] ?? "-"}
                    </Badge>
                  )}
                </div>
                <div className="flex items-center gap-4 mt-1 text-sm text-slate-500">
                  {profile.employeeNo && <span>工号 · {profile.employeeNo}</span>}
                  {profile.position && <span>{profile.position}</span>}
                  {profile.professionalTitle && <span>{profile.professionalTitle}</span>}
                </div>
              </div>
            </div>
          </div>

          {editing ? <EditForm form={form} setForm={setForm} /> : <ReadOnlyView profile={profile} />}
        </>
      )}
    </div>
  );
}

// ==================== 只读展示 ====================

function ReadOnlyView({ profile }: { profile: SysUser }) {
  const basic = [
    { icon: User, label: "真实姓名", value: profile.realName },
    { icon: User, label: "性别", value: profile.gender !== null && profile.gender !== undefined ? GENDER_MAP[profile.gender] : "-" },
    { icon: User, label: "民族", value: profile.ethnicity },
    { icon: CalendarDays, label: "出生日期", value: formatDate(profile.birthDate) },
    { icon: IdCard, label: "身份证号", value: profile.idCard },
    { icon: Mail, label: "邮箱", value: profile.email },
    { icon: Phone, label: "手机号", value: profile.phone },
  ];

  const work = [
    { icon: Briefcase, label: "职务", value: profile.position },
    { icon: Award, label: "职称", value: profile.professionalTitle },
    { icon: Award, label: "证书", value: profile.certificates },
    { icon: CalendarDays, label: "入职时间", value: formatDate(profile.entryDate) },
    { icon: CalendarDays, label: "合同期限", value: formatDate(profile.contractEndDate) },
    { icon: CalendarDays, label: "入社保时间", value: formatDate(profile.socialInsuranceDate) },
  ];

  const edu = [
    { icon: GraduationCap, label: "学历", value: profile.education },
    { icon: GraduationCap, label: "毕业院校", value: profile.school },
    { icon: GraduationCap, label: "专业", value: profile.major },
  ];

  const location = [
    { icon: MapPin, label: "户籍所在地", value: profile.domicile },
    { icon: MapPin, label: "居住地", value: profile.address },
  ];

  return (
    <>
      <Section title="基本信息" items={basic} />
      <Section title="工作信息" items={work} />
      <Section title="教育背景" items={edu} />
      <Section title="地址" items={location} />
      {profile.remark && (
        <div className="glass-card rounded-2xl p-6 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-700 mb-2">备注</h3>
          <p className="text-sm text-slate-600 whitespace-pre-wrap">{profile.remark}</p>
        </div>
      )}
    </>
  );
}

function Section({
  title,
  items,
}: {
  title: string;
  items: { icon: React.ComponentType<{ className?: string }>; label: string; value: string | null | undefined }[];
}) {
  return (
    <div className="glass-card rounded-2xl p-6 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-700 mb-4">{title}</h3>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {items.map((item) => (
          <div key={item.label} className="flex items-start gap-3">
            <div className="p-2 bg-slate-100 text-slate-500 rounded-lg shrink-0">
              <item.icon className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <p className="text-xs text-slate-400">{item.label}</p>
              <p className="text-sm font-medium text-slate-800 break-all">
                {item.value || "-"}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ==================== 编辑表单 ====================

function EditForm({
  form,
  setForm,
}: {
  form: FormState;
  setForm: (v: FormState) => void;
}) {
  const patch = (p: Partial<FormState>) => setForm({ ...form, ...p });

  return (
    <>
      <div className="glass-card rounded-2xl p-6 shadow-sm space-y-5">
        <h3 className="text-sm font-semibold text-slate-700">基本信息</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
          <Field label="民族">
            <input
              className="form-input"
              value={form.ethnicity ?? ""}
              onChange={(e) => patch({ ethnicity: e.target.value })}
            />
          </Field>
          <Field label="出生日期">
            <input
              type="date"
              className="form-input"
              value={form.birthDate ?? ""}
              onChange={(e) => patch({ birthDate: e.target.value })}
            />
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
          <Field label="头像 URL">
            <input
              className="form-input"
              value={form.avatar ?? ""}
              onChange={(e) => patch({ avatar: e.target.value })}
              placeholder="https://..."
            />
          </Field>
        </div>
      </div>

      <div className="glass-card rounded-2xl p-6 shadow-sm space-y-5">
        <h3 className="text-sm font-semibold text-slate-700">教育背景</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field label="学历">
            <input
              className="form-input"
              value={form.education ?? ""}
              onChange={(e) => patch({ education: e.target.value })}
            />
          </Field>
          <Field label="毕业院校">
            <input
              className="form-input"
              value={form.school ?? ""}
              onChange={(e) => patch({ school: e.target.value })}
            />
          </Field>
          <Field label="专业">
            <input
              className="form-input"
              value={form.major ?? ""}
              onChange={(e) => patch({ major: e.target.value })}
            />
          </Field>
          <Field label="证书">
            <input
              className="form-input"
              value={form.certificates ?? ""}
              onChange={(e) => patch({ certificates: e.target.value })}
            />
          </Field>
        </div>
      </div>

      <div className="glass-card rounded-2xl p-6 shadow-sm space-y-5">
        <h3 className="text-sm font-semibold text-slate-700">地址</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field label="户籍所在地">
            <input
              className="form-input"
              value={form.domicile ?? ""}
              onChange={(e) => patch({ domicile: e.target.value })}
            />
          </Field>
          <Field label="居住地">
            <input
              className="form-input"
              value={form.address ?? ""}
              onChange={(e) => patch({ address: e.target.value })}
            />
          </Field>
        </div>
      </div>

      <div className="glass-card rounded-2xl p-6 shadow-sm space-y-3">
        <h3 className="text-sm font-semibold text-slate-700">备注</h3>
        <textarea
          rows={3}
          className="form-input resize-none"
          value={form.remark ?? ""}
          onChange={(e) => patch({ remark: e.target.value })}
          placeholder="选填"
        />
      </div>

      <p className="text-xs text-slate-400">
        员工编号、身份证号、入职 / 离职、合同期限、入社保时间、账号状态等信息由管理员维护，
        如需修改请联系管理员。
      </p>
    </>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-1.5">{label}</label>
      {children}
    </div>
  );
}
