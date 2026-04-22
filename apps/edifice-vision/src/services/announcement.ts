import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  AnnouncementVo,
  CreateAnnouncementParams,
  GetAnnouncementListParams,
  UpdateAnnouncementParams,
} from "@/types/announcement";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize: number;
}

export async function getAnnouncementList(
  params?: GetAnnouncementListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<AnnouncementVo>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.priority !== undefined) query.priority = String(params.priority);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<AnnouncementVo>>("/announcement/list", {
    params: query,
    signal,
  });
}

/** 首页用：近期已发布的公告 */
export async function getRecentAnnouncements(
  limit = 5
): Promise<BaseResponse<AnnouncementVo[]>> {
  return get<AnnouncementVo[]>("/announcement/recent", {
    params: { limit: String(limit) },
  });
}

export async function getAnnouncementDetail(
  id: string
): Promise<BaseResponse<AnnouncementVo>> {
  return get<AnnouncementVo>(`/announcement/${id}`);
}

export async function createAnnouncement(
  params: CreateAnnouncementParams
): Promise<BaseResponse<number>> {
  return post<number>("/announcement/create", { body: params });
}

export async function updateAnnouncement(
  params: UpdateAnnouncementParams
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/announcement/update", { body: params });
}

export async function publishAnnouncement(
  id: string
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/announcement/publish/${id}`);
}

export async function unpublishAnnouncement(
  id: string
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/announcement/unpublish/${id}`);
}

export async function deleteAnnouncement(
  id: string
): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/announcement/${id}`);
}
