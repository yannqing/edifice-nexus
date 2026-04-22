"use client";

import { cn } from "@/lib/utils";

/**
 * 基础骨架元素
 */
function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded-lg bg-slate-200/60", className)}
      {...props}
    />
  );
}

/**
 * 列表页骨架屏（统计卡片 + 表格行）
 * 适用于：全部项目、验工审批、验工单管理
 */
function TablePageSkeleton({ columns = 5, rows = 5 }: { columns?: number; rows?: number }) {
  return (
    <div className="space-y-6">
      {/* 统计卡片骨架 */}
      <div className={cn("grid gap-4", `grid-cols-${columns}`)}>
        {Array.from({ length: columns }).map((_, i) => (
          <div key={i} className="glass-card p-4 rounded-xl">
            <div className="flex items-center gap-3">
              <Skeleton className="w-10 h-10 rounded-lg" />
              <div className="space-y-2 flex-1">
                <Skeleton className="h-3 w-16" />
                <Skeleton className="h-6 w-10" />
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* 筛选栏骨架 */}
      <div className="flex items-center gap-4">
        <Skeleton className="h-10 w-80 rounded-xl" />
        <div className="flex-1" />
        <Skeleton className="h-10 w-72 rounded-xl" />
      </div>

      {/* 表格骨架 */}
      <div className="glass-card rounded-2xl shadow-sm overflow-hidden">
        {/* 表头 */}
        <div className="bg-slate-50/50 px-6 py-4 flex gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-3 flex-1" />
          ))}
        </div>
        {/* 表行 */}
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="px-6 py-4 flex items-center gap-4 border-t border-slate-100">
            <div className="flex-[2] space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
            <Skeleton className="flex-1 h-4" />
            <Skeleton className="flex-1 h-4" />
            <div className="flex-1 flex items-center gap-2">
              <Skeleton className="w-7 h-7 rounded-full" />
              <Skeleton className="h-4 w-12" />
            </div>
            <Skeleton className="flex-1 h-4" />
            <Skeleton className="w-16 h-6 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * 卡片页骨架屏
 * 适用于：我的项目
 */
function CardPageSkeleton({ cards = 6 }: { cards?: number }) {
  return (
    <div className="space-y-6">
      {/* Tab 骨架 */}
      <div className="flex items-center gap-4">
        <Skeleton className="h-10 w-96 rounded-xl" />
        <div className="flex-1" />
        <Skeleton className="h-10 w-64 rounded-xl" />
      </div>

      {/* 卡片网格骨架 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
        {Array.from({ length: cards }).map((_, i) => (
          <div key={i} className="glass-card rounded-2xl p-6 space-y-4">
            <div className="flex justify-between">
              <Skeleton className="h-6 w-24 rounded-md" />
              <Skeleton className="h-6 w-16 rounded-full" />
            </div>
            <div className="space-y-2">
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-3 w-1/3" />
            </div>
            <div className="space-y-2">
              <div className="flex justify-between">
                <Skeleton className="h-3 w-24" />
                <Skeleton className="h-3 w-8" />
              </div>
              <div className="flex gap-1">
                {Array.from({ length: 5 }).map((_, j) => (
                  <Skeleton key={j} className="h-1.5 flex-1 rounded-full" />
                ))}
              </div>
            </div>
            <Skeleton className="h-px w-full" />
            <div className="flex justify-between">
              <Skeleton className="h-10 w-20" />
              <Skeleton className="h-10 w-20" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * 弹窗内容骨架屏
 * 适用于：详情弹窗、编辑弹窗的加载态
 */
function DialogSkeleton() {
  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <Skeleton className="h-5 w-48" />
        <Skeleton className="h-4 w-32" />
      </div>
      <div className="grid grid-cols-2 gap-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="py-2 px-3 bg-slate-50 rounded-lg space-y-1.5">
            <Skeleton className="h-3 w-16" />
            <Skeleton className="h-4 w-24" />
          </div>
        ))}
      </div>
      <Skeleton className="h-24 w-full rounded-xl" />
      <div className="space-y-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full rounded-lg" />
        ))}
      </div>
    </div>
  );
}

export { Skeleton, TablePageSkeleton, CardPageSkeleton, DialogSkeleton };
