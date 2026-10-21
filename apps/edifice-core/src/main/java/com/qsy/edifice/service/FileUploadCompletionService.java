package com.qsy.edifice.service;

import com.qsy.edifice.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

/** Long read-back verification must not hold a Cloudflare request open. */
@Slf4j
@Service
public class FileUploadCompletionService {
    @Resource private UnifiedFileUploadService uploads;
    private record Job(FutureTask<UnifiedFileUploadService.SessionVo> task, Instant created) {}
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "file-verification"); thread.setDaemon(true); return thread;
    });

    public UnifiedFileUploadService.SessionVo start(String id, Long userId) {
        var state = uploads.status(id, userId);
        if ("completed".equals(state.status())) return state;
        if (!"uploading".equals(state.status()) || state.uploadedParts().size() != state.totalParts())
            throw new BusinessException("分片未全部上传或会话已结束，无法校验");
        synchronized (jobs) {
            if (!jobs.containsKey(id)) {
                // One verifier and at most two waiting jobs keep a 4GB host responsive.
                if (jobs.size() >= 3) throw new BusinessException("文件校验通道繁忙，请稍后重试");
                var task = new FutureTask<>(() -> uploads.complete(id, userId));
                jobs.put(id, new Job(task, Instant.now()));
                try { worker.execute(task); }
                catch (RejectedExecutionException error) { jobs.remove(id); throw new BusinessException("文件服务正在重启，请稍后重试"); }
            }
        }
        return status(id, userId);
    }

    public UnifiedFileUploadService.SessionVo status(String id, Long userId) {
        var state = uploads.status(id, userId); // Always enforce session ownership, including cached jobs.
        Job job = jobs.get(id);
        if (job == null) return state;
        if (!job.task().isDone()) return new UnifiedFileUploadService.SessionVo(state.uploadId(), state.totalSize(),
                state.chunkSize(), state.totalParts(), "verifying", state.uploadedParts(), state.partHashes(), state.file());
        try {
            var result = job.task().get(); jobs.remove(id, job); return result;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new BusinessException("校验查询中断，请重试");
        } catch (ExecutionException error) {
            jobs.remove(id, job);
            log.warn("File verification failed uploadId={}", id, error.getCause());
            if (error.getCause() instanceof BusinessException business) throw business;
            throw new BusinessException("文件校验失败，请重试或联系管理员");
        }
    }

    public void cancel(String id, Long userId) {
        uploads.status(id, userId);
        synchronized (jobs) {
            Job job = jobs.get(id);
            if (job != null && !job.task().isDone()) throw new BusinessException("文件正在校验，请等待校验完成");
            uploads.cancel(id, userId);
            jobs.remove(id);
        }
    }

    @Scheduled(fixedDelay = 600000)
    public void discardOldResults() {
        jobs.entrySet().removeIf(entry -> entry.getValue().task().isDone()
                && entry.getValue().created().isBefore(Instant.now().minusSeconds(600)));
    }

    @PreDestroy public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(30, TimeUnit.SECONDS)) worker.shutdownNow(); }
        catch (InterruptedException error) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
