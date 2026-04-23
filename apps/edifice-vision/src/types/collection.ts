/** 回款状态：0-未回款 / 1-部分回款 / 2-已回款 */
export const COLLECTION_STATUS_MAP: Record<number, string> = {
  0: "未回款",
  1: "部分回款",
  2: "已回款",
};

/** 项目级别的回款汇总（列表页一行） */
export interface CollectionSummaryVo {
  projectId: string;
  projectName: string;
  projectCode: string;
  projectTypeCode: string | null;
  projectTypeName: string | null;
  managerName: string | null;
  contractAmount: number;
  completedPhases: string;
  expectedAmount: number;
  collectedAmount: number;
  /** 0-未回款 / 1-部分回款 / 2-已回款 */
  collectionStatus: number;
}

/** 阶段维度的回款聚合 */
export interface StageCollectionVo {
  projectStageId: string;
  stageName: string;
  stageOutput: number;
  planAmount: number;
  actualAmount: number;
  /** 0-未回款 / 1-部分回款 / 2-已回款 */
  status: number;
}

/** 单条回款记录 */
export interface CollectionRecordVo {
  collectionRecordId: string;
  projectId: string;
  projectName: string | null;
  projectCode: string | null;
  projectStageId: string | null;
  stageName: string | null;
  amount: number;
  collectDate: string;
  voucherFileId: string | null;
  remark: string | null;
  recordUserId: string | null;
  recordUserName: string | null;
  createdTime: string;
  updatedTime: string;
}

/** 详情弹窗返回 */
export interface CollectionDetailVo {
  summary: CollectionSummaryVo;
  stageCollections: StageCollectionVo[];
  records: CollectionRecordVo[];
}

/** 顶部统计卡片 */
export interface CollectionStatisticsVo {
  totalExpected: number;
  totalCollected: number;
  totalPending: number;
}

/** 录入回款记录参数 */
export interface CreateCollectionRecordParams {
  projectId: string;
  projectStageId?: string;
  amount: number;
  collectDate: string;
  voucherFileId?: string;
  remark?: string;
}

/** 更新回款记录参数 */
export interface UpdateCollectionRecordParams {
  collectionRecordId: string;
  projectStageId?: string;
  amount?: number;
  collectDate?: string;
  voucherFileId?: string;
  remark?: string;
}

/** 列表查询参数 */
export interface GetCollectionListParams {
  keywords?: string;
  collectionStatus?: number;
  current?: number;
  pageSize?: number;
}
