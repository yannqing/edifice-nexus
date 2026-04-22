import { get, post, put, del } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  CollectionDetailVo,
  CollectionStatisticsVo,
  CollectionSummaryVo,
  CreateCollectionRecordParams,
  GetCollectionListParams,
  UpdateCollectionRecordParams,
} from "@/types/collection";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize: number;
}

export async function getCollectionList(
  params?: GetCollectionListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<CollectionSummaryVo>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.collectionStatus !== undefined)
    query.collectionStatus = String(params.collectionStatus);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<CollectionSummaryVo>>("/collection/list", {
    params: query,
    signal,
  });
}

export async function getCollectionStatistics(): Promise<
  BaseResponse<CollectionStatisticsVo>
> {
  return get<CollectionStatisticsVo>("/collection/statistics");
}

export async function getCollectionDetail(
  projectId: string
): Promise<BaseResponse<CollectionDetailVo>> {
  return get<CollectionDetailVo>(`/collection/detail/${projectId}`);
}

export async function createCollectionRecord(
  params: CreateCollectionRecordParams
): Promise<BaseResponse<number>> {
  return post<number>("/collection/record", { body: params });
}

export async function updateCollectionRecord(
  params: UpdateCollectionRecordParams
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/collection/record", { body: params });
}

export async function deleteCollectionRecord(
  id: string
): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/collection/record/${id}`);
}
