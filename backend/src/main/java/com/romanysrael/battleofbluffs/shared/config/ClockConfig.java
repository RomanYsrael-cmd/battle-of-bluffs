package com.romanysrael.battleofbluffs.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
