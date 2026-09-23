package com.medora.memory.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMemoryProfileMappingTest {

    @Test
    void mapsFingerprintToTheFixedWidthSha256ColumnCreatedByFlyway() throws Exception {
        Column column = UserMemoryProfileEntity.class
                .getDeclaredField("fingerprint")
                .getAnnotation(Column.class);
        JdbcTypeCode jdbcType = UserMemoryProfileEntity.class
                .getDeclaredField("fingerprint")
                .getAnnotation(JdbcTypeCode.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("char(64)");
        assertThat(column.length()).isEqualTo(64);
        assertThat(jdbcType).isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.CHAR);
    }
}
