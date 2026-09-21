package com.medora.a2ui.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface A2uiActionRepository extends JpaRepository<A2uiActionEntity, Long> {
    Optional<A2uiActionEntity> findByPublicId(UUID publicId);
}
