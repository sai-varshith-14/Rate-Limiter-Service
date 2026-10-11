package com.platform.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "com.platform.ratelimiter.config")
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
