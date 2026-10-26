import { get, post, del } from "@/lib/request";
import type { FilesVo } from "@/types/project";

export const MAX_FILE_SIZE = 5 * 1024 * 1024 * 1024;
export const MAX_FILE_SIZE_LABEL = "5GB";
export const MAX_BATCH_FILES = 20;
export type UploadPurpose = "contract" | "project" | "inspection" | "bid" | "import";
export const ATTACHMENT_ACCEPT = ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.csv,.rtf,.odt,.ods,.odp,.jpg,.jpeg,.png,.gif,.bmp,.webp,.svg,.ico,.tiff,.tif,.mp3,.wav,.flac,.aac,.ogg,.wma,.m4a,.opus,.zip,.rar,.7z,.dwg,.dxf,.gbq7,.gdlpb6,.gec5,.gtj,.gjg,.gqi";

interface UploadSession {
  uploadId: string;
  totalSize: number;
  chunkSize: number;
  totalParts: number;
  status: string;
  uploadedParts: number[];
  partHashes: Record<string, string>;
  file: FilesVo | null;
}

class UploadSlots {
  private active = 0;
  private waiters: Array<() => void> = [];
  async run<T>(action: () => Promise<T>): Promise<T> {
    if (this.active >= 3) await new Promise<void>((resolve) => this.waiters.push(resolve));
    else this.active++;
    try { return await action(); }
    finally { const next = this.waiters.shift(); if (next) next(); else this.active--; }
  }
}
const slots = new UploadSlots();
const abortError = () => new DOMException("上传已取消", "AbortError");

async function retry<T>(action: () => Promise<T>, signal?: AbortSignal): Promise<T> {
  let error: unknown;
  for (let attempt = 0; attempt < 3; attempt++) {
    if (signal?.aborted) throw abortError();
    try { return await action(); }
    catch (cause) {
      error = cause;
      if (signal?.aborted || (cause instanceof DOMException && cause.name === "AbortError")) throw cause;
      if (attempt < 2) await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => { signal?.removeEventListener("abort", abort); resolve(); }, 500 * (attempt + 1));
        const abort = () => { clearTimeout(timer); reject(abortError()); };
        signal?.addEventListener("abort", abort, { once: true });
      });
    }
  }
  throw error;
}

function sessionKey(file: File, purpose: UploadPurpose, scope?: string) {
  const directory = file.webkitRelativePath ? `:${encodeURIComponent(file.webkitRelativePath)}` : "";
  return `edifice:upload:v1:${purpose}:${file.name}:${file.size}:${file.lastModified}${directory}${scope ? `:scope:${encodeURIComponent(scope)}` : ""}`;
}
function cached(key: string) { try { return sessionStorage.getItem(key); } catch { return null; } }
function cache(key: string, id?: string) {
  try { if (id) sessionStorage.setItem(key, id); else sessionStorage.removeItem(key); } catch { /* Private mode. */ }
}

async function finishUpload(id: string, totalSize: number, signal?: AbortSignal): Promise<FilesVo> {
  const asynchronous = totalSize > 100 * 1024 * 1024;
  let response = await retry(() => post<UploadSession>(`/file/uploads/${id}/${asynchronous ? "complete-async" : "complete"}`,
    { signal, toastOnBizError: false }), signal);
  for (;;) {
    if (response.code !== 200 || !response.data) throw new Error(response.msg || "文件校验失败");
    if (response.data.status === "completed" && response.data.file) return response.data.file;
    if (!asynchronous || !["verifying", "uploading"].includes(response.data.status)) throw new Error("上传会话已结束，请重新上传");
    await new Promise<void>((resolve, reject) => {
      if (signal?.aborted) return reject(abortError());
      const abort = () => { clearTimeout(timer); reject(abortError()); };
      const timer = setTimeout(() => { signal?.removeEventListener("abort", abort); resolve(); }, 2000);
      signal?.addEventListener("abort", abort, { once: true });
    });
    response = await retry(() => get<UploadSession>(`/file/uploads/${id}`, { signal, toastOnBizError: false }), signal);
    // A process restart loses only the in-memory job; the object and parts remain retryable.
    if (response.code === 200 && response.data?.status === "uploading") {
      response = await post<UploadSession>(`/file/uploads/${id}/complete-async`, { signal, toastOnBizError: false });
    }
  }
}

export async function uploadFile(file: File, purpose: UploadPurpose, options?: {
  signal?: AbortSignal;
  scope?: string;
  onProgress?: (value: number) => void;
  onSession?: (id: string) => void;
}): Promise<FilesVo> {
  if (!file.size || file.size > MAX_FILE_SIZE) throw new Error(`单个文件必须大于0且不能超过${MAX_FILE_SIZE_LABEL}`);
  const signal = options?.signal;
  const key = sessionKey(file, purpose, options?.scope);
  let session: UploadSession | null = null;
  let savedId = cached(key);
  if (!savedId && options?.scope) {
    const legacyKey = sessionKey(file, purpose);
    savedId = cached(legacyKey);
    if (savedId) {
      // Claim old, unscoped resumable uploads once; other project windows get their own sessions.
      cache(key, savedId);
      cache(legacyKey);
    }
  }
  if (savedId) {
    try {
      const response = await get<UploadSession>(`/file/uploads/${savedId}`, { signal, toastOnBizError: false });
      if (response.code === 200 && response.data?.status === "completed" && response.data.file) {
        cache(key); options?.onProgress?.(100); return response.data.file;
      }
      if (response.code === 200 && response.data?.status === "uploading") session = response.data;
      if (response.code === 200 && response.data?.status === "verifying") session = response.data;
    } catch { if (signal?.aborted) throw abortError(); }
  }
  if (session?.status === "verifying") {
    options?.onProgress?.(95);
    const result = await finishUpload(session.uploadId, file.size, signal);
    cache(key); options?.onProgress?.(100); return result;
  }
  if (!session) {
    const response = await post<UploadSession>("/file/uploads", {
      body: { fileName: file.name, totalSize: file.size, purpose }, signal, toastOnBizError: false,
    });
    if (response.code !== 200 || !response.data) throw new Error(response.msg || "无法开始上传");
    session = response.data;
    cache(key, session.uploadId);
  }
  options?.onSession?.(session.uploadId);
  const state = session;
  let done = 0;
  for (let part = 1; part <= state.totalParts; part++) {
    if (signal?.aborted) throw abortError();
    const chunk = file.slice((part - 1) * state.chunkSize, Math.min(part * state.chunkSize, file.size));
    if (state.uploadedParts.includes(part)) {
      const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", await chunk.arrayBuffer())))
        .map((byte) => byte.toString(16).padStart(2, "0")).join("");
      if (hash !== state.partHashes[String(part)]) throw new Error("续传文件内容与原文件不一致，请取消后重新上传");
    } else {
      await slots.run(() => retry(async () => {
        const body = new FormData(); body.append("chunk", chunk, `part-${part}`);
        const response = await post<UploadSession>(`/file/uploads/${state.uploadId}/parts/${part}`, { body, signal, toastOnBizError: false });
        if (response.code !== 200) throw new Error(response.msg || "分片上传失败");
      }, signal));
    }
    done += chunk.size;
    options?.onProgress?.(Math.min(95, Math.round(done / file.size * 95)));
  }
  const result = await finishUpload(state.uploadId, file.size, signal);
  cache(key); options?.onProgress?.(100);
  return result;
}

export async function cancelUpload(id: string) {
  const response = await del(`/file/uploads/${id}`, { toastOnBizError: false });
  if (response.code !== 200) throw new Error(response.msg || "取消失败");
}
