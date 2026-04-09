"use client";

import { useState } from "react";
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
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { timesheetRecordsData, weekDaysData } from "@/data/mock-data";
import type { WorkType } from "@/types";

const workTypeStyles: Record<WorkType, string> = {
  管理工作: "bg-blue-100 text-blue-600",
  基础工作: "bg-emerald-100 text-emerald-600",
  智励工作: "bg-amber-100 text-amber-600",
};

const workTypeBarColors: Record<WorkType, string> = {
  管理工作: "bg-blue-500",
  基础工作: "bg-emerald-500",
  智励工作: "bg-amber-500",
};

export default function TimesheetPage() {
  const [showForm, setShowForm] = useState(false);
  const [selectedDate, setSelectedDate] = useState("2024-01-15");

  const getDateHours = (date: string) => {
    return timesheetRecordsData
      .filter((r) => r.date === date)
      .reduce((sum, r) => sum + r.hours, 0);
  };

  const weeklyStats = {
    totalHours: timesheetRecordsData.reduce((sum, r) => sum + r.hours, 0),
    management: timesheetRecordsData
      .filter((r) => r.workType === "管理工作")
      .reduce((sum, r) => sum + r.hours, 0),
    basic: timesheetRecordsData
      .filter((r) => r.workType === "基础工作")
      .reduce((sum, r) => sum + r.hours, 0),
    intellectual: timesheetRecordsData
      .filter((r) => r.workType === "智励工作")
      .reduce((sum, r) => sum + r.hours, 0),
  };

  const filteredRecords = timesheetRecordsData.filter(
    (r) => r.date === selectedDate
  );

  const workTypes = [
    {
      value: "管理工作",
      label: "管理工作",
      description: "项目管理、协调会议等",
      color: "bg-blue-500",
    },
    {
      value: "基础工作",
      label: "基础工作",
      description: "计算、编制、复核等",
      color: "bg-emerald-500",
    },
    {
      value: "智励工作",
      label: "智励工作",
      description: "技术指导、方案审核等",
      color: "bg-amber-500",
    },
  ];

  return (
    <div className="p-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            工时填报
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            记录您在各项目上的工作时间，用于成本核算与绩效统计。
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="flex items-center gap-2">
            <Download className="w-4 h-4" /> 导出记录
          </Button>
          <Button
            onClick={() => setShowForm(true)}
            className="bg-blue-600 hover:bg-blue-700 flex items-center gap-2"
          >
            <Plus className="w-4 h-4" /> 填报工时
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-slate-100 text-slate-600 rounded-lg">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">本周总工时</p>
              <p className="text-xl font-bold text-slate-800">
                {weeklyStats.totalHours}h
              </p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">管理工作</p>
              <p className="text-xl font-bold text-slate-800">
                {weeklyStats.management}h
              </p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-emerald-100 text-emerald-600 rounded-lg">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">基础工作</p>
              <p className="text-xl font-bold text-slate-800">
                {weeklyStats.basic}h
              </p>
            </div>
          </div>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-amber-100 text-amber-600 rounded-lg">
              <Lightbulb className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs text-slate-500">智励工作</p>
              <p className="text-xl font-bold text-slate-800">
                {weeklyStats.intellectual}h
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Week View */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex justify-between items-center mb-6">
          <div className="flex items-center gap-4">
            <button className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              <ChevronLeft className="w-5 h-5" />
            </button>
            <h3 className="text-lg font-bold text-slate-800">
              2024年1月 第3周
            </h3>
            <button className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>
          <button className="px-3 py-1.5 text-sm text-blue-600 font-medium bg-blue-50 rounded-lg">
            本周
          </button>
        </div>

        {/* Week Days */}
        <div className="grid grid-cols-7 gap-2 mb-6">
          {weekDaysData.map((day, idx) => {
            const hours = getDateHours(day.date);
            const isSelected = selectedDate === day.date;
            const isWeekend = idx >= 5;
            return (
              <button
                key={day.date}
                onClick={() => setSelectedDate(day.date)}
                className={cn(
                  "p-4 rounded-xl text-center transition-all",
                  isSelected
                    ? "bg-blue-600 text-white shadow-lg shadow-blue-200"
                    : isWeekend
                    ? "bg-slate-50 text-slate-400 hover:bg-slate-100"
                    : "bg-slate-50 text-slate-600 hover:bg-slate-100"
                )}
              >
                <p
                  className={cn(
                    "text-xs font-medium",
                    isSelected ? "text-blue-100" : ""
                  )}
                >
                  {day.day}
                </p>
                <p
                  className={cn(
                    "text-2xl font-bold my-1",
                    isSelected ? "text-white" : ""
                  )}
                >
                  {day.dateNum}
                </p>
                <p
                  className={cn(
                    "text-xs font-medium",
                    isSelected
                      ? "text-blue-100"
                      : hours > 0
                      ? "text-emerald-600"
                      : "text-slate-400"
                  )}
                >
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
              <span className="ml-2 text-slate-400 font-normal">
                共 {filteredRecords.reduce((sum, r) => sum + r.hours, 0)} 小时
              </span>
            </h4>
            <button
              onClick={() => setShowForm(true)}
              className="text-sm text-blue-600 font-medium hover:underline flex items-center gap-1"
            >
              <Plus className="w-4 h-4" /> 添加记录
            </button>
          </div>

          {filteredRecords.length > 0 ? (
            <div className="space-y-3">
              {filteredRecords.map((record) => (
                <div
                  key={record.id}
                  className="flex items-center gap-4 p-4 bg-slate-50/50 rounded-xl hover:bg-slate-100/50 transition-colors group"
                >
                  <div
                    className={cn(
                      "w-1 h-12 rounded-full",
                      workTypeBarColors[record.workType]
                    )}
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-semibold text-slate-800">
                        {record.projectName}
                      </span>
                      <span className="text-xs text-slate-400">·</span>
                      <span className="text-xs text-slate-500">
                        {record.phase} {record.phaseName}
                      </span>
                    </div>
                    <p className="text-sm text-slate-500 truncate">
                      {record.description}
                    </p>
                  </div>
                  <div className="flex items-center gap-4">
                    <span
                      className={cn(
                        "text-xs px-2 py-1 rounded-full font-medium",
                        workTypeStyles[record.workType]
                      )}
                    >
                      {record.workType}
                    </span>
                    <div className="text-right">
                      <p className="text-lg font-bold text-slate-800">
                        {record.hours}h
                      </p>
                    </div>
                    <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors">
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
              <button
                onClick={() => setShowForm(true)}
                className="text-sm text-blue-600 font-medium hover:underline"
              >
                立即填报
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Weekly Summary */}
      <div className="glass-card rounded-2xl p-6 shadow-sm">
        <div className="flex justify-between items-center mb-6">
          <h3 className="text-lg font-bold text-slate-800">本周工时汇总</h3>
          <button className="text-sm text-blue-600 font-medium hover:underline">
            查看月度报表
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="text-slate-400 text-xs uppercase tracking-wider border-b border-slate-100">
                <th className="text-left py-3 font-semibold">项目名称</th>
                <th className="text-center py-3 font-semibold">周一</th>
                <th className="text-center py-3 font-semibold">周二</th>
                <th className="text-center py-3 font-semibold">周三</th>
                <th className="text-center py-3 font-semibold">周四</th>
                <th className="text-center py-3 font-semibold">周五</th>
                <th className="text-center py-3 font-semibold">周六</th>
                <th className="text-center py-3 font-semibold">周日</th>
                <th className="text-right py-3 font-semibold">合计</th>
              </tr>
            </thead>
            <tbody className="text-sm">
              <tr className="border-b border-slate-50 hover:bg-slate-50/50">
                <td className="py-3 text-slate-800 font-medium">
                  国家电投通辽智慧热电项目
                </td>
                <td className="py-3 text-center text-slate-600">4h</td>
                <td className="py-3 text-center text-slate-600">6h</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-right text-slate-800 font-semibold">
                  10h
                </td>
              </tr>
              <tr className="border-b border-slate-50 hover:bg-slate-50/50">
                <td className="py-3 text-slate-800 font-medium">
                  三峡能源哈密光热项目
                </td>
                <td className="py-3 text-center text-slate-600">2h</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-600">8h</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-right text-slate-800 font-semibold">
                  10h
                </td>
              </tr>
              <tr className="border-b border-slate-50 hover:bg-slate-50/50">
                <td className="py-3 text-slate-800 font-medium">大坝电厂项目</td>
                <td className="py-3 text-center text-slate-600">2h</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-right text-slate-800 font-semibold">
                  2h
                </td>
              </tr>
              <tr className="border-b border-slate-50 hover:bg-slate-50/50">
                <td className="py-3 text-slate-800 font-medium">
                  内蒙杭锦旗供热管网项目
                </td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-600">2h</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-right text-slate-800 font-semibold">
                  2h
                </td>
              </tr>
              <tr className="bg-slate-50/50 font-semibold">
                <td className="py-3 text-slate-800">每日合计</td>
                <td className="py-3 text-center text-blue-600">8h</td>
                <td className="py-3 text-center text-blue-600">8h</td>
                <td className="py-3 text-center text-blue-600">8h</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-center text-slate-400">-</td>
                <td className="py-3 text-right text-blue-600">24h</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-8">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl overflow-hidden">
            <div className="p-6 border-b border-slate-100 flex justify-between items-center">
              <h2 className="text-xl font-bold text-slate-900">填报工时</h2>
              <button
                onClick={() => setShowForm(false)}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-5">
              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  填报日期 <span className="text-rose-500">*</span>
                </label>
                <input
                  type="date"
                  defaultValue={selectedDate}
                  className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  项目名称 <span className="text-rose-500">*</span>
                </label>
                <select className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white">
                  <option value="">请选择项目</option>
                  <option value="1">
                    国家电投通辽智慧热电项目 (PJ2024-004)
                  </option>
                  <option value="2">三峡能源哈密光热项目 (PJ2024-007)</option>
                  <option value="3">大坝电厂项目 (PJ2024-013)</option>
                  <option value="4">
                    内蒙杭锦旗供热管网项目 (PJ2024-003)
                  </option>
                </select>
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  项目阶段 <span className="text-rose-500">*</span>
                </label>
                <select className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white">
                  <option value="">请先选择项目</option>
                </select>
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  工作类型 <span className="text-rose-500">*</span>
                </label>
                <div className="grid grid-cols-3 gap-3">
                  {workTypes.map((type) => (
                    <label key={type.value} className="relative cursor-pointer">
                      <input
                        type="radio"
                        name="workType"
                        value={type.value}
                        className="peer sr-only"
                      />
                      <div className="p-3 border border-slate-200 rounded-xl text-center peer-checked:border-blue-500 peer-checked:bg-blue-50 transition-all">
                        <div
                          className={cn(
                            "w-3 h-3 rounded-full mx-auto mb-2",
                            type.color
                          )}
                        />
                        <p className="text-sm font-medium text-slate-700">
                          {type.label}
                        </p>
                        <p className="text-xs text-slate-400 mt-1">
                          {type.description}
                        </p>
                      </div>
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  工作时长（小时） <span className="text-rose-500">*</span>
                </label>
                <input
                  type="number"
                  min="0.5"
                  max="24"
                  step="0.5"
                  placeholder="请输入工作时长"
                  className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>

              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">
                  工作内容描述
                </label>
                <textarea
                  rows={3}
                  placeholder="请简要描述您完成的工作内容..."
                  className="w-full p-3 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                />
              </div>
            </div>

            <div className="p-6 border-t border-slate-100 flex justify-end gap-3">
              <Button variant="outline" onClick={() => setShowForm(false)}>
                取消
              </Button>
              <Button variant="outline">保存草稿</Button>
              <Button className="bg-blue-600 hover:bg-blue-700">提交</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
