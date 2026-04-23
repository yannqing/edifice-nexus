import { del, get, post, put } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type { SysUser } from "@/types/auth";

interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  pageSize: number;
}

// ==================== 个人中心 ====================

/** 个人中心可自助修改的字段子集 */
export interface UpdateProfileParams {
  realName?: string;
  /** 0-男/1-女/2-其他 */
  gender?: number;
  ethnicity?: string;
  birthDate?: string;
  email?: string;
  phone?: string;
  avatar?: string;
  education?: string;
  school?: string;
  major?: string;
  certificates?: string;
  domicile?: string;
  address?: string;
  remark?: string;
}

/**
 * 获取当前登录用户的个人资料
 */
export async function getProfile(): Promise<BaseResponse<SysUser>> {
  return get<SysUser>("/users/profile");
}

/**
 * 更新当前登录用户的个人资料
 */
export async function updateProfile(
  params: UpdateProfileParams
): Promise<BaseResponse<SysUser>> {
  return put<SysUser>("/users/profile", { body: params });
}

// ==================== 管理端 ====================

/** 列表查询参数 */
export interface GetUserListParams {
  /** 统一关键字：同时模糊匹配 用户名/姓名/工号/手机号（OR） */
  keywords?: string;
  username?: string;
  realName?: string;
  employeeNo?: string;
  email?: string;
  phone?: string;
  position?: string;
  /** 0-离职/1-在职 */
  employmentStatus?: number;
  /** 0-禁用/1-启用 */
  status?: number;
  current?: number;
  pageSize?: number;
}

/** 列表返回的一行（对齐 SysUserListVo） */
export interface SysUserListItem {
  userId: string;
  username: string;
  employeeNo: string | null;
  realName: string | null;
  gender: number | null;
  email: string | null;
  phone: string | null;
  avatar: string | null;
  position: string | null;
  professionalTitle: string | null;
  entryDate: string | null;
  employmentStatus: number | null;
  status: number;
  lastLoginTime: string | null;
}

/** 新增用户参数（对齐 SysUserCreateDto） */
export interface CreateUserParams {
  username: string;
  employeeNo?: string;
  realName?: string;
  gender?: number;
  ethnicity?: string;
  birthDate?: string;
  idCard?: string;
  email?: string;
  phone?: string;
  avatar?: string;
  education?: string;
  school?: string;
  major?: string;
  position?: string;
  professionalTitle?: string;
  certificates?: string;
  entryDate?: string;
  contractEndDate?: string;
  socialInsuranceDate?: string;
  employmentStatus?: number;
  domicile?: string;
  address?: string;
  remark?: string;
}

/** 更新用户参数（对齐 SysUserUpdateDto，字段全部可选） */
export interface UpdateUserParams extends Partial<CreateUserParams> {
  userId: string;
  resignDate?: string;
  /** 账号状态 */
  status?: number;
}

export async function getUserList(
  params?: GetUserListParams,
  signal?: AbortSignal
): Promise<BaseResponse<PageResult<SysUserListItem>>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.username) query.username = params.username;
  if (params?.realName) query.realName = params.realName;
  if (params?.employeeNo) query.employeeNo = params.employeeNo;
  if (params?.email) query.email = params.email;
  if (params?.phone) query.phone = params.phone;
  if (params?.position) query.position = params.position;
  if (params?.employmentStatus !== undefined) query.employmentStatus = String(params.employmentStatus);
  if (params?.status !== undefined) query.status = String(params.status);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);

  return get<PageResult<SysUserListItem>>("/users/all", { params: query, signal });
}

export async function getUserDetail(id: string): Promise<BaseResponse<SysUser>> {
  return get<SysUser>(`/users/${id}`);
}

export async function createUser(params: CreateUserParams): Promise<BaseResponse<boolean>> {
  return post<boolean>("/users/create", { body: params });
}

export async function updateUser(params: UpdateUserParams): Promise<BaseResponse<boolean>> {
  return put<boolean>("/users/update", { body: params });
}

export async function deleteUser(id: string): Promise<BaseResponse<boolean>> {
  return del<boolean>(`/users/${id}`);
}

// ==================== Excel 导入 / 模板 ====================

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

/** 用户导入模板下载 URL（通过 a 标签触发下载，需要附带 token） */
export function getUserTemplateUrl(): string {
  return `${BASE_URL}/users/export/template`;
}

/** 批量导入花名册 Excel，返回后端生成的结果描述字符串 */
export async function importUsers(file: File): Promise<BaseResponse<string>> {
  const formData = new FormData();
  formData.append("file", file);

  return post<string>("/users/import", {
    body: formData,
    // 让浏览器自动设置 multipart/form-data boundary
    headers: {},
  });
}
