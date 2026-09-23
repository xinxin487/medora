package com.medora.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class StartupLoggingSecurityTest {

    @Test
    void defaultLoggingDoesNotEnableFrameworkRequestBodiesOrSqlDebugOutput() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/application.yml")) {
            assertThat(stream).isNotNull();
            JsonNode levels = new ObjectMapper(new YAMLFactory()).readTree(stream)
                    .path("logging").path("level");

            assertThat(levels.path("org.springframework.web").asText()).isEqualTo("INFO");
            assertThat(levels.path("org.hibernate.SQL").asText()).isEqualTo("INFO");
            assertThat(levels.path("org.hibernate.orm.jdbc.bind").asText()).isEqualTo("INFO");
        }
    }
}
