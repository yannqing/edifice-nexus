"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, ChevronLeft, ChevronRight, Loader2, Search, Users } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { isAbortError } from "@/lib/request";
import { getUserCandidates, type SysUserListItem } from "@/services/user";
import { ResponseCode } from "@/types/api";

const PAGE_SIZE = 8;

interface UserPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  value?: string;
  onSelect: (user: SysUserListItem) => void;
  title?: string;
}

function displayName(user: SysUserListItem) {
  return user.realName || user.username;
}

export function UserPickerDialog({
  open,
  onOpenChange,
  value,
  onSelect,
  title = "选择审批人",
}: UserPickerDialogProps) {
  const [searchText, setSearchText] = useState("");
  const [keywords, setKeywords] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [users, setUsers] = useState<SysUserListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [selectedId, setSelectedId] = useState("");
  const [selectedUser, setSelectedUser] = useState<SysUserListItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [reloadVersion, setReloadVersion] = useState(0);

  useEffect(() => {
    if (!open) return;
    setSearchText("");
    setKeywords("");
    setCurrentPage(1);
    setSelectedId(value ?? "");
    setSelectedUser(null);
    setLoadError("");
  }, [open, value]);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => {
      setKeywords(searchText.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [open, searchText]);

  const loadUsers = useCallback(
    async (signal?: AbortSignal) => {
      if (!open) return;
      setLoading(true);
      setLoadError("");
      try {
        const res = await getUserCandidates(
          {
            keywords: keywords || undefined,
            current: currentPage,
            pageSize: PAGE_SIZE,
          },
          signal
        );
        if (res.code !== ResponseCode.SUCCESS || !res.data) {
          throw new Error(res.msg || "候选人加载失败");
        }
        setUsers(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
      } catch (error) {
        if (isAbortError(error)) return;
        setUsers([]);
        setTotal(0);
        setLoadError(error instanceof Error ? error.message : "候选人加载失败，请稍后重试");
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [open, keywords, currentPage]
  );

  useEffect(() => {
    const controller = new AbortController();
    loadUsers(controller.signal);
    return () => controller.abort();
  }, [loadUsers, reloadVersion]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const handleConfirm = () => {
    if (selectedUser) onSelect(selectedUser);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl p-0">
        <DialogHeader className="border-b border-slate-100 px-5 pb-4 pt-5 sm:px-6">
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>仅显示在职且已启用的员工</DialogDescription>
        </DialogHeader>

        <div className="px-5 pt-4 sm:px-6">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              placeholder="搜索姓名、用户名、工号或手机号"
              className="form-input pl-9"
              autoFocus
            />
          </div>
        </div>

        <div className="min-h-[352px] px-5 py-4 sm:px-6">
          {loading ? (
            <div className="flex min-h-[320px] items-center justify-center gap-2 text-sm text-slate-500">
              <Loader2 className="h-5 w-5 animate-spin text-blue-500" />
              正在加载员工列表...
            </div>
          ) : loadError ? (
            <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 text-center">
              <p className="text-sm text-slate-500">{loadError}</p>
              <Button variant="outline" size="sm" onClick={() => setReloadVersion((value) => value + 1)}>
                重新加载
              </Button>
            </div>
          ) : users.length === 0 ? (
            <div className="flex min-h-[320px] flex-col items-center justify-center text-center">
              <Users className="mb-3 h-8 w-8 text-slate-300" />
              <p className="text-sm font-medium text-slate-700">没有找到员工</p>
              <p className="mt-1 text-xs text-slate-400">请调整搜索条件后重试</p>
            </div>
          ) : (
            <div className="overflow-hidden rounded-md border border-slate-200" role="radiogroup" aria-label="审批人列表">
              {users.map((user) => {
                const active = selectedId === String(user.userId);
                return (
                  <button
                    key={user.userId}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    onClick={() => {
                      setSelectedId(String(user.userId));
                      setSelectedUser(user);
                    }}
                    className={`flex min-h-16 w-full items-center gap-3 border-b border-slate-100 px-3 py-2.5 text-left transition-colors last:border-b-0 ${
                      active ? "bg-blue-50" : "bg-white hover:bg-slate-50"
                    }`}
                  >
                    <Avatar size="lg">
                      {user.avatar && <AvatarImage src={user.avatar} alt="" />}
                      <AvatarFallback className="bg-slate-100 text-xs font-medium text-slate-600">
                        {displayName(user).slice(0, 2)}
                      </AvatarFallback>
                    </Avatar>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2">
                        <span className="truncate text-sm font-medium text-slate-800">{displayName(user)}</span>
                        <span className="truncate text-xs text-slate-400">{user.username}</span>
                      </span>
                      <span className="mt-0.5 block truncate text-xs text-slate-500">
                        {[user.departmentName, user.positionName].filter(Boolean).join(" · ") || "未设置部门和岗位"}
                      </span>
                    </span>
                    <span
                      className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${
                        active ? "border-blue-600 bg-blue-600 text-white" : "border-slate-300 text-transparent"
                      }`}
                    >
                      <Check className="h-3 w-3" />
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span>共 {total} 人</span>
            <span>第 {currentPage} / {totalPages} 页</span>
            <Button
              type="button"
              variant="outline"
              size="icon-xs"
              title="上一页"
              aria-label="上一页"
              disabled={loading || currentPage <= 1}
              onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
            >
              <ChevronLeft />
            </Button>
            <Button
              type="button"
              variant="outline"
              size="icon-xs"
              title="下一页"
              aria-label="下一页"
              disabled={loading || currentPage >= totalPages}
              onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
            >
              <ChevronRight />
            </Button>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button type="button" disabled={!selectedId} onClick={handleConfirm}>
              确认选择
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
