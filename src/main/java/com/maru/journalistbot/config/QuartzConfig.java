package com.maru.journalistbot.config;

import com.maru.journalistbot.application.scheduler.quartz.NewsJob;
import com.maru.journalistbot.domain.model.NewsCategory;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz Scheduler configuration — Phase 4.
 *
 * Runs ALONGSIDE the existing @Scheduled jobs (NewsScheduler.java).
 * Quartz handles jobs that require:
 *   1. Persistence — survive restart (stored in PostgreSQL via JdbcJobStore)
 *   2. Distributed lock — only 1 instance runs per trigger in a cluster
 *   3. Misfire handling — catch up missed jobs after downtime
 *
 * Strategy:
 *   - @Scheduled kept for simple, non-critical tasks
 *   - Quartz used for the 3 main news broadcast jobs
 *
 * JobDataMap: passes NewsCategory to NewsJob via Quartz's data map,
 * because Quartz jobs are instantiated by Spring (via SpringBeanJobFactory),
 * so @Autowired works inside NewsJob.
 */
@Configuration
public class QuartzConfig {

    @Value("${bot.schedule.ai-news-interval-ms:1800000}")
    private long aiNewsIntervalMs;

    @Value("${bot.schedule.prog-news-interval-ms:3600000}")
    private long progNewsIntervalMs;

    @Value("${bot.schedule.gamedev-news-interval-ms:7200000}")
    private long gamedevNewsIntervalMs;

    @Value("${bot.schedule.initial-delay-ms:30000}")
    private long initialDelayMs;

    // ── AI News Job ──────────────────────────────────────────────────────────

    @Bean
    public JobDetail aiNewsJobDetail() {
        return buildJobDetail(NewsCategory.AI, "ai-news-job");
    }

    @Bean
    public Trigger aiNewsTrigger(JobDetail aiNewsJobDetail) {
        return buildTrigger(aiNewsJobDetail, "ai-news-trigger", aiNewsIntervalMs);
    }

    // ── Programming News Job ─────────────────────────────────────────────────

    @Bean
    public JobDetail progNewsJobDetail() {
        return buildJobDetail(NewsCategory.PROGRAMMING, "prog-news-job");
    }

    @Bean
    public Trigger progNewsTrigger(JobDetail progNewsJobDetail) {
        return buildTrigger(progNewsJobDetail, "prog-news-trigger", progNewsIntervalMs);
    }

    // ── Game Dev News Job ────────────────────────────────────────────────────

    @Bean
    public JobDetail gamedevNewsJobDetail() {
        return buildJobDetail(NewsCategory.GAME_DEV, "gamedev-news-job");
    }

    @Bean
    public Trigger gamedevNewsTrigger(JobDetail gamedevNewsJobDetail) {
        return buildTrigger(gamedevNewsJobDetail, "gamedev-news-trigger", gamedevNewsIntervalMs);
    }

    // ── Shared builders ──────────────────────────────────────────────────────

    private JobDetail buildJobDetail(NewsCategory category, String identity) {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("category", category.name());  // pass category name as string

        return JobBuilder.newJob(NewsJob.class)
                .withIdentity(identity, "news-jobs")
                .withDescription("Fetch and broadcast " + category.getDisplayName() + " news")
                .usingJobData(dataMap)
                // storeDurably: keep job even when no trigger attached (for manual runs)
                .storeDurably()
                .build();
    }

    private Trigger buildTrigger(JobDetail jobDetail, String triggerIdentity, long intervalMs) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(triggerIdentity, "news-triggers")
                .startAt(DateBuilder.futureDate((int) (initialDelayMs / 1000), DateBuilder.IntervalUnit.SECOND))
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(intervalMs)
                                .repeatForever()
                                // MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT:
                                // If a job was missed (restart/downtime), don't fire all missed
                                // instances — just resume on the next regular interval.
                                .withMisfireHandlingInstructionNextWithRemainingCount()
                )
                .build();
    }
}
