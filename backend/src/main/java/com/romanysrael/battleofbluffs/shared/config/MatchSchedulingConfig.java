package com.romanysrael.battleofbluffs.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class MatchSchedulingConfig {
    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler matchTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("gotg-match-lifecycle-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
