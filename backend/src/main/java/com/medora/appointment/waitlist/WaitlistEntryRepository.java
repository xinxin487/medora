package com.medora.appointment.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntryEntity, Long> {
    List<WaitlistEntryEntity> findBySlotIdAndStatusOrderByPriorityDescJoinedAtAsc(Long slotId, String status);
    List<WaitlistEntryEntity> findByStatusOrderByPriorityDescJoinedAtAsc(String status);
    java.util.Optional<WaitlistEntryEntity> findByPublicId(java.util.UUID publicId);
    java.util.Optional<WaitlistEntryEntity> findByOfferedHoldId(Long offeredHoldId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitlistEntryEntity w where w.id = :id")
    java.util.Optional<WaitlistEntryEntity> findByIdForUpdate(@Param("id") Long id);
}
