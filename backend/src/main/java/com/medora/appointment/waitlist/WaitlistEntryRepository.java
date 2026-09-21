package com.medora.appointment.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntryEntity, Long> {
    List<WaitlistEntryEntity> findBySlotIdAndStatusOrderByPriorityDescJoinedAtAsc(Long slotId, String status);
}
