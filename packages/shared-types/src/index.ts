// Shared type definitions
// Add your shared types here

export interface User {
  userId: number;
  userName: string;
  nickName: string;
  email: string;
  phone: string;
  sex: string;
  avatar: string;
  status: string;
  createTime: Date;
  updateTime: Date;
}

export interface Role {
  roleId: number;
  roleName: string;
  roleKey: string;
  status: string;
  createTime: Date;
  updateTime: Date;
}

export interface Permission {
  permissionId: number;
  permissionName: string;
  permissionKey: string;
  menuType: string;
  parentId: number;
  path: string;
  icon: string;
  orderNum: number;
  status: string;
  createTime: Date;
  updateTime: Date;
}
