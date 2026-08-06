package com.medora.chat.repository;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long>,
        JpaSpecificationExecutor<ConversationEntity> {

    Optional<ConversationEntity> findByPublicId(UUID publicId);

    Optional<ConversationEntity> findByPublicIdAndUserAccount(UUID publicId, UserAccountEntity userAccount);

    Page<ConversationEntity> findByUserAccountOrderByLastMessageAtDescCreatedAtDesc(
            UserAccountEntity userAccount,
            Pageable pageable
    );

    Page<ConversationEntity> findByUserAccountAndStatusNotOrderByLastMessageAtDescCreatedAtDesc(
            UserAccountEntity userAccount,
            String status,
            Pageable pageable
    );
}
