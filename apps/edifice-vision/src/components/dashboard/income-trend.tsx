"use client";

import { Zap } from "lucide-react";
import type { IncomeTrend } from "@/types";

interface IncomeTrendProps {
  trends: IncomeTrend[];
}

export function IncomeTrendCard({ trends }: IncomeTrendProps) {
  return (
    <div className="glass-card rounded-2xl p-6 shadow-sm">
      <h3 className="text-lg font-bold text-slate-800 mb-6">收入趋势对比</h3>
      <div className="space-y-6">
        {trends.map((trend, idx) => (
          <div key={idx} className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500 font-medium">{trend.time}</span>
              <span className="text-slate-900 font-bold">{trend.amount}</span>
            </div>
            <div className="h-2 bg-slate-100 rounded-full flex overflow-hidden">
              <div
                className="bg-blue-600"
                style={{ width: `${trend.bar1Percent}%` }}
              />
              <div
                className="bg-emerald-400"
                style={{ width: `${trend.bar2Percent}%` }}
              />
            </div>
          </div>
        ))}
        <div className="p-4 bg-blue-50 rounded-xl mt-6">
          <div className="flex gap-3">
            <Zap className="w-5 h-5 text-blue-600 flex-shrink-0" />
            <div>
              <p className="text-sm font-bold text-blue-900">系统提示</p>
              <p className="text-xs text-blue-700 mt-1">
                当前有 2 个项目效益收费已达到修正阈值，建议及时调整预计效益。
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
