"use client";

import { cn } from "@/lib/utils";
import type { CategoryStat } from "@/types";

interface CategoryDistributionProps {
  categories: CategoryStat[];
  totalCount: number;
}

export function CategoryDistribution({
  categories,
  totalCount,
}: CategoryDistributionProps) {
  return (
    <div className="glass-card rounded-2xl p-6 shadow-sm">
      <h3 className="text-lg font-bold text-slate-800 mb-6">项目分类分布</h3>
      <div className="space-y-4">
        {categories.map((item, idx) => (
          <div key={idx} className="flex items-center gap-3">
            <div className={cn("w-3 h-3 rounded-full", item.color)} />
            <div className="flex-1">
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-600">
                  {item.category} · {item.name}
                </span>
                <span className="text-slate-800 font-semibold">
                  {item.count} 个
                </span>
              </div>
              <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
                <div
                  className={cn("h-full", item.color)}
                  style={{ width: `${(item.count / totalCount) * 100}%` }}
                />
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-6 p-4 bg-slate-50 rounded-xl">
        <div className="flex justify-between items-center">
          <span className="text-sm text-slate-500">项目总数</span>
          <span className="text-2xl font-bold text-slate-800">{totalCount}</span>
        </div>
      </div>
    </div>
  );
}
