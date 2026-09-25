package com.dcdev.pt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** A {@link Clock} bean rather than {@code Instant.now()} scattered in services, so creation timestamps can be pinned in tests. */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
