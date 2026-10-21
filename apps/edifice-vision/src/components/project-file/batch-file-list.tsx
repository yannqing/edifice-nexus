"use client";

import { AttachmentFileActions } from "@/components/file/attachment-file-list";
import type { ProjectFileVo } from "@/types/project-file";

export function BatchFileList({ file }: { file: ProjectFileVo }) {
  if (!file.batchHeader) return null;
  return <section aria-label="本批文件清单">
    <h3 className="mb-2 text-sm font-medium text-slate-700">本批文件（{file.batchFileCount ?? file.batchFiles?.length ?? 0} 份）</h3>
    <div className="max-h-52 space-y-2 overflow-y-auto overscroll-contain">
      {file.batchFiles?.map((item) => <div key={item.projectFileId}>
        <AttachmentFileActions fileId={item.fileId} fileName={item.fileName} fileSize={item.fileSize ?? undefined} />
        <p className="px-3 pt-1 text-xs text-slate-500">{item.fileCategory || "未分类"} · {item.stageName || "不关联阶段"}{item.description ? ` · ${item.description}` : ""}</p>
        <p className="break-all px-3 pt-1 text-xs text-slate-500">目录：{item.folderPath || "根目录"}</p>
      </div>)}
    </div>
  </section>;
}
