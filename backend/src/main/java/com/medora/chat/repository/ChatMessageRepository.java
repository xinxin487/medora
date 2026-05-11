package com.medora.chat.repository;

import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findByConversationAndClientMessageId(
            ConversationEntity conversation,
            String clientMessageId
    );

    Page<ChatMessageEntity> findByConversationOrderByCreatedAtAscIdAsc(
            ConversationEntity conversation,
            Pageable pageable
    );
}
