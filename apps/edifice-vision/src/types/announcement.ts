/** 公告优先级：0-普通 / 1-重要 / 2-紧急 */
export const ANNOUNCEMENT_PRIORITY_MAP: Record<number, string> = {
  0: "普通",
  1: "重要",
  2: "紧急",
};

/** 公告状态：0-草稿 / 1-已发布 / 2-已下线 */
export const ANNOUNCEMENT_STATUS_MAP: Record<number, string> = {
  0: "草稿",
  1: "已发布",
  2: "已下线",
};

export interface AnnouncementVo {
  announcementId: string;
  title: string;
  content: string;
  /** 0-普通 / 1-重要 / 2-紧急 */
  priority: number;
  /** 0-草稿 / 1-已发布 / 2-已下线 */
  status: number;
  publishTime: string | null;
  expireTime: string | null;
  publishUserId: string | null;
  publishUserName: string | null;
  createdTime: string;
  updatedTime: string;
}

export interface CreateAnnouncementParams {
  title: string;
  content: string;
  priority?: number;
  /** 0-草稿 / 1-立即发布 */
  status?: number;
  expireTime?: string;
}

export interface UpdateAnnouncementParams {
  announcementId: string;
  title?: string;
  content?: string;
  priority?: number;
  expireTime?: string;
}

export interface GetAnnouncementListParams {
  keywords?: string;
  status?: number;
  priority?: number;
  current?: number;
  pageSize?: number;
}
