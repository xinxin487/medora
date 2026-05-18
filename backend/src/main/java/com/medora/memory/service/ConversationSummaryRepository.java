package com.medora.memory.service;

import com.medora.chat.domain.ConversationEntity;
import com.medora.memory.domain.ConversationSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationSummaryRepository extends JpaRepository<ConversationSummaryEntity, Long> {

    Optional<ConversationSummaryEntity> findByConversation(ConversationEntity conversation);

    List<ConversationSummaryEntity> findByConversationIn(Collection<ConversationEntity> conversations);
}
