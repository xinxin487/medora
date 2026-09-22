package com.medora.appointment.hold;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;

public interface AppointmentHoldRepository extends JpaRepository<AppointmentHoldEntity, Long> {
    Optional<AppointmentHoldEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AppointmentHoldEntity h where h.id = :id")
    Optional<AppointmentHoldEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from AppointmentHoldEntity h
            where h.status = 'PENDING_CONFIRMATION' and h.expiresAt <= :now
            order by h.expiresAt asc
            """)
    List<AppointmentHoldEntity> findPendingExpiredForUpdate(@Param("now") OffsetDateTime now);

    long countBySlotIdAndStatus(Long slotId, String status);
}
