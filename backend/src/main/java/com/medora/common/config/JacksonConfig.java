package com.medora.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 强制 OffsetDateTime / LocalDateTime 等日期类型序列化为 ISO-8601 字符串。
 *
 * 前端服务页 sort 用 String.localeCompare,如果日期被序列化为 timestamp 数组就会
 * 抛 "localeCompare is not a function"。这里显式禁用 WRITE_DATES_AS_TIMESTAMPS,
 * 避免不同 Spring Boot 版本默认行为差异。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer isoDateCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
