import { get } from "@/lib/request";
import type { BaseResponse } from "@/types/api";
import type {
  GetLifecycleProjectListParams,
  LifecycleProjectPageResult,
  ProjectLifecycleVo,
} from "@/types/project-lifecycle";

export function getLifecycleProjects(
  params?: GetLifecycleProjectListParams,
  signal?: AbortSignal
): Promise<BaseResponse<LifecycleProjectPageResult>> {
  const query: Record<string, string> = {};
  if (params?.keywords) query.keywords = params.keywords;
  if (params?.projectStatus !== undefined) query.projectStatus = String(params.projectStatus);
  if (params?.projectType !== undefined) query.projectType = String(params.projectType);
  if (params?.current !== undefined) query.current = String(params.current);
  if (params?.pageSize !== undefined) query.pageSize = String(params.pageSize);
  return get<LifecycleProjectPageResult>("/project/lifecycle/list", { params: query, signal });
}

export function getProjectLifecycle(
  projectId: string,
  signal?: AbortSignal
): Promise<BaseResponse<ProjectLifecycleVo>> {
  return get<ProjectLifecycleVo>(`/project/lifecycle/${projectId}`, { signal });
}
