import type { FilesVo } from "@/types/project";
import type { BaseResponse } from "@/types/api";
import { getAccessToken } from "@/lib/token";
import { getApiBaseUrl } from "@/lib/request";

const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
const MAX_CONCURRENT = 3;
const SMALL_FILE_LIMIT = 10 * 1024 * 1024; // 10MB

function generateUploadId(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function isSmallFile(file: File): boolean {
  return file.size <= SMALL_FILE_LIMIT;
}

export interface ChunkUploadOptions {
  onProgress?: (percent: number) => void;
  fileType?: string;
}

/**
 * 分片上传大文件
 * 1. 初始化上传会话
 * 2. 并发上传分片
 * 3. 合并分片
 */
export async function uploadFileInChunks(
  file: File,
  options?: ChunkUploadOptions
): Promise<BaseResponse<FilesVo>> {
  const uploadId = generateUploadId();
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
  const fileType = options?.fileType ?? "document";
  const base = getApiBaseUrl();
  const token = getAccessToken();

  // 1. 初始化
  const initRes = await fetch(`${base}/file/chunk/init`, {
    method: "POST",
    headers: { token: token ?? "" },
    body: (() => {
      const fd = new FormData();
      fd.append("uploadId", uploadId);
      fd.append("fileName", file.name);
      fd.append("totalChunks", String(totalChunks));
      fd.append("totalSize", String(file.size));
      fd.append("fileType", fileType);
      return fd;
    })(),
  });
  const initData = await initRes.json();
  if (initData.code !== 200) {
    return initData;
  }

  // 2. 并发上传分片
  let uploadedChunks = 0;
  const chunkIndices = Array.from({ length: totalChunks }, (_, i) => i);

  async function uploadOneChunk(index: number): Promise<void> {
    const start = index * CHUNK_SIZE;
    const end = Math.min(start + CHUNK_SIZE, file.size);
    const blob = file.slice(start, end);

    const fd = new FormData();
    fd.append("uploadId", uploadId);
    fd.append("chunkIndex", String(index));
    fd.append("chunk", blob, `chunk-${index}`);

    const res = await fetch(`${base}/file/chunk/upload`, {
      method: "POST",
      headers: { token: token ?? "" },
      body: fd,
    });
    const data = await res.json();
    if (data.code !== 200) {
      throw new Error(data.msg || `分片 ${index} 上传失败`);
    }

    uploadedChunks++;
    options?.onProgress?.(Math.round((uploadedChunks / totalChunks) * 100));
  }

  // 并发控制
  const queue = [...chunkIndices];
  const workers: Promise<void>[] = [];

  for (let i = 0; i < Math.min(MAX_CONCURRENT, totalChunks); i++) {
    workers.push(
      (async () => {
        while (queue.length > 0) {
          const idx = queue.shift();
          if (idx !== undefined) await uploadOneChunk(idx);
        }
      })()
    );
  }

  await Promise.all(workers);

  // 3. 合并
  const mergeFd = new FormData();
  mergeFd.append("uploadId", uploadId);
  mergeFd.append("fileName", file.name);
  mergeFd.append("fileType", fileType);

  const mergeRes = await fetch(`${base}/file/chunk/merge`, {
    method: "POST",
    headers: { token: token ?? "" },
    body: mergeFd,
  });

  return mergeRes.json();
}
