import { get, post, put, del } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import { getUserCandidates } from "@/services/user";
import type {
  ProjectListVo,
  ProjectDetailVo,
  ProjectStatisticsVo,
  GetMyProjectListParams,
  GetAllProjectListParams,
  PageResult,
  CreateProjectParams,
  UpdateProjectParams,
  ProjectStageTemplate,
  FilesVo,
  UserListItem,
  SettlementProjectPage,
  ProjectTreeNode,
  ProjectTreeStatistics,
  ProjectTreeScope,
  ProjectTreeQuery,
} from "@/types/project";

function treeQuery(params: ProjectTreeQuery): Record<string, string> {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined).map(([key, value]) => [key, String(value)]));
}

export function getProjectTree(scope: ProjectTreeScope, params: ProjectTreeQuery, signal?: AbortSignal) {
  return get<PageResult<ProjectTreeNode>>(`/project/${scope}/tree`, {params: treeQuery(params), signal});
}

export function getProjectTreeChildren(scope: ProjectTreeScope, parentId: string, params: ProjectTreeQuery, signal?: AbortSignal) {
  return get<PageResult<ProjectListVo>>(`/project/${scope}/tree/${parentId}/children`, {params: treeQuery(params), signal});
}

export function getProjectTreeStatistics(scope: ProjectTreeScope, params: ProjectTreeQuery, signal?: AbortSignal) {
  return get<ProjectTreeStatistics>(`/project/${scope}/tree/statistics`, {params: treeQuery(params), signal});
}

/**
 * 新建项目
 */
export async function createProject(
  params: CreateProjectParams
): Promise<BaseResponse<number>> {
  return post<number>("/project/create", { body: params });
}

export async function createSettlementProject(parentId: string, params: CreateProjectParams) {
  return post<string>(`/project/${parentId}/settlements`, { body: params });
}

export async function getSettlementProjects(
  parentId: string,
  params: { keywords?: string; current?: number; pageSize?: number },
  signal?: AbortSignal,
): Promise<BaseResponse<SettlementProjectPage>> {
  const query: Record<string, string> = {};
  if (params.keywords) query.keywords = params.keywords;
  if (params.current !== undefined) query.current = String(params.current);
  if (params.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return get<SettlementProjectPage>(`/project/${parentId}/settlements`, { params: query, signal });
}

/**
 * 更新项目
 */
export async function updateProject(
  params: UpdateProjectParams
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/project/update", { body: params });
}

/**
 * 删除项目
 */
export async function deleteProject(
  projectId: string
): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/project/delete/${projectId}`);
}

/**
 * 批量启动阶段（未开始→进行中）
 */
export async function startStages(
  stageIds: string[]
): Promise<BaseResponse<boolean>> {
  return put<boolean>("/project/stage/start", { body: stageIds });
}

/**
 * 重启已驳回阶段（已驳回→进行中）
 */
export async function restartStage(
  stageId: string
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/project/stage/restart/${stageId}`);
}

/**
 * 更新阶段系数
 */
export async function updateStageCoefficient(
  stageId: string,
  coefficient: number
): Promise<BaseResponse<boolean>> {
  return put<boolean>(`/project/stage/coefficient?stageId=${stageId}&coefficient=${coefficient}`);
}

/**
 * 我的项目统计
 */
export async function getMyProjectStatistics(): Promise<
  BaseResponse<ProjectStatisticsVo>
> {
  return get<ProjectStatisticsVo>("/project/mine/statistics");
}

/**
 * 查询全部项目（分页 + 条件）
 */
export async function getAllProjects(
  params?: GetAllProjectListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<ProjectListVo>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.projectStatus !== undefined)
    query.projectStatus = String(params.projectStatus);
  if (params?.projectType !== undefined)
    query.projectType = String(params.projectType);
  if (params?.parentProjectId) query.parentProjectId = params.parentProjectId;
  if (params?.projectLevel !== undefined) query.projectLevel = String(params.projectLevel);
  if (params?.archiveStatus !== undefined) query.archiveStatus = String(params.archiveStatus);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<ProjectListVo>>("/project/all", { params: query, signal });
}

/**
 * 查询本人参与的项目（分页 + 条件）
 */
export async function getMyProjects(
  params?: GetMyProjectListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<ProjectListVo>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.projectStatus !== undefined)
    query.projectStatus = String(params.projectStatus);
  if (params?.parentProjectId) query.parentProjectId = params.parentProjectId;
  if (params?.projectLevel !== undefined) query.projectLevel = String(params.projectLevel);
  if (params?.archiveStatus !== undefined) query.archiveStatus = String(params.archiveStatus);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<ProjectListVo>>("/project/mine", { params: query, signal });
}

/**
 * 根据 id 查询项目详情
 */
export async function getProjectDetail(
  projectId: string
): Promise<BaseResponse<ProjectDetailVo>> {
  return get<ProjectDetailVo>(`/project/details/${projectId}`);
}

/**
 * 获取项目统计总览
 */
export async function getProjectStatistics(): Promise<
  BaseResponse<ProjectStatisticsVo>
> {
  return get<ProjectStatisticsVo>("/project/all/statistics");
}

/**
 * 获取所有启用的项目类型
 */
export async function getProjectTypes(): Promise<
  BaseResponse<{ projectTypeId: number; projectTypeName: string; projectTypeCode: string }[]>
> {
  return get("/project/types");
}

/**
 * 获取所有启用的阶段模板
 */
export async function getStageTemplates(
  projectTypeId?: number
): Promise<BaseResponse<ProjectStageTemplate[]>> {
  const params: Record<string, string> = {};
  if (projectTypeId !== undefined) params.projectTypeId = String(projectTypeId);
  return get<ProjectStageTemplate[]>("/project/stage-templates", { params });
}

/**
 * 获取用户列表（用于选择项目成员/审批人等业务场景）。
 *
 * <p>内部代理 {@link getUserCandidates}（GET /users/candidates），
 * 任何登录用户均可调用，不再要求 menu:user-management 权限。
 * 这是为了修复「ceshi 等普通员工在审批/上传文件等业务场景下选不到审批人」的问题。
 */
export async function getUserList(params?: {
  keywords?: string;
  departmentId?: string;
  includeChildren?: boolean;
}): Promise<
  BaseResponse<PageResult<UserListItem>>
> {
  const query: {
    keywords?: string;
    departmentId?: string;
    includeChildren?: boolean;
    pageSize: number;
    status: number;
    employmentStatus: number;
  } = {
    pageSize: 500,
    status: 1,
    employmentStatus: 1,
  };
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.departmentId) query.departmentId = params.departmentId;
  if (params?.includeChildren !== undefined) {
    query.includeChildren = params.includeChildren;
  }
  return getUserCandidates(query) as unknown as Promise<BaseResponse<PageResult<UserListItem>>>;
}

/** Compatibility wrappers; every upload now uses the same verified multipart API. */
export { MAX_FILE_SIZE as MAX_ATTACHMENT_FILE_SIZE } from "@/lib/file-upload";

export async function uploadDocument(file: File): Promise<BaseResponse<FilesVo>> {
  const { uploadFile } = await import("@/lib/file-upload");
  return { code: 200, msg: "上传成功", data: await uploadFile(file, "project") };
}
export const uploadImage = uploadDocument;
export const uploadAudio = uploadDocument;

export async function uploadProjectAttachment(file: File, onProgress?: (percent: number) => void): Promise<BaseResponse<FilesVo>> {
  const { uploadFile } = await import("@/lib/file-upload");
  return { code: 200, msg: "上传成功", data: await uploadFile(file, "project", { onProgress }) };
}

export function importProjectsByFileId(fileId: string, contractFileIds: string[] = []) {
  return post<import("@/types/project-import").ProjectImportResult>("/project/import-file", { body: { fileId, contractFileIds } });
}

// ==================== 导入导出 ====================

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

/**
 * 获取导出项目 URL（直接下载）
 */
export function getExportUrl(ids?: string[]): string {
  const params = new URLSearchParams();
  if (ids && ids.length > 0) {
    ids.forEach((id) => params.append("ids", id));
  }
  const query = params.toString();
  return `${BASE_URL}/project/export${query ? `?${query}` : ""}`;
}

/**
 * 获取下载模板 URL
 */
export function getTemplateUrl(): string {
  return `${BASE_URL}/project/export/template`;
}

/**
 * 导入项目
 */
export async function importProjects(
  file: File,
  contractFileIds: string[] = []
): Promise<BaseResponse<import("@/types/project-import").ProjectImportResult>> {
  const formData = new FormData();
  formData.append("file", file);
  contractFileIds.forEach((fileId) => formData.append("contractFileIds", fileId));

  return post<import("@/types/project-import").ProjectImportResult>("/project/import", {
    body: formData,
    headers: {},
  });
}
