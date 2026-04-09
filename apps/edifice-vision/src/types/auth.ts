// ==================== 用户实体 ====================

export interface SysUser {
  userId: number | string;
  username: string;
  realName: string;
  email: string;
  phone: string;
  status: number; // 0=禁用, 1=启用
  lastLoginIp: string;
  lastLoginTime: string;
  createdTime: string;
  updatedTime: string;
}

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
