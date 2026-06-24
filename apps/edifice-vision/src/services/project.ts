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
} from "@/types/project";

/**
 * 新建项目
 */
export async function createProject(
  params: CreateProjectParams
): Promise<BaseResponse<number>> {
  return post<number>("/project/create", { body: params });
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

/**
 * 上传合同文档
 */
export async function uploadDocument(
  file: File
): Promise<BaseResponse<FilesVo>> {
  const formData = new FormData();
  formData.append("document", file);

  return post<FilesVo>("/file/upload/document", {
    body: formData,
    // 让浏览器自动设置 multipart/form-data boundary
    headers: {},
  });
}

export async function uploadImage(file: File): Promise<BaseResponse<FilesVo>> {
  const formData = new FormData();
  formData.append("image", file);

  return post<FilesVo>("/file/upload/images", {
    body: formData,
    headers: {},
  });
}

export async function uploadAudio(file: File): Promise<BaseResponse<FilesVo>> {
  const formData = new FormData();
  formData.append("audio", file);

  return post<FilesVo>("/file/upload/audio", {
    body: formData,
    headers: {},
  });
}

const imageExtensions = new Set([
  "jpg",
  "jpeg",
  "png",
  "gif",
  "bmp",
  "webp",
  "svg",
  "ico",
  "tiff",
  "tif",
]);

const audioExtensions = new Set([
  "mp3",
  "wav",
  "flac",
  "aac",
  "ogg",
  "wma",
  "m4a",
  "opus",
]);

function getFileExtension(file: File): string {
  const name = file.name.toLowerCase();
  const dotIndex = name.lastIndexOf(".");
  return dotIndex >= 0 ? name.slice(dotIndex + 1) : "";
}

/**
 * 上传项目文件附件。根据文件类型选择后端对应上传接口。
 * 大文件（>10MB）自动走分片上传。
 */
export async function uploadProjectAttachment(
  file: File,
  onProgress?: (percent: number) => void
): Promise<BaseResponse<FilesVo>> {
  const extension = getFileExtension(file);
  const { isSmallFile, uploadFileInChunks } = await import("@/lib/chunk-upload");

  // 小文件走原有直传
  if (isSmallFile(file)) {
    if (file.type.startsWith("image/") || imageExtensions.has(extension)) {
      return uploadImage(file);
    }
    if (file.type.startsWith("audio/") || audioExtensions.has(extension)) {
      return uploadAudio(file);
    }
    return uploadDocument(file);
  }

  // 大文件走分片上传
  let fileType = "document";
  if (file.type.startsWith("image/") || imageExtensions.has(extension)) fileType = "image";
  if (file.type.startsWith("audio/") || audioExtensions.has(extension)) fileType = "audio";

  return uploadFileInChunks(file, { onProgress, fileType });
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
  file: File
): Promise<BaseResponse<string>> {
  const formData = new FormData();
  formData.append("file", file);

  return post<string>("/project/import", {
    body: formData,
    headers: {},
  });
}
