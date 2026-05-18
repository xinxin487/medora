package com.medora.memory.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.memory.domain.UserMemoryProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserMemoryProfileRepository extends JpaRepository<UserMemoryProfileEntity, Long> {

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

    List<UserMemoryProfileEntity> findTop10ByUserAccountAndStatusOrderByLastConfirmedAtDescCreatedAtDesc(
            UserAccountEntity userAccount,
            String status
    );
}
