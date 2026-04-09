"use client";

import { MoreHorizontal } from "lucide-react";
import { Progress } from "@/components/ui/progress";
import type { Project } from "@/types";

interface ProjectsTableProps {
  projects: Project[];
}

export function ProjectsTable({ projects }: ProjectsTableProps) {
  return (
    <div className="lg:col-span-2 glass-card rounded-2xl p-6 shadow-sm">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-bold text-slate-800">关键项目回款看板</h3>
        <button className="text-sm text-blue-600 font-medium hover:underline">
          查看全部
        </button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left">
          <thead>
            <tr className="text-slate-400 text-xs uppercase tracking-wider border-b border-slate-50">
              <th className="pb-4 font-semibold">项目名称</th>
              <th className="pb-4 font-semibold">项目经理</th>
              <th className="pb-4 font-semibold">验收进度</th>
              <th className="pb-4 font-semibold text-center">回款率</th>
              <th className="pb-4 font-semibold text-right">操作</th>
            </tr>
          </thead>
          <tbody className="text-sm divide-y divide-slate-50">
            {projects.map((project) => (
              <tr
                key={project.id}
                className="group hover:bg-slate-50/50 transition-colors"
              >
                <td className="py-4">
                  <span className="font-semibold text-slate-800 block">
                    {project.name}
                  </span>
                  <span className="text-xs text-slate-400">
                    分类：{project.type} | 金额：¥{project.amount}
                  </span>
                </td>
                <td className="py-4 text-slate-600">{project.manager}</td>
                <td className="py-4">
                  <div className="w-24">
                    <Progress value={project.progress} className="h-1.5" />
                  </div>
                  <span className="text-[10px] text-slate-400 mt-1 block">
                    {project.progress}% 已验收
                  </span>
                </td>
                <td className="py-4 text-center text-slate-800 font-medium">
                  {project.collection}%
                </td>
                <td className="py-4 text-right">
                  <button className="p-2 text-slate-400 hover:text-blue-600 transition-colors">
                    <MoreHorizontal className="w-4 h-4" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
