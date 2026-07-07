package com.medora.chat.repository;

import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    List<ChatMessageEntity> findTop12ByConversationOrderByCreatedAtDescIdDesc(ConversationEntity conversation);

    /**
     * 最近 N 条消息(倒序取,业务层按需 reverse)。
     * 给 ADK PostgresBackedSessionService.listEvents 用 —— 反构历史对话给 LLM 看。
     */
    List<ChatMessageEntity> findTop30ByConversationOrderByCreatedAtDescIdDesc(ConversationEntity conversation);

    long countByConversation(ConversationEntity conversation);
}
