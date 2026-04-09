"use client";

import {
  Layers,
  TrendingUp,
  PieChart,
  ClipboardCheck,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type { StatItem } from "@/types";

const iconMap: Record<string, React.ComponentType<{ className?: string }>> = {
  Layers,
  TrendingUp,
  PieChart,
  ClipboardCheck,
};

const colorMap = {
  blue: {
    bg: "bg-blue-100",
    text: "text-blue-600",
  },
  emerald: {
    bg: "bg-emerald-100",
    text: "text-emerald-600",
  },
  amber: {
    bg: "bg-amber-100",
    text: "text-amber-600",
  },
  rose: {
    bg: "bg-rose-100",
    text: "text-rose-600",
  },
};

interface StatsCardProps {
  stat: StatItem;
}

export function StatsCard({ stat }: StatsCardProps) {
  const Icon = iconMap[stat.icon];
  const colors = colorMap[stat.color];
  const isPositive = stat.change.startsWith("+");

  return (
    <div className="glass-card p-6 rounded-2xl shadow-sm hover:shadow-md transition-shadow">
      <div className="flex justify-between items-start">
        <div className={cn("p-3 rounded-xl", colors.bg, colors.text)}>
          {Icon && <Icon className="w-5 h-5" />}
        </div>
        <span
          className={cn(
            "text-xs font-bold px-2 py-1 rounded-lg",
            isPositive
              ? "bg-emerald-50 text-emerald-600"
              : "bg-slate-100 text-slate-600"
          )}
        >
          {stat.change}
        </span>
      </div>
      <div className="mt-4">
        <p className="text-sm font-medium text-slate-500">{stat.label}</p>
        <h3 className="text-2xl font-bold text-slate-900 mt-1">{stat.value}</h3>
      </div>
    </div>
  );
}

interface StatsGridProps {
  stats: StatItem[];
}

export function StatsGrid({ stats }: StatsGridProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {stats.map((stat, idx) => (
        <StatsCard key={idx} stat={stat} />
      ))}
    </div>
  );
}
