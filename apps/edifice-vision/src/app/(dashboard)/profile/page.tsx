"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
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
  Info,
  ArrowRight,
} from "lucide-react";
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
import { getProfile } from "@/services/user";

function formatDate(d: string | null | undefined): string {
  if (!d) return "-";
  return d.slice(0, 10);
}

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<SysUser | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchProfile = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getProfile();
      if (res.code === ResponseCode.SUCCESS && res.data) {
        setProfile(res.data);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchProfile(); }, [fetchProfile]);

  const displayName = profile?.realName || profile?.username || "用户";

  return (
    <div className="p-4 md:p-8 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">个人中心</h1>
          <p className="text-slate-500 text-sm mt-1">查看个人资料</p>
        </div>
        <Button
          onClick={() => router.push("/")}
          className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
        >
          工作台
          <ArrowRight className="w-4 h-4" />
        </Button>
      </div>

      <div className="rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 flex items-start gap-3">
        <Info className="w-5 h-5 text-blue-600 mt-0.5 shrink-0" />
        <div>
          <p className="text-sm font-medium text-blue-800">这里仅展示个人信息</p>
          <p className="text-sm text-blue-700 mt-0.5">
            如需修改姓名、手机号、邮箱、部门、岗位等员工资料，请到工作台中的 OA 办公系统维护；edifice 会按同步任务更新展示。
          </p>
        </div>
      </div>

      {loading && <CardPageSkeleton cards={3} />}

      {!loading && profile && (
        <>
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
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1 text-sm text-slate-500">
                  {profile.employeeNo && <span>工号 · {profile.employeeNo}</span>}
                  {profile.position && <span>{profile.position}</span>}
                  {profile.professionalTitle && <span>{profile.professionalTitle}</span>}
                </div>
              </div>
            </div>
          </div>

          <ReadOnlyView profile={profile} />
        </>
      )}
    </div>
  );
}

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
