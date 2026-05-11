package com.medora.appointment.repository;

import com.medora.appointment.domain.AppointmentEntity;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, Long> {

    Optional<AppointmentEntity> findByUserAccountAndIdempotencyKey(UserAccountEntity userAccount, String idempotencyKey);

    boolean existsByUserAccountAndSlotAndStatus(UserAccountEntity userAccount, ScheduleSlotEntity slot, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from AppointmentEntity a
            join fetch a.userAccount
            join fetch a.slot
            join fetch a.department
            join fetch a.doctor
            where a.publicId = :publicId
            """)
    Optional<AppointmentEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query("""
            select a
            from AppointmentEntity a
            join fetch a.slot
            join fetch a.department
            join fetch a.doctor
            where a.publicId = :publicId
              and a.userAccount = :userAccount
            """)
    Optional<AppointmentEntity> findDetail(@Param("publicId") UUID publicId,
                                           @Param("userAccount") UserAccountEntity userAccount);

    @Query("""
            select a
            from AppointmentEntity a
            where a.userAccount = :userAccount
              and (:status is null or a.status = :status)
              and (:scheduleDateFrom is null or a.scheduleDate >= :scheduleDateFrom)
              and (:scheduleDateTo is null or a.scheduleDate <= :scheduleDateTo)
            order by a.createdAt desc
            """)
    Page<AppointmentEntity> searchMine(@Param("userAccount") UserAccountEntity userAccount,
                                       @Param("status") String status,
                                       @Param("scheduleDateFrom") LocalDate scheduleDateFrom,
                                       @Param("scheduleDateTo") LocalDate scheduleDateTo,
                                       Pageable pageable);
}
