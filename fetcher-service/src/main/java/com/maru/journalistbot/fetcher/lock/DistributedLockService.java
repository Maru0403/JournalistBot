package com.maru.journalistbot.fetcher.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed lock via Redisson — đảm bảo chỉ 1 fetcher-service pod chạy mỗi job tại một thời điểm.
 * Khi scale K8s HPA lên nhiều pod: lock ngăn broadcast trùng lặp.
 *
 * Pattern: tryLock với waitTime=0 — nếu lock đang bị giữ, skip cycle này (không queue).
 * leaseTime = TTL tự động release nếu pod crash giữa chừng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "fetcher:lock:";

    public void executeWithLock(String lockName, long leaseSec, Runnable task) {
        String key = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, leaseSec, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[LOCK] Skipping '{}' — another instance holds the lock", lockName);
                return;
            }
            log.debug("[LOCK] Acquired '{}' — running job", lockName);
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LOCK] Interrupted acquiring '{}'", lockName);
        } catch (Exception e) {
            log.error("[LOCK] Job '{}' failed: {}", lockName, e.getMessage(), e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[LOCK] Released '{}'", lockName);
            }
        }
    }
}
