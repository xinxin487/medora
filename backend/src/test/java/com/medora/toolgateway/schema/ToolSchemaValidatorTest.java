package com.medora.toolgateway.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolSchemaValidatorTest {
    @Test void marksTheRuntimeConstructorForSpringInjection() throws Exception {
        assertThat(ToolSchemaValidator.class.getConstructor(ObjectMapper.class).getAnnotation(Autowired.class))
                .isNotNull();
    }

    @Test void rejectsUnknownInvalidUuidAndOversizedPageSize() throws Exception {
        ToolSchemaValidator validator = ToolSchemaValidator.fromClasspath(new ObjectMapper(), "query-schedule-slots-input");
        assertThat(validator.validate(Map.of("scheduleDate", "2026-01-01", "pageSize", 5))).isEmpty();
        assertThat(validator.validate(Map.of("scheduleDate", "2026-01-01", "surprise", true))).isNotEmpty();
        assertThat(validator.validate(Map.of("scheduleDate", "2026-01-01", "departmentId", "not-a-uuid"))).isNotEmpty();
        assertThat(validator.validate(Map.of("scheduleDate", "2026-01-01", "pageSize", 99))).isNotEmpty();
    }
}
