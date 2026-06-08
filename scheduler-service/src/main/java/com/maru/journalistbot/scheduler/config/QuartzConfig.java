package com.maru.journalistbot.scheduler.config;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.scheduler.job.NewsSchedulerJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 6 — Quartz config chuyển từ fetcher-service sang scheduler-service.
 * Job state persist vào postgres-scheduler (PostgreSQL instance riêng).
 * isClustered=true → an toàn khi chạy nhiều scheduler-service pod (chỉ 1 pod trigger).
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

    // ── JobDetail beans ─────────────────────────────────────────────────────
    @Bean public JobDetail aiNewsJobDetail()      { return buildJobDetail(NewsCategory.AI, "ai-scheduler-job"); }
    @Bean public JobDetail progNewsJobDetail()    { return buildJobDetail(NewsCategory.PROGRAMMING, "prog-scheduler-job"); }
    @Bean public JobDetail gamedevNewsJobDetail() { return buildJobDetail(NewsCategory.GAME_DEV, "gamedev-scheduler-job"); }

    // ── Trigger beans ────────────────────────────────────────────────────────
    @Bean public Trigger aiNewsTrigger(JobDetail aiNewsJobDetail)          { return buildTrigger(aiNewsJobDetail, "ai-scheduler-trigger", aiNewsIntervalMs); }
    @Bean public Trigger progNewsTrigger(JobDetail progNewsJobDetail)      { return buildTrigger(progNewsJobDetail, "prog-scheduler-trigger", progNewsIntervalMs); }
    @Bean public Trigger gamedevNewsTrigger(JobDetail gamedevNewsJobDetail) { return buildTrigger(gamedevNewsJobDetail, "gamedev-scheduler-trigger", gamedevNewsIntervalMs); }

    private JobDetail buildJobDetail(NewsCategory category, String identity) {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("category", category.name());
        return JobBuilder.newJob(NewsSchedulerJob.class)
                .withIdentity(identity, "scheduler-jobs")
                .withDescription("Trigger fetch for " + category.name())
                .usingJobData(dataMap)
                .storeDurably()
                .build();
    }

    private Trigger buildTrigger(JobDetail jobDetail, String triggerIdentity, long intervalMs) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(triggerIdentity, "scheduler-triggers")
                .startAt(DateBuilder.futureDate((int)(initialDelayMs / 1000), DateBuilder.IntervalUnit.SECOND))
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(intervalMs)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount()
                )
                .build();
    }
}
