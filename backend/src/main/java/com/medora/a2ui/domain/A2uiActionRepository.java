package com.medora.a2ui.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface A2uiActionRepository extends JpaRepository<A2uiActionEntity, Long> {
    Optional<A2uiActionEntity> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from A2uiActionEntity a where a.publicId = :publicId")
    Optional<A2uiActionEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from A2uiActionEntity a where a.userId = :userId and a.conversationId = :conversationId and a.status = 'PENDING' order by a.createdAt asc")
    List<A2uiActionEntity> findPendingByUserIdAndConversationIdForUpdate(@Param("userId") Long userId,
                                                                           @Param("conversationId") UUID conversationId);
}
