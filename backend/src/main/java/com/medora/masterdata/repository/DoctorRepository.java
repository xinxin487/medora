package com.medora.masterdata.repository;

import com.medora.masterdata.domain.DoctorEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<DoctorEntity, Long> {

    @Query("""
            select d
            from DoctorEntity d
            where (:departmentPublicId is null or d.department.publicId = :departmentPublicId)
              and (:status is null or d.status = :status)
              and (:keyword is null or lower(d.name) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<DoctorEntity> search(@Param("departmentPublicId") UUID departmentPublicId,
                              @Param("status") String status,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    Optional<DoctorEntity> findByPublicId(UUID publicId);
}
