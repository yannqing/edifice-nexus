import type { ProjectFileFolder } from "@/types/project-file";

export function folderAncestors(folders: ProjectFileFolder[], id: string): ProjectFileFolder[] {
  return ancestorsFromMap(new Map(folders.map((folder) => [folder.folderId, folder])), id);
}

function ancestorsFromMap(byId: Map<string, ProjectFileFolder>, id: string): ProjectFileFolder[] {
  const result: ProjectFileFolder[] = [];
  const seen = new Set<string>();
  while (id && id !== "0") {
    const folder = byId.get(id);
    if (!folder || seen.has(id) || result.length >= 20) break;
    result.unshift(folder); seen.add(id); id = folder.parentFolderId;
  }
  return result;
}

export function folderOptions(folders: ProjectFileFolder[]) {
  const byId = new Map(folders.map((folder) => [folder.folderId, folder]));
  return folders.map((folder) => ({ id: folder.folderId,
    path: ancestorsFromMap(byId, folder.folderId).map((parent) => parent.folderName).join("/") }))
    .sort((a, b) => a.path.localeCompare(b.path, "zh-CN"));
}

export function relativeDirectory(path?: string, maxDepth = 20) {
  if (!path) return "";
  const parts = path.split("/");
  if (parts.length > maxDepth + 1 || path.length > 2304 || parts.some((name) => !name.trim() || name.trim() === "." || name.trim() === ".."
      || /[\\\u0000-\u001f\u007f]/.test(name))) throw new Error("文件夹路径无效或超过20层");
  const directories = parts.slice(0, -1);
  if (directories.join("/").length > 2048) throw new Error("文件夹路径不能超过2048个字符");
  if (directories.some((name) => name.trim().length > 128 || /[:*?"<>|]/.test(name))) throw new Error("文件夹名称过长或包含不支持的字符");
  return directories.join("/");
}
