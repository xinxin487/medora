package com.medora.masterdata.repository;

import com.medora.masterdata.domain.ScheduleSlotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlotEntity, Long> {

    @Query("""
            select s
            from ScheduleSlotEntity s
            where s.scheduleDate = :scheduleDate
              and (:departmentPublicId is null or s.department.publicId = :departmentPublicId)
              and (:doctorPublicId is null or s.doctor.publicId = :doctorPublicId)
              and (:periodCode is null or s.periodCode = :periodCode)
              and (:status is null or s.status = :status)
            order by s.periodCode asc, s.doctor.name asc
            """)
    Page<ScheduleSlotEntity> search(@Param("departmentPublicId") UUID departmentPublicId,
                                    @Param("doctorPublicId") UUID doctorPublicId,
                                    @Param("scheduleDate") LocalDate scheduleDate,
                                    @Param("periodCode") String periodCode,
                                    @Param("status") String status,
                                    Pageable pageable);

    Optional<ScheduleSlotEntity> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from ScheduleSlotEntity s
            where s.publicId = :publicId
            """)
    Optional<ScheduleSlotEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleSlotEntity s where s.id = :id")
    Optional<ScheduleSlotEntity> findByIdForUpdate(@Param("id") Long id);
}
