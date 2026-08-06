package com.medora.appointment.repository;

import com.medora.appointment.domain.AppointmentOperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentOperationLogRepository extends JpaRepository<AppointmentOperationLogEntity, Long> {
}
