package com.medora.appointment.hold;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentHoldMappingTest {

    @Test
    void mapsSnapshotJsonToTheJsonbColumnCreatedByFlyway() throws Exception {
        JdbcTypeCode jdbcType = AppointmentHoldEntity.class
                .getDeclaredField("snapshotJson")
                .getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcType).isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.JSON);
    }
}
