package com.maru.journalistbot.notification.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service for notification-service.
 * Chống duplicate broadcast khi multi-instance (Scale out → nhiều pod).
 *
 * Prefix: "notif:lock:<name>"
 * Strategy: tryLock với waitTime=0 (không chờ), leaseTime=60s (tự giải phóng nếu crash)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String PREFIX      = "notif:lock:";
    private static final long   LEASE_TIME  = 60L;  // seconds
    private static final long   WAIT_TIME   = 0L;   // try once, don't wait

    /**
     * Execute a task only if this instance acquires the lock.
     * If another instance holds the lock → skip (return null).
     *
     * @param lockName unique name for this lock (e.g. "broadcast:news.summarized:AI")
     * @param task     the work to execute under the lock
     * @param <T>      return type of the task
     * @return task result, or null if lock was not acquired
     */
    public <T> T executeWithLock(String lockName, Supplier<T> task) {
        RLock lock = redissonClient.getLock(PREFIX + lockName);
        try {
            boolean acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[LOCK] Could not acquire lock '{}' — another instance is processing", lockName);
                return null;
            }
            log.debug("[LOCK] Acquired lock '{}'", lockName);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LOCK] Interrupted while acquiring lock '{}'", lockName);
            return null;
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("[LOCK] Released lock '{}'", lockName);
                }
            } catch (Exception e) {
                log.warn("[LOCK] Failed to release lock '{}': {}", lockName, e.getMessage());
            }
        }
    }

    /**
     * Void version — execute a task without returning a value.
     */
    public void executeWithLock(String lockName, Runnable task) {
        executeWithLock(lockName, () -> {
            task.run();
            return null;
        });
    }
}
