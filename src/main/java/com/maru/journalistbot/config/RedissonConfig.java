package com.maru.journalistbot.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson client configuration.
 *
 * Redisson provides advanced Redis features on top of spring-data-redis:
 *   - RLock       — distributed lock (used by Quartz jobs)
 *   - RRateLimiter — token bucket rate limiting (NewsAPI, Reddit)
 *   - RMap, RQueue — additional distributed data structures
 *
 * We keep the existing RedisConfig (RedisTemplate<String,String>) for
 * simple dedup key/value ops, and add Redisson for the advanced features.
 * Both can coexist — they connect to the same Redis instance.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                // Connection pool settings — balanced for bot workload
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
