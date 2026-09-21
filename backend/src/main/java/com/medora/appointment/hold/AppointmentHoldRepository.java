package com.medora.appointment.hold;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentHoldRepository extends JpaRepository<AppointmentHoldEntity, Long> {
    Optional<AppointmentHoldEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
