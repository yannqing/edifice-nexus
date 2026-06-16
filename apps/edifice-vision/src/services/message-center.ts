import { get, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type { MessageCenterItem, MessageCenterListParams } from "@/types/message-center";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
}

export function getMessageCenterList(
  params: MessageCenterListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<MessageCenterItem>>> {
  const query: Record<string, string> = {};
  if (params.category) query.category = params.category;
  if (params.unreadOnly !== undefined) query.unreadOnly = String(params.unreadOnly);
  if (params.current !== undefined) query.current = String(params.current);
  if (params.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return get<PageResult<MessageCenterItem>>("/message-center/list", { params: query, signal });
}

export function getUnreadMessageCount(signal?: AbortSignal): Promise<BaseResponse<number>> {
  return get<number>("/message-center/unread-count", { signal });
}

export function markMessageRead(sourceType: string, sourceId: string): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/message-center/read/${sourceType}/${sourceId}`);
}

export function markAllMessagesRead(): Promise<BaseResponse<boolean>> {
  return put<boolean>("/message-center/read-all");
}
