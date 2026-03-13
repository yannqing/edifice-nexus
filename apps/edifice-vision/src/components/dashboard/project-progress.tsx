"use client";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { MyProject } from "@/types";

interface ProjectProgressProps {
  projects: MyProject[];
}

const statusColors: Record<string, string> = {
  已完成: "bg-emerald-100 text-emerald-600",
  待验收: "bg-amber-100 text-amber-600",
  进行中: "bg-blue-100 text-blue-600",
  未开始: "bg-slate-100 text-slate-500",
};

export function ProjectProgress({ projects }: ProjectProgressProps) {
  return (
    <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-bold text-slate-800">我的项目进度</h3>
        <button className="text-sm text-blue-600 font-medium hover:underline">
          管理项目
        </button>
      </div>
      <div className="space-y-4">
        {projects.map((project) => (
          <div
            key={project.id}
            className="p-4 bg-slate-50/50 rounded-xl hover:bg-slate-100/50 transition-colors"
          >
            <div className="flex justify-between items-start mb-3">
              <div>
                <span className="text-sm font-semibold text-slate-800">
                  {project.name}
                </span>
                <span className="ml-2 text-xs text-slate-400">
                  {project.category}
                </span>
              </div>
              <Badge
                variant="secondary"
                className={cn(
                  "text-xs font-medium",
                  statusColors[project.status]
                )}
              >
                {project.status}
              </Badge>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex-1 flex gap-1">
                {[...Array(project.phases)].map((_, i) => (
                  <div
                    key={i}
                    className={cn(
                      "h-2 flex-1 rounded-full",
                      i < project.currentPhase ? "bg-blue-500" : "bg-slate-200"
                    )}
                  />
                ))}
              </div>
              <span className="text-xs text-slate-500 font-medium whitespace-nowrap">
                阶段 {project.currentPhase}/{project.phases}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
