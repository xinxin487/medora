package com.medora.masterdata.repository;

import com.medora.masterdata.domain.DepartmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {

    Page<DepartmentEntity> findByStatusAndNameContainingIgnoreCase(String status, String keyword, Pageable pageable);

    Page<DepartmentEntity> findByStatus(String status, Pageable pageable);

    java.util.Optional<DepartmentEntity> findByPublicId(UUID publicId);
}
