import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  BidVo,
  CreateBidParams,
  UpdateBidParams,
  UpdateBidStatusParams,
} from "@/types/bid";
import type { ApproveParams } from "@/types/approval";

export async function createBid(
  params: CreateBidParams,
): Promise<BaseResponse<number>> {
  return post<number>("/bids/create", { body: params });
}

export async function updateBid(
  params: UpdateBidParams,
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/bids/update", { body: params });
}

export async function deleteBid(id: string): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/bids/${id}`);
}

export async function updateBidStatus(
  params: UpdateBidStatusParams,
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/bids/status", { body: params });
}

export async function submitBidApproval(
  bidId: string,
  firstApproverId: string,
): Promise<BaseResponse<boolean>> {
  return post<boolean>("/bids/submit-approval", {
    params: { bidId, firstApproverId },
  });
}

export async function approveBid(
  params: ApproveParams,
): Promise<BaseResponse<boolean>> {
  return post<boolean>("/bids/approve", { body: params });
}

export async function getBidList(params?: {
  bidStatus?: number;
  approvalStatus?: number;
  keyword?: string;
}): Promise<BaseResponse<BidVo[]>> {
  const q: Record<string, string> = {};
  if (params?.bidStatus !== undefined) q.bidStatus = String(params.bidStatus);
  if (params?.approvalStatus !== undefined)
    q.approvalStatus = String(params.approvalStatus);
  if (params?.keyword) q.keyword = params.keyword;
  return get<BidVo[]>("/bids/list", { params: q });
}

export async function getBidDetail(id: string): Promise<BaseResponse<BidVo>> {
  return get<BidVo>(`/bids/${id}`);
}

export async function getMyPendingBids(): Promise<BaseResponse<BidVo[]>> {
  return get<BidVo[]>("/bids/my-pending");
}
