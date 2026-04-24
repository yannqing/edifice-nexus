import type { ApprovalRecordVo } from "@/types/approval";

export interface BidFileVo {
  bidFileId: string;
  fileId: string;
  fileName: string | null;
  fileUrl: string | null;
  fileExtension: string | null;
  fileSize: string | null;
  fileCategory: string | null;
  createdTime: string;
}

export interface BidVo {
  bidId: string;
  bidName: string;
  bidCode: string | null;
  ownerUserId: string;
  ownerUserName: string | null;
  tenderAmount: number | null;
  /** 0-筹备/1-已投递/2-中标/3-未中标/4-终止 */
  bidStatus: number;
  bidStatusLabel: string;
  bidDate: string | null;
  resultDate: string | null;
  clientName: string | null;
  description: string | null;
  /** 0-草稿/1-审核中/2-通过/3-驳回 */
  approvalStatus: number;
  approvalStatusLabel: string;
  currentRecordId: string | null;
  currentApproverId: string | null;
  currentApproverName: string | null;
  createdTime: string;
  updatedTime: string | null;
  files?: BidFileVo[] | null;
  approvalChain?: ApprovalRecordVo[] | null;
}

export interface CreateBidFileItem {
  fileId: string;
  fileCategory?: string;
}

export interface CreateBidParams {
  bidName: string;
  bidCode?: string;
  ownerUserId: string;
  tenderAmount?: number;
  clientName?: string;
  bidDate?: string;
  resultDate?: string;
  description?: string;
  files?: CreateBidFileItem[];
}

export interface UpdateBidParams extends Partial<CreateBidParams> {
  bidId: string;
}

export interface UpdateBidStatusParams {
  bidId: string;
  bidStatus: number;
  bidDate?: string;
  resultDate?: string;
}

export const BID_STATUS_OPTIONS = [
  { value: 0, label: "筹备", color: "slate" },
  { value: 1, label: "已投递", color: "blue" },
  { value: 2, label: "中标", color: "emerald" },
  { value: 3, label: "未中标", color: "rose" },
  { value: 4, label: "终止", color: "amber" },
] as const;

export const BID_APPROVAL_STATUS_MAP: Record<number, string> = {
  0: "草稿",
  1: "审核中",
  2: "已通过",
  3: "已驳回",
};

export const BID_FILE_CATEGORIES = [
  "招标文件",
  "投标文件",
  "中标通知",
  "其他",
];
