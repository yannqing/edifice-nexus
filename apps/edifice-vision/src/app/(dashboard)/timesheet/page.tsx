"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Download,
  Plus,
  Clock,
  Users,
  FileText,
  Lightbulb,
  ChevronLeft,
  ChevronRight,
  Pencil,
  Trash2,
  Loader2,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { CardPageSkeleton } from "@/components/ui/skeleton";
import {
  getMyTimesheets,
  getWeeklyStats,
  saveTimesheet,
  deleteTimesheet,
} from "@/services/timesheet";
import { getMyProjects, getProjectDetail } from "@/services/project";
import { ResponseCode } from "@/types/api";
import type { TimesheetVo, WeeklyStats, TimesheetFormParams } from "@/types/timesheet";
import { WORK_TYPE_MAP } from "@/types/timesheet";
import type { ProjectListVo, ProjectStageVo } from "@/types/project";

const workTypeStyles: Record<number, string> = {
  0: "bg-blue-100 text-blue-600",
  1: "bg-emerald-100 text-emerald-600",
  2: "bg-amber-100 text-amber-600",
};

const workTypeBarColors: Record<number, string> = {
  0: "bg-blue-500",
  1: "bg-emerald-500",
  2: "bg-amber-500",
};

/** 获取指定日期所在周的周一 */
function getMonday(d: Date): Date {
  const date = new Date(d);
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  date.setDate(diff);
  return date;
}

/** 格式化日期为 yyyy-MM-dd */
function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

const DAY_LABELS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

export default function TimesheetPage() {
  const [currentMonday, setCurrentMonday] = useState(() => getMonday(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => formatDate(new Date()));
  const [records, setRecords] = useState<TimesheetVo[]>([]);
  const [stats, setStats] = useState<WeeklyStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<TimesheetVo | null>(null);

  // 当前周的 7 天
  const weekDays = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(currentMonday);
    d.setDate(d.getDate() + i);
    return { date: formatDate(d), day: DAY_LABELS[i], dateNum: String(d.getDate()) };
  });

  const startDate = weekDays[0].date;
  const endDate = weekDays[6].date;

  // 加载数据
  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [recordsRes, statsRes] = await Promise.all([
        getMyTimesheets({ startDate, endDate }),
        getWeeklyStats(startDate, endDate),
      ]);
      if (recordsRes.code === ResponseCode.SUCCESS) {
        setRecords(recordsRes.data ?? []);
      }
      if (statsRes.code === ResponseCode.SUCCESS) {
        setStats(statsRes.data ?? null);
      }
    } catch { /* 静默 */ }
    finally { setLoading(false); }
  }, [startDate, endDate]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // 切换周时，选中日期调整到新周的周一
  const goWeek = (offset: number) => {
    const d = new Date(currentMonday);
    d.setDate(d.getDate() + offset * 7);
    setCurrentMonday(d);
    setSelectedDate(formatDate(d));
  };

  const goToday = () => {
    const today = new Date();
    setCurrentMonday(getMonday(today));
    setSelectedDate(formatDate(today));
  };

  // 当天记录
  const dailyRecords = records.filter((r) => r.workDate === selectedDate);
  const dailyTotal = dailyRecords.reduce((sum, r) => sum + r.hours, 0);

  // 按天统计工时
  const getDateHours = (date: string) =>
    records.filter((r) => r.workDate === date).reduce((sum, r) => sum + r.hours, 0);

  // 周汇总表：按项目分组
  const projectMap = new Map<string, { name: string; days: Record<string, number>; total: number }>();
  for (const r of records) {
    const key = r.projectId;
    if (!projectMap.has(key)) {
      projectMap.set(key, { name: r.projectName || r.projectCode, days: {}, total: 0 });
    }
    const entry = projectMap.get(key)!;
    entry.days[r.workDate] = (entry.days[r.workDate] || 0) + r.hours;
    entry.total += r.hours;
  }

  // 删除
  const handleDelete = async (id: string) => {
    try {
      const res = await deleteTimesheet(id);
      if (res.code === ResponseCode.SUCCESS) {
        toast.success("删除成功");
        fetchData();
      }
      // 业务错误由 request.ts 统一提示
    } catch {
      /* 网络错误由 request.ts 提示 */
    }
  };

  // 周标题
  const weekTitle = (() => {
    const d = new Date(currentMonday);
    const year = d.getFullYear();
    const month = d.getMonth() + 1;
    const weekNum = Math.ceil(d.getDate() / 7);
    return `${year}年${month}月 第${weekNum}周`;
  })();

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">工时填报</h1>
          <p className="text-slate-500 text-sm mt-1">记录您在各项目上的工作时间，用于成本核算与绩效统计。</p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="w-4 h-4" /> 导出记录
          </Button>
          <Button onClick={() => { setEditingRecord(null); setFormOpen(true); }}
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2">
            <Plus className="w-4 h-4" /> 填报工时
          </Button>
        </div>
      </div>

      {loading && <CardPageSkeleton cards={4} />}

      {!loading && (
        <>
          {/* Stats */}
          <div className="grid grid-cols-4 gap-4">
            <StatCard icon={<Clock className="w-5 h-5" />} label="本周总工时" value={`${stats?.totalHours ?? 0}h`} color="slate" />
            <StatCard icon={<Users className="w-5 h-5" />} label="管理工作" value={`${stats?.managementHours ?? 0}h`} color="blue" />
            <StatCard icon={<FileText className="w-5 h-5" />} label="基础工作" value={`${stats?.basicHours ?? 0}h`} color="emerald" />
            <StatCard icon={<Lightbulb className="w-5 h-5" />} label="智励工作" value={`${stats?.intellectualHours ?? 0}h`} color="amber" />
          </div>

          {/* Week View */}
          <div className="glass-card rounded-2xl p-6 shadow-sm">
            <div className="flex justify-between items-center mb-6">
              <div className="flex items-center gap-4">
                <button onClick={() => goWeek(-1)} className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
                  <ChevronLeft className="w-5 h-5" />
                </button>
                <h3 className="text-lg font-bold text-slate-800">{weekTitle}</h3>
                <button onClick={() => goWeek(1)} className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
                  <ChevronRight className="w-5 h-5" />
                </button>
              </div>
              <button onClick={goToday} className="px-3 py-1.5 text-sm text-blue-600 font-medium bg-blue-50 rounded-lg">本周</button>
            </div>

            {/* Week Days */}
            <div className="grid grid-cols-7 gap-2 mb-6">
              {weekDays.map((day, idx) => {
                const hours = getDateHours(day.date);
                const isSelected = selectedDate === day.date;
                const isWeekend = idx >= 5;
                return (
                  <button key={day.date} onClick={() => setSelectedDate(day.date)}
                    className={cn("p-4 rounded-xl text-center transition-all",
                      isSelected ? "bg-blue-600 text-white shadow-lg shadow-blue-200"
                        : isWeekend ? "bg-slate-50 text-slate-400 hover:bg-slate-100"
                        : "bg-slate-50 text-slate-600 hover:bg-slate-100")}>
                    <p className={cn("text-xs font-medium", isSelected ? "text-blue-100" : "")}>{day.day}</p>
                    <p className={cn("text-2xl font-bold my-1", isSelected ? "text-white" : "")}>{day.dateNum}</p>
                    <p className={cn("text-xs font-medium",
                      isSelected ? "text-blue-100" : hours > 0 ? "text-emerald-600" : "text-slate-400")}>
                      {hours > 0 ? `${hours}h` : "-"}
                    </p>
                  </button>
                );
              })}
            </div>

            {/* Daily Records */}
            <div>
              <div className="flex justify-between items-center mb-4">
                <h4 className="text-sm font-semibold text-slate-800">
                  {selectedDate} 工时记录
                  <span className="ml-2 text-slate-400 font-normal">共 {dailyTotal} 小时</span>
                </h4>
                <button onClick={() => { setEditingRecord(null); setFormOpen(true); }}
                  className="text-sm text-blue-600 font-medium hover:underline flex items-center gap-1">
                  <Plus className="w-4 h-4" /> 添加记录
                </button>
              </div>

              {dailyRecords.length > 0 ? (
                <div className="space-y-3">
                  {dailyRecords.map((record) => (
                    <div key={record.timesheetId}
                      className="flex items-center gap-4 p-4 bg-slate-50/50 rounded-xl hover:bg-slate-100/50 transition-colors group">
                      <div className={cn("w-1 h-12 rounded-full", workTypeBarColors[record.workType])} />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm font-semibold text-slate-800">{record.projectName}</span>
                          {record.stageName && (
                            <>
                              <span className="text-xs text-slate-400">·</span>
                              <span className="text-xs text-slate-500">{record.stageName}</span>
                            </>
                          )}
                        </div>
                        {record.description && (
                          <p className="text-sm text-slate-500 truncate">{record.description}</p>
                        )}
                      </div>
                      <div className="flex items-center gap-4">
                        <span className={cn("text-xs px-2 py-1 rounded-full font-medium", workTypeStyles[record.workType])}>
                          {WORK_TYPE_MAP[record.workType]}
                        </span>
                        <p className="text-lg font-bold text-slate-800">{record.hours}h</p>
                        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button onClick={() => { setEditingRecord(record); setFormOpen(true); }}
                            className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button onClick={() => handleDelete(record.timesheetId)}
                            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="py-12 text-center">
                  <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-3">
                    <Clock className="w-6 h-6 text-slate-400" />
                  </div>
                  <p className="text-sm text-slate-500 mb-3">当日暂无工时记录</p>
                  <button onClick={() => { setEditingRecord(null); setFormOpen(true); }}
                    className="text-sm text-blue-600 font-medium hover:underline">立即填报</button>
                </div>
              )}
            </div>
          </div>

          {/* Weekly Summary Table */}
          {projectMap.size > 0 && (
            <div className="glass-card rounded-2xl p-6 shadow-sm">
              <h3 className="text-lg font-bold text-slate-800 mb-6">本周工时汇总</h3>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="text-slate-400 text-xs uppercase tracking-wider border-b border-slate-100">
                      <th className="text-left py-3 font-semibold">项目名称</th>
                      {weekDays.map((d) => (
                        <th key={d.date} className="text-center py-3 font-semibold">{d.day}</th>
                      ))}
                      <th className="text-right py-3 font-semibold">合计</th>
                    </tr>
                  </thead>
                  <tbody className="text-sm">
                    {Array.from(projectMap.entries()).map(([key, entry]) => (
                      <tr key={key} className="border-b border-slate-50 hover:bg-slate-50/50">
                        <td className="py-3 text-slate-800 font-medium">{entry.name}</td>
                        {weekDays.map((d) => (
                          <td key={d.date} className={cn("py-3 text-center", entry.days[d.date] ? "text-slate-600" : "text-slate-400")}>
                            {entry.days[d.date] ? `${entry.days[d.date]}h` : "-"}
                          </td>
                        ))}
                        <td className="py-3 text-right text-slate-800 font-semibold">{entry.total}h</td>
                      </tr>
                    ))}
                    {/* 每日合计 */}
                    <tr className="bg-slate-50/50 font-semibold">
                      <td className="py-3 text-slate-800">每日合计</td>
                      {weekDays.map((d) => {
                        const h = getDateHours(d.date);
                        return (
                          <td key={d.date} className={cn("py-3 text-center", h > 0 ? "text-blue-600" : "text-slate-400")}>
                            {h > 0 ? `${h}h` : "-"}
                          </td>
                        );
                      })}
                      <td className="py-3 text-right text-blue-600">{stats?.totalHours ?? 0}h</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {/* Form Dialog */}
      <TimesheetFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        editing={editingRecord}
        defaultDate={selectedDate}
        onSuccess={fetchData}
      />
    </div>
  );
}

// ==================== 统计卡片 ====================

function StatCard({ icon, label, value, color }: {
  icon: React.ReactNode; label: string; value: string; color: string;
}) {
  const colorMap: Record<string, string> = {
    slate: "bg-slate-100 text-slate-600", blue: "bg-blue-100 text-blue-600",
    emerald: "bg-emerald-100 text-emerald-600", amber: "bg-amber-100 text-amber-600",
  };
  return (
    <div className="glass-card p-4 rounded-xl">
      <div className="flex items-center gap-3">
        <div className={cn("p-2 rounded-lg", colorMap[color])}>{icon}</div>
        <div>
          <p className="text-xs text-slate-500">{label}</p>
          <p className="text-xl font-bold text-slate-800">{value}</p>
        </div>
      </div>
    </div>
  );
}

// ==================== 填报弹窗 ====================

function TimesheetFormDialog({ open, onOpenChange, editing, defaultDate, onSuccess }: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  editing: TimesheetVo | null;
  defaultDate: string;
  onSuccess: () => void;
}) {
  const [projects, setProjects] = useState<ProjectListVo[]>([]);
  const [stages, setStages] = useState<ProjectStageVo[]>([]);
  const [form, setForm] = useState<TimesheetFormParams>({
    projectId: "", workType: 1, workDate: defaultDate, hours: 0,
  });
  const [submitting, setSubmitting] = useState(false);

  // 加载项目列表
  useEffect(() => {
    if (!open) return;
    async function load() {
      try {
        const res = await getMyProjects({ pageSize: 100 });
        if (res.code === ResponseCode.SUCCESS && res.data) {
          setProjects(res.data.records ?? []);
        }
      } catch { /* 静默 */ }
    }
    load();
  }, [open]);

  // 初始化表单
  useEffect(() => {
    if (!open) return;
    if (editing) {
      setForm({
        timesheetId: editing.timesheetId,
        projectId: editing.projectId,
        projectStageId: editing.projectStageId ?? undefined,
        workType: editing.workType,
        workDate: editing.workDate,
        hours: editing.hours,
        description: editing.description,
        status: editing.status,
      });
    } else {
      setForm({ projectId: "", workType: 1, workDate: defaultDate, hours: 0 });
    }
  }, [open, editing, defaultDate]);

  // 选择项目后加载阶段
  useEffect(() => {
    if (!form.projectId) { setStages([]); return; }
    async function load() {
      try {
        const res = await getProjectDetail(form.projectId);
        if (res.code === ResponseCode.SUCCESS && res.data?.projectStages) {
          setStages(res.data.projectStages);
        }
      } catch { /* 静默 */ }
    }
    load();
  }, [form.projectId]);

  const handleSubmit = async (status: number) => {
    if (!form.projectId) { toast.error("请选择项目"); return; }
    if (!form.workDate) { toast.error("请选择日期"); return; }
    if (!form.hours || form.hours <= 0) { toast.error("请输入工作时长"); return; }

    setSubmitting(true);
    try {
      const res = await saveTimesheet({ ...form, status });
      if (res.code === ResponseCode.SUCCESS) {
        toast.success(status === 1 ? "提交成功" : "草稿已保存");
        onOpenChange(false);
        onSuccess();
      }
    } catch {
      /* 网络错误由 request.ts 提示 */
    }
    finally { setSubmitting(false); }
  };

  const workTypes = [
    { value: 0, label: "管理工作", desc: "项目管理、协调会议等", color: "bg-blue-500" },
    { value: 1, label: "基础工作", desc: "计算、编制、复核等", color: "bg-emerald-500" },
    { value: 2, label: "智励工作", desc: "技术指导、方案审核等", color: "bg-amber-500" },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? "编辑工时" : "填报工时"}</DialogTitle>
          <DialogDescription>记录项目工作时间</DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-5">
          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              填报日期 <span className="text-rose-500">*</span>
            </label>
            <input type="date" value={form.workDate} onChange={(e) => setForm({ ...form, workDate: e.target.value })}
              className="form-input" />
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              项目名称 <span className="text-rose-500">*</span>
            </label>
            <select value={form.projectId} onChange={(e) => setForm({ ...form, projectId: e.target.value, projectStageId: undefined })}
              className="form-input">
              <option value="">请选择项目</option>
              {projects.map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName} ({p.projectCode})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">项目阶段</label>
            <select value={form.projectStageId ?? ""} onChange={(e) => setForm({ ...form, projectStageId: e.target.value || undefined })}
              className="form-input" disabled={!form.projectId}>
              <option value="">{form.projectId ? "请选择阶段（可选）" : "请先选择项目"}</option>
              {stages.map((s) => (
                <option key={s.projectStageId} value={s.projectStageId}>{s.stageName}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              工作类型 <span className="text-rose-500">*</span>
            </label>
            <div className="grid grid-cols-3 gap-3">
              {workTypes.map((type) => (
                <label key={type.value} className="relative cursor-pointer">
                  <input type="radio" name="workType" value={type.value} checked={form.workType === type.value}
                    onChange={() => setForm({ ...form, workType: type.value })} className="peer sr-only" />
                  <div className="p-3 border border-slate-200 rounded-xl text-center peer-checked:border-blue-500 peer-checked:bg-blue-50 transition-all">
                    <div className={cn("w-3 h-3 rounded-full mx-auto mb-2", type.color)} />
                    <p className="text-sm font-medium text-slate-700">{type.label}</p>
                    <p className="text-xs text-slate-400 mt-1">{type.desc}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">
              工作时长（小时）<span className="text-rose-500">*</span>
            </label>
            <input type="number" min="0.5" max="24" step="0.5" value={form.hours || ""}
              onChange={(e) => setForm({ ...form, hours: Number(e.target.value) })}
              placeholder="请输入工作时长" className="form-input" />
          </div>

          <div>
            <label className="text-sm font-medium text-slate-700 mb-1.5 block">工作内容描述</label>
            <textarea rows={3} value={form.description ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="请简要描述您完成的工作内容..." className="form-input resize-none" />
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-slate-100 mt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant="outline" disabled={submitting} onClick={() => handleSubmit(0)}>保存草稿</Button>
          <Button className="bg-blue-600 hover:bg-blue-700 text-white" disabled={submitting} onClick={() => handleSubmit(1)}>
            {submitting ? <><Loader2 className="w-4 h-4 animate-spin mr-1" />提交中...</> : "提交"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
