package com.maru.journalistbot.fetcher.config;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.scheduler.NewsJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz config — giữ nguyên từ Phase 4, chỉ đổi package.
 * Job state persist vào PostgreSQL (QRTZ_* tables) → survive restart.
 * isClustered=true → an toàn khi scale nhiều fetcher-service pod.
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

    @Bean public JobDetail aiNewsJobDetail()       { return buildJobDetail(NewsCategory.AI, "ai-news-job"); }
    @Bean public JobDetail progNewsJobDetail()     { return buildJobDetail(NewsCategory.PROGRAMMING, "prog-news-job"); }
    @Bean public JobDetail gamedevNewsJobDetail()  { return buildJobDetail(NewsCategory.GAME_DEV, "gamedev-news-job"); }

    @Bean public Trigger aiNewsTrigger(JobDetail aiNewsJobDetail)          { return buildTrigger(aiNewsJobDetail, "ai-news-trigger", aiNewsIntervalMs); }
    @Bean public Trigger progNewsTrigger(JobDetail progNewsJobDetail)      { return buildTrigger(progNewsJobDetail, "prog-news-trigger", progNewsIntervalMs); }
    @Bean public Trigger gamedevNewsTrigger(JobDetail gamedevNewsJobDetail) { return buildTrigger(gamedevNewsJobDetail, "gamedev-news-trigger", gamedevNewsIntervalMs); }

    private JobDetail buildJobDetail(NewsCategory category, String identity) {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("category", category.name());
        return JobBuilder.newJob(NewsJob.class)
                .withIdentity(identity, "fetch-jobs")
                .withDescription("Fetch & publish " + category.getDisplayName())
                .usingJobData(dataMap)
                .storeDurably()
                .build();
    }

    private Trigger buildTrigger(JobDetail jobDetail, String triggerIdentity, long intervalMs) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(triggerIdentity, "fetch-triggers")
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
