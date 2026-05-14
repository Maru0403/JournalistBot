package com.maru.journalistbot.application.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service using Redisson RLock.
 *
 * Purpose: prevent duplicate job execution across multiple bot instances.
 * When multiple pods are running (K8s scale-out), only ONE should run
 * each scheduled news fetch at a time. Without this, all pods broadcast
 * simultaneously → users receive duplicate messages.
 *
 * Pattern: try-lock with TTL (auto-release if process crashes).
 *   - waitTime  = 0  → don't queue; if locked, skip this cycle
 *   - leaseTime = job TTL → auto-release even if job hangs / pod crashes
 *
 * SRP: only responsible for lock acquire/release lifecycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "journalist-bot:lock:";

    /**
     * Execute {@code task} only if the distributed lock is acquired.
     * If another instance already holds the lock, skip silently.
     *
     * @param lockName  unique name for this job (e.g. "news-job:AI")
     * @param leaseSec  max seconds the lock is held (auto-released after crash)
     * @param task      the work to execute under the lock
     */
    public void executeWithLock(String lockName, long leaseSec, Runnable task) {
        String key = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(key);

        boolean acquired = false;
        try {
            // waitTime=0: don't block — if locked, another instance is already running
            acquired = lock.tryLock(0, leaseSec, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[LOCK] Skipping '{}' — another instance holds the lock", lockName);
                return;
            }
            log.debug("[LOCK] Acquired '{}' — executing job", lockName);
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LOCK] Interrupted while acquiring lock '{}'", lockName);
        } catch (Exception e) {
            log.error("[LOCK] Job '{}' failed: {}", lockName, e.getMessage(), e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[LOCK] Released '{}'", lockName);
            }
        }
    }

    /**
     * Execute {@code task} with return value under the distributed lock.
     * Returns null if lock is not acquired.
     *
     * @param lockName  unique name for this job
     * @param leaseSec  max seconds the lock is held
     * @param task      the supplier to execute under the lock
     * @return result of task, or null if lock was not acquired
     */
    public <T> T executeWithLock(String lockName, long leaseSec, Supplier<T> task) {
        String key = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(key);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, leaseSec, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[LOCK] Skipping '{}' — another instance holds the lock", lockName);
                return null;
            }
            log.debug("[LOCK] Acquired '{}' — executing task", lockName);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LOCK] Interrupted while acquiring lock '{}'", lockName);
            return null;
        } catch (Exception e) {
            log.error("[LOCK] Task '{}' failed: {}", lockName, e.getMessage(), e);
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[LOCK] Released '{}'", lockName);
            }
        }
    }
}
