package com.medora.admin.service;

import com.medora.admin.api.response.AdminChatMessageItem;
import com.medora.admin.api.response.AdminConversationDetailResponse;
import com.medora.admin.api.response.AdminConversationItem;
import com.medora.admin.api.response.AdminMemoryProfileItem;
import com.medora.auth.domain.UserAccountEntity;
import com.medora.chat.domain.ChatMessageEntity;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.common.api.PageResponse;
import com.medora.common.exception.BusinessException;
import com.medora.memory.domain.ConversationSummaryEntity;
import com.medora.memory.domain.UserMemoryProfileEntity;
import com.medora.memory.service.ConversationSummaryRepository;
import com.medora.memory.service.UserMemoryProfileRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminConversationQueryService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationSummaryRepository conversationSummaryRepository;
    private final UserMemoryProfileRepository userMemoryProfileRepository;

    public AdminConversationQueryService(ConversationRepository conversationRepository,
                                         ChatMessageRepository chatMessageRepository,
                                         ConversationSummaryRepository conversationSummaryRepository,
                                         UserMemoryProfileRepository userMemoryProfileRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationSummaryRepository = conversationSummaryRepository;
        this.userMemoryProfileRepository = userMemoryProfileRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminConversationItem> list(String keyword,
                                                    String status,
                                                    String sceneType,
                                                    Integer pageNo,
                                                    Integer pageSize) {
        Page<ConversationEntity> page = conversationRepository.findAll(
                toSpec(keyword, status, sceneType),
                PageRequest.of(
                        normalizePageNo(pageNo) - 1,
                        normalizePageSize(pageSize),
                        Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("createdAt"))
                )
        );
        Map<Long, ConversationSummaryEntity> summaryMap = conversationSummaryRepository.findByConversationIn(page.getContent())
                .stream()
                .collect(Collectors.toMap(entity -> entity.getConversation().getId(), Function.identity()));
        return PageResponse.from(page.map(conversation -> toItem(conversation, summaryMap.get(conversation.getId()))));
    }

    @Transactional(readOnly = true)
    public AdminConversationDetailResponse get(String conversationId) {
        ConversationEntity conversation = conversationRepository.findByPublicId(parseConversationId(conversationId))
                .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "conversation not found"));
        ConversationSummaryEntity summary = conversationSummaryRepository.findByConversation(conversation).orElse(null);
        List<AdminMemoryProfileItem> memories = userMemoryProfileRepository
                .findTop10ByUserAccountAndStatusOrderByLastConfirmedAtDescCreatedAtDesc(
                        conversation.getUserAccount(),
                        "ACTIVE"
                ).stream()
                .map(this::toMemoryItem)
                .toList();
        return new AdminConversationDetailResponse(
                conversation.getPublicId().toString(),
                conversation.getTitle(),
                conversation.getSceneType(),
                conversation.getStatus(),
                conversation.getUserAccount().getPublicId().toString(),
                resolveUserLabel(conversation.getUserAccount()),
                chatMessageRepository.countByConversation(conversation),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                summary == null ? "" : summary.getSummaryText(),
                summary == null ? "{}" : summary.getSummaryJson(),
                summary == null ? "{}" : summary.getBusinessStateJson(),
                summary == null ? null : summary.getLastSummarizedAt(),
                memories
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminChatMessageItem> listMessages(String conversationId, Integer pageNo, Integer pageSize) {
        ConversationEntity conversation = conversationRepository.findByPublicId(parseConversationId(conversationId))
                .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "conversation not found"));
        Page<AdminChatMessageItem> page = chatMessageRepository
                .findByConversationOrderByCreatedAtAscIdAsc(
                        conversation,
                        PageRequest.of(normalizePageNo(pageNo) - 1, normalizePageSize(pageSize))
                )
                .map(this::toMessageItem);
        return PageResponse.from(page);
    }

    private Specification<ConversationEntity> toSpec(String keyword, String status, String sceneType) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<ConversationEntity, UserAccountEntity> userJoin = root.join("userAccount", JoinType.LEFT);
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), like),
                        builder.like(builder.lower(userJoin.get("username")), like),
                        builder.like(builder.lower(userJoin.get("mobile")), like),
                        builder.like(builder.lower(userJoin.get("email")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(builder.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (StringUtils.hasText(sceneType)) {
                predicates.add(builder.equal(root.get("sceneType"), sceneType.trim().toUpperCase()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminConversationItem toItem(ConversationEntity conversation, ConversationSummaryEntity summary) {
        return new AdminConversationItem(
                conversation.getPublicId().toString(),
                conversation.getTitle(),
                conversation.getSceneType(),
                conversation.getStatus(),
                conversation.getUserAccount().getPublicId().toString(),
                resolveUserLabel(conversation.getUserAccount()),
                chatMessageRepository.countByConversation(conversation),
                summary == null ? "" : summary.getSummaryText(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt()
        );
    }

    private AdminChatMessageItem toMessageItem(ChatMessageEntity entity) {
        return new AdminChatMessageItem(
                entity.getPublicId().toString(),
                entity.getRole(),
                entity.getMessageType(),
                entity.getContent(),
                entity.getCitationJson(),
                entity.getToolCallJson(),
                entity.getTokenUsageJson(),
                entity.getCreatedAt()
        );
    }

    private AdminMemoryProfileItem toMemoryItem(UserMemoryProfileEntity entity) {
        return new AdminMemoryProfileItem(
                entity.getPublicId().toString(),
                entity.getMemoryType(),
                entity.getMemoryKey(),
                entity.getSummary(),
                entity.getValueJson(),
                entity.getConfidence(),
                entity.getSource(),
                entity.getStatus(),
                entity.getLastConfirmedAt(),
                entity.getLastAccessedAt(),
                entity.getExpiresAt()
        );
    }

    private String resolveUserLabel(UserAccountEntity userAccount) {
        if (StringUtils.hasText(userAccount.getUsername())) {
            return userAccount.getUsername().trim();
        }
        if (StringUtils.hasText(userAccount.getMobile())) {
            return userAccount.getMobile().trim();
        }
        if (StringUtils.hasText(userAccount.getEmail())) {
            return userAccount.getEmail().trim();
        }
        return userAccount.getPublicId().toString();
    }

    private UUID parseConversationId(String conversationId) {
        try {
            return UUID.fromString(conversationId);
        } catch (RuntimeException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "conversationId must be a valid uuid");
        }
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }
}
