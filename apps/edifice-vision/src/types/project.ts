// ==================== 项目类型 VO ====================

export interface ProjectTypeVo {
  projectTypeId: string;
  projectTypeName: string;
  projectTypeCode: string;
  projectTypeStatus: number;
}

// ==================== 项目阶段 VO ====================

export interface ProjectStageVo {
  projectStageId: string;
  projectId: string;
  stageName: string;
  /** 0-未开始/1-进行中/2-待验收/3-已验收/4-已驳回/5-待分配/6-已完成 */
  stageStatus: number;
  /** 基本部分累计计入比例（%，0-100） */
  stageOutput: number;
  /** 效益部分累计计入比例（%，0-100），v0.4 新增 */
  benefitInclusionRatio?: number;
}

// ==================== 合同 VO ====================

export interface ContractVo {
  contractId: string;
  contractName: string;
  contractCode: string;
  /** 0-基本收费/1-基本+效益 */
  contractType: number;
  contractAmount: number;
  contractFile: string;
  contractOtherFiles: string;
  baseAmount: number;
  benefitRules: string;
  /** 当前预计效益金额（v0.4 新增，contract_type=1 才有意义） */
  benefitAmount?: number;
  /** 效益状态：0-预计中/1-已最终确认（v0.4 新增） */
  benefitStatus?: number;
  signingDate: string;
  preStartDate: string;
  preEndDate: string;
}

// ==================== 项目成员 VO ====================

export interface ProjectMemberVo {
  projectMemberId: string;
  projectId: string;
  userId: string;
  projectRoleId: string;
  realName: string;
}

// ==================== 项目列表 VO ====================

export interface ProjectListVo {
  projectId: string;
  projectName: string;
  projectCode: string;
  projectType: ProjectTypeVo | null;
  /** 0-未开始/1-进行中/2-待验收/3-验收中/4-已结束 */
  projectStatus: number;
  projectStage: ProjectStageVo | null;
  projectStages: ProjectStageVo[] | null;
  contractAmount: ContractVo | null;
  projectMemberList: ProjectMemberVo[] | null;
  preStartTime: string | null;
  preEndTime: string | null;
  /** 项目文件数量（project_files） */
  fileCount: number;
  /** 归档状态：0-未归档/1-已归档 */
  archiveStatus?: number;
}

// ==================== 项目详情 VO ====================

export interface ProjectDetailVo {
  projectId: string;
  projectName: string;
  projectCode: string;
  projectType: ProjectTypeVo | null;
  /** 0-未开始/1-进行中/2-待验收/3-验收中/4-已结束 */
  projectStatus: number;
  projectStage: ProjectStageVo | null;
  projectStages: ProjectStageVo[] | null;
  contract: ContractVo | null;
  projectMemberList: ProjectMemberVo[] | null;
  preStartTime: string | null;
  preEndTime: string | null;
}

// ==================== 项目统计 VO ====================

export interface ProjectStatisticsVo {
  totalCount: number;
  notStartedCount: number;
  processingCount: number;
  pendingAcceptanceCount: number;
  completedCount: number;
  archivedCount?: number;
  totalContractAmount: number;
}

// ==================== 查询参数 ====================

export interface GetMyProjectListParams {
  keywords?: string;
  projectStatus?: number;
  current?: number;
  pageSize?: number;
}

export interface GetAllProjectListParams {
  keywords?: string;
  projectStatus?: number;
  projectType?: number;
  current?: number;
  pageSize?: number;
}

// ==================== 分页响应 ====================

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ==================== 更新项目参数 ====================

export interface UpdateProjectParams {
  projectId: string;
  projectName?: string;
  projectCode?: string;
  projectType?: number;
  projectStatus?: number;
  contractType?: number;
  contractAmount?: number;
  baseAmount?: number;
  benefitRule?: string;
  /** 预计效益金额（v0.4，仅 contractType=1） */
  benefitAmount?: number;
  preStartTime?: string;
  preEndTime?: string;
  projectCharges?: string[];
  projectMembers?: string[];
}

// ==================== 新建项目参数 ====================

export interface CreateProjectParams {
  projectName: string;
  projectCode?: string;
  projectType: number;
  contractType: number;
  contractAmount: number;
  baseAmount?: number;
  benefitRule?: string;
  /** 预计效益金额（v0.4，仅 contractType=1） */
  benefitAmount?: number;
  contractFile?: string;
  contractOtherFiles?: string[];
  projectCharges: string[];
  projectMembers?: string[];
  signingTime?: string;
  preStartTime?: string;
  preEndTime?: string;
}

// ==================== 文件上传响应 ====================

export interface FilesVo {
  fileId: string;
  fileType: string;
  displayName: string;
  fileExtension: string;
  fileUrl: string;
  fileSize: string;
  status: number;
}

// ==================== 项目阶段模板 ====================

export interface ProjectStageTemplate {
  stageId: string;
  stageName: string;
  stageOutput: number;
  stageStatus: number;
}

// ==================== 用户列表项 ====================

export interface UserListItem {
  userId: string;
  username: string;
  realName: string;
  email: string;
  phone: string;
  departmentId?: string | null;
  departmentName?: string | null;
  position?: string | null;
  positionName?: string | null;
  status: number;
  employmentStatus?: number | null;
}

// ==================== 状态映射工具 ====================

/** 后端项目状态数字 → 前端中文 */
export const PROJECT_STATUS_MAP: Record<number, string> = {
  0: "未开始",
  1: "进行中",
  2: "待验收",
  3: "验收中",
  4: "已完成",
};

/** 阶段状态：已完成的状态值集合 */
export const STAGE_COMPLETED_STATUSES = [3, 6]; // 已验收、已完成
