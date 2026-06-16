export interface OperationAuditLogVo {
  auditLogId: string;
  operatorId?: string | null;
  operatorName?: string | null;
  moduleName: string;
  operationName: string;
  httpMethod: string;
  requestPath: string;
  clientIp?: string | null;
  status: number;
  costMs?: number | null;
  requestSummary?: string | null;
  errorMessage?: string | null;
  createdTime: string;
}

export interface GetOperationAuditLogListParams {
  operatorName?: string;
  moduleName?: string;
  operationName?: string;
  httpMethod?: string;
  status?: number;
  startTime?: string;
  endTime?: string;
  current?: number;
  pageSize?: number;
}
