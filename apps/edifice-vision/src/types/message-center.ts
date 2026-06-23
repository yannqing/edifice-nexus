export interface MessageCenterItem {
  messageKey: string;
  category: "approval" | "result" | "processed" | "announcement" | "project";
  categoryLabel: string;
  title: string;
  content?: string | null;
  link: string;
  priority: number;
  read: boolean;
  sourceType: string;
  sourceId: string;
  createdTime: string;
}

export interface MessageCenterListParams {
  category?: string;
  unreadOnly?: boolean;
  current?: number;
  pageSize?: number;
}
