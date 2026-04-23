// ==================== 用户实体 ====================

export interface SysUser {
  userId: number | string;
  username: string;
  /** 员工编号（花名册编号） */
  employeeNo?: string | null;
  realName: string;
  /** 0-男/1-女/2-其他 */
  gender?: number | null;
  ethnicity?: string | null;
  birthDate?: string | null;
  idCard?: string | null;
  email: string;
  phone: string;
  avatar?: string | null;
  education?: string | null;
  school?: string | null;
  major?: string | null;
  /** 职务 */
  position?: string | null;
  /** 职称 */
  professionalTitle?: string | null;
  certificates?: string | null;
  entryDate?: string | null;
  contractEndDate?: string | null;
  socialInsuranceDate?: string | null;
  /** 在职状态：0-离职/1-在职 */
  employmentStatus?: number | null;
  resignDate?: string | null;
  domicile?: string | null;
  address?: string | null;
  remark?: string | null;
  /** 账号状态：0=禁用, 1=启用（能否登录） */
  status: number;
  lastLoginIp: string;
  lastLoginTime: string;
  createdTime: string;
  updatedTime: string;
}

/** 0-男/1-女/2-其他 */
export const GENDER_MAP: Record<number, string> = {
  0: "男",
  1: "女",
  2: "其他",
};

/** 0-离职/1-在职 */
export const EMPLOYMENT_STATUS_MAP: Record<number, string> = {
  0: "离职",
  1: "在职",
};

// ==================== 角色实体 ====================

export interface SysRole {
  roleId: number;
  roleName: string;
  roleCode: string;
  roleDesc: string;
  status: number;
  createdTime: string;
  updatedTime: string;
}

// ==================== 登录相关 ====================

export interface LoginParams {
  username: string;
  password: string;
}

export interface LoginResponseData {
  accessToken: string;
  refreshToken: string;
  userInfo: SysUser;
  roles: SysRole[];
}

// ==================== 认证状态 ====================

export interface AuthState {
  isAuthenticated: boolean;
  user: SysUser | null;
  roles: SysRole[];
  accessToken: string | null;
  refreshToken: string | null;
}
