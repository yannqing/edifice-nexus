"use client";

import { useCallback, useEffect, useState } from "react";
import { getProjectTree, getProjectTreeStatistics } from "@/services/project";
import { isAbortError } from "@/lib/request";
import { ResponseCode } from "@/types/api";
import type {
  PageResult,
  ProjectTreeNode,
  ProjectTreeQuery,
  ProjectTreeScope,
  ProjectTreeStatistics,
} from "@/types/project";

export function useProjectTree(
  scope: ProjectTreeScope,
  query: ProjectTreeQuery,
) {
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{
    key: string;
    data?: PageResult<ProjectTreeNode>;
    error?: string;
  } | null>(null);
  const [statistics, setStatistics] = useState<{
    key: string;
    data?: ProjectTreeStatistics;
  } | null>(null);
  const queryKey = JSON.stringify(query);
  const key = `${scope}:${queryKey}:${revision}`;
  // The server separates status facets from the fully filtered result summary.
  const statsQuery = JSON.stringify({
    keywords: query.keywords,
    projectType: query.projectType,
    projectLevel: query.projectLevel,
    projectStatus: query.projectStatus,
    archiveStatus: query.archiveStatus,
  });
  const statsKey = `${scope}:${statsQuery}:${revision}`;
  useEffect(() => {
    const controller = new AbortController();
    getProjectTree(scope, JSON.parse(queryKey), controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setResult(
          response.code === ResponseCode.SUCCESS && response.data
            ? { key, data: response.data }
            : { key, error: response.msg || "项目加载失败" },
        );
      })
      .catch((error) => {
        if (!controller.signal.aborted && !isAbortError(error))
          setResult({ key, error: "项目加载失败，请重试" });
      });
    return () => controller.abort();
  }, [scope, queryKey, key]);
  useEffect(() => {
    const controller = new AbortController();
    getProjectTreeStatistics(scope, JSON.parse(statsQuery), controller.signal)
      .then((response) => {
        if (!controller.signal.aborted)
          setStatistics({
            key: statsKey,
            data:
              response.code === ResponseCode.SUCCESS
                ? response.data
                : undefined,
          });
      })
      .catch(() => {
        if (!controller.signal.aborted) setStatistics({ key: statsKey });
      });
    return () => controller.abort();
  }, [scope, statsQuery, statsKey]);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);
  return {
    nodes: result?.key === key ? (result.data?.records ?? []) : [],
    total: result?.key === key ? (result.data?.total ?? 0) : 0,
    loading: result?.key !== key,
    error: result?.key === key ? result.error : undefined,
    statistics: statistics?.key === statsKey ? statistics.data : undefined,
    revision,
    refresh,
    queryKey,
  };
}
