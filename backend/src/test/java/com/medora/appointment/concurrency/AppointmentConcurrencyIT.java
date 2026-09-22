package com.medora.appointment.concurrency;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the row-lock quota transition against a real PostgreSQL schema. */
@Testcontainers(disabledWithoutDocker = true)
class AppointmentConcurrencyIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    void oneHundredConcurrentHoldAttemptsCannotOversellTenSlots() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        long slotId = seedSlot();
        List<Long> users = seedUsers(100);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                long userId = users.get(i);
                futures.add(pool.submit(() -> reserveHeldSlot(slotId, userId)));
            }
            for (Future<Boolean> future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        try (Connection connection = connection();
             PreparedStatement slot = connection.prepareStatement("select available_quota, reserved_quota from schedule_slot where id = ?");
             PreparedStatement holds = connection.prepareStatement("select count(*) from appointment_hold where slot_id = ? and status = 'PENDING_CONFIRMATION'")) {
            slot.setLong(1, slotId);
            holds.setLong(1, slotId);
            try (ResultSet row = slot.executeQuery(); ResultSet active = holds.executeQuery()) {
                row.next(); active.next();
                assertThat(row.getInt("available_quota")).isZero();
                assertThat(row.getInt("reserved_quota")).isEqualTo(10);
                assertThat(row.getInt("available_quota") + row.getInt("reserved_quota")).isEqualTo(10);
                assertThat(active.getInt(1)).isEqualTo(10);
            }
        }
    }

    private boolean reserveHeldSlot(long slotId, long userId) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("select available_quota from schedule_slot where id = ? for update")) {
                lock.setLong(1, slotId);
                try (ResultSet row = lock.executeQuery()) {
                    row.next();
                    if (row.getInt(1) == 0) { connection.rollback(); return false; }
                }
            }
            try (PreparedStatement update = connection.prepareStatement("update schedule_slot set available_quota = available_quota - 1, reserved_quota = reserved_quota + 1, status = 'FULL' where id = ?");
                 PreparedStatement hold = connection.prepareStatement("insert into appointment_hold (user_id, slot_id, idempotency_key, status, expires_at) values (?, ?, ?, 'PENDING_CONFIRMATION', now() + interval '2 minutes')")) {
                update.setLong(1, slotId); update.executeUpdate();
                hold.setLong(1, userId); hold.setLong(2, slotId); hold.setString(3, "IT-" + userId); hold.executeUpdate();
            }
            connection.commit();
            return true;
        }
    }

    private long seedSlot() throws Exception {
        try (Connection connection = connection()) {
            long department = returningId(connection, "insert into department(code,name,status) values('IT','并发测试','ACTIVE') returning id");
            long doctor = returningId(connection, "insert into doctor(department_id,code,name,status) values(" + department + ",'IT-D','并发医生','ACTIVE') returning id");
            return returningId(connection, "insert into schedule_slot(department_id,doctor_id,schedule_date,period_code,total_quota,available_quota,reserved_quota,status) values(" + department + "," + doctor + ",current_date,'IT',10,10,0,'OPEN') returning id");
        }
    }

    private List<Long> seedUsers(int count) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = connection()) {
            for (int i = 0; i < count; i++) ids.add(returningId(connection, "insert into user_account(email,password_hash,status,register_source) values('it-" + UUID.randomUUID() + "@example.test','hash','ACTIVE','IT') returning id"));
        }
        return ids;
    }

    private long returningId(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) { row.next(); return row.getLong(1); }
    }

    private Connection connection() throws Exception { return java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); }
}
