import { get } from "@/lib/request";
import type { BaseResponse } from "@/types/api";

export interface DepartmentTreeItem {
  departmentId: string;
  oaDepartmentId: number | null;
  parentId: string;
  name: string;
  sort: number | null;
  status: number;
  children: DepartmentTreeItem[];
}

export async function getDepartmentTree(): Promise<BaseResponse<DepartmentTreeItem[]>> {
  return get<DepartmentTreeItem[]>("/org/departments/tree");
}
