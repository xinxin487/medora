package com.medora.memory.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.memory.domain.UserMemoryProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserMemoryProfileRepository extends JpaRepository<UserMemoryProfileEntity, Long> {

    List<UserMemoryProfileEntity> findTop100ByUserAccountIdOrderByUpdatedAtDesc(Long userAccountId);

    List<UserMemoryProfileEntity> findTop100ByUserAccountIdAndStatusOrderByUpdatedAtDesc(
            Long userAccountId, String status);

    List<UserMemoryProfileEntity> findByUserAccountIdAndStatusAndMemoryTypeInOrderByConfidenceDescUpdatedAtDesc(
            Long userAccountId,
            String status,
            Collection<String> memoryTypes
    );

    Optional<UserMemoryProfileEntity> findByUserAccountAndMemoryTypeAndMemoryKey(
            UserAccountEntity userAccount,
            String memoryType,
            String memoryKey
    );

    Optional<UserMemoryProfileEntity> findByUserAccountIdAndFingerprint(Long userAccountId, String fingerprint);

    List<UserMemoryProfileEntity> findByUserAccountIdAndStatusAndMemoryTypeAndScopeKey(
            Long userAccountId, String status, String memoryType, String scopeKey);

    List<UserMemoryProfileEntity> findTop10ByUserAccountAndStatusOrderByLastConfirmedAtDescCreatedAtDesc(
            UserAccountEntity userAccount,
            String status
    );

    List<UserMemoryProfileEntity> findByExpiresAtBeforeAndStatus(
            OffsetDateTime expiresAt,
            String status
    );

    List<UserMemoryProfileEntity> findByUserAccountIdAndStatusOrderByMemoryTypeAscLastConfirmedAtDesc(
            Long userAccountId,
            String status
    );
}
