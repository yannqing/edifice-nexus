"use client";

import { ClipboardCheck, Coins } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { TodoItem } from "@/types";

interface TodoListProps {
  todos: TodoItem[];
}

export function TodoList({ todos }: TodoListProps) {
  const pendingCount = todos.length;

  return (
    <div className="glass-card rounded-2xl p-6 shadow-sm">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-bold text-slate-800">待办事项</h3>
        <Badge
          variant="secondary"
          className="text-xs bg-rose-100 text-rose-600 hover:bg-rose-100"
        >
          {pendingCount} 待处理
        </Badge>
      </div>
      <div className="space-y-3">
        {todos.map((todo) => (
          <div
            key={todo.id}
            className="flex items-start gap-3 p-3 bg-slate-50/50 rounded-xl hover:bg-slate-100/50 transition-colors cursor-pointer"
          >
            <div
              className={cn(
                "p-2 rounded-lg",
                todo.urgent
                  ? "bg-rose-100 text-rose-600"
                  : "bg-blue-100 text-blue-600"
              )}
            >
              {todo.type === "验工审批" ? (
                <ClipboardCheck className="w-4 h-4" />
              ) : (
                <Coins className="w-4 h-4" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400">{todo.type}</span>
                {todo.urgent && (
                  <span className="text-xs bg-rose-500 text-white px-1.5 py-0.5 rounded">
                    加急
                  </span>
                )}
              </div>
              <p className="text-sm font-medium text-slate-800 truncate">
                {todo.title}
              </p>
              <p className="text-xs text-slate-400 mt-1">
                来自 {todo.from} · {todo.time}
              </p>
            </div>
          </div>
        ))}
      </div>
      <button className="w-full mt-4 py-2 text-sm text-blue-600 font-medium hover:bg-blue-50 rounded-lg transition-colors">
        查看全部待办
      </button>
    </div>
  );
}
