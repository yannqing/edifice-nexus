"use client";

import { cn } from "@/lib/utils";
import type { PersonalStat } from "@/types";

interface PersonalStatsProps {
  stats: PersonalStat[];
}

export function PersonalStats({ stats }: PersonalStatsProps) {
  return (
    <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-bold text-slate-800">个人产值统计</h3>
        <button className="text-sm text-blue-600 font-medium hover:underline">
          查看详情
        </button>
      </div>
      <div className="grid grid-cols-4 gap-4">
        {stats.map((stat, idx) => (
          <div
            key={idx}
            className={cn("p-4 rounded-xl text-center", stat.bgColor)}
          >
            <p className={cn("text-xs font-medium mb-1", stat.textColor)}>
              {stat.label}
            </p>
            <p
              className={cn(
                "text-xl font-bold",
                stat.textColor.replace("600", "800")
              )}
            >
              {stat.value}
            </p>
            {stat.change && (
              <p className="text-xs text-emerald-600 mt-1">{stat.change}</p>
            )}
            {stat.note && (
              <p className="text-xs text-slate-400 mt-1">{stat.note}</p>
            )}
          </div>
        ))}
      </div>
      <div className="mt-6 pt-4 border-t border-slate-100">
        <div className="flex justify-between items-center text-sm">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-blue-500" />
              <span className="text-slate-500">管理工作</span>
              <span className="font-semibold text-slate-800">¥0.9万</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-emerald-500" />
              <span className="text-slate-500">基础工作</span>
              <span className="font-semibold text-slate-800">¥0.4万</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-amber-500" />
              <span className="text-slate-500">智励工作</span>
              <span className="font-semibold text-slate-800">¥0.2万</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
