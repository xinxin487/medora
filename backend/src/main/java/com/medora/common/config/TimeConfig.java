package com.medora.common.config;

import com.medora.ai.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 统一时区 bean — 所有业务代码注入此 Clock 取当前时间,避免裸用 LocalDate.now() 依赖
 * JVM 默认时区(容器里常是 UTC,导致北京时间 20:00 后跨日)。
 *
 * 时区由 {@code medora.ai.zone-id} 配置,默认 Asia/Shanghai。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock medoraClock(AiProperties aiProperties) {
        return Clock.system(ZoneId.of(aiProperties.getZoneId()));
    }
}
