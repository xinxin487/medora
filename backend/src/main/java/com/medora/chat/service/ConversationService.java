package com.medora.chat.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.chat.api.request.CreateConversationRequest;
import com.medora.chat.api.response.ChatMessageItem;
import com.medora.chat.api.response.ConversationItem;
import com.medora.chat.api.response.ConversationResponse;
import com.medora.chat.api.response.ConversationSummaryResponse;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import com.medora.common.api.PageResponse;
import com.medora.common.exception.BusinessException;
import com.medora.memory.service.ConversationSummaryService;
import com.medora.memory.service.ConversationSummaryService.ConversationContextSnapshot;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class ConversationService {

    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationSummaryService conversationSummaryService;

    public ConversationService(CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                               UserAccountRepository userAccountRepository,
                               ConversationRepository conversationRepository,
                               ChatMessageRepository chatMessageRepository,
                               ConversationSummaryService conversationSummaryService) {
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationSummaryService = conversationSummaryService;
    }

    @Transactional
    public ConversationResponse create(CreateConversationRequest request) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        ConversationEntity conversation = ConversationEntity.create(
                userAccount,
                normalizeTitle(request.title()),
                normalizeSceneType(request.sceneType())
        );
        return ConversationResponseMapper.toResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationItem> listMine(Integer pageNo, Integer pageSize) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        var page = conversationRepository
                .findByUserAccountOrderByLastMessageAtDescCreatedAtDesc(userAccount, toPageRequest(pageNo, pageSize))
                .map(ConversationResponseMapper::toItem);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatMessageItem> listMessages(String conversationId, Integer pageNo, Integer pageSize) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        ConversationEntity conversation = findOwnedConversation(conversationId, userAccount);
        var page = chatMessageRepository
                .findByConversationOrderByCreatedAtAscIdAsc(conversation, toPageRequest(pageNo, pageSize))
                .map(ConversationResponseMapper::toMessageItem);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ConversationSummaryResponse getSummary(String conversationId) {
        UserAccountEntity userAccount = getCurrentUserAccount();
        ConversationEntity conversation = findOwnedConversation(conversationId, userAccount);
        ConversationContextSnapshot snapshot = conversationSummaryService.loadContext(conversation.getId());
        return new ConversationSummaryResponse(
                snapshot.sceneType(),
                snapshot.summaryText(),
                snapshot.summaryData(),
                snapshot.businessState(),
                snapshot.recentMessages().stream()
                        .map(m -> new ConversationSummaryResponse.RecentMessageItem(m.role(), m.content()))
                        .toList()
        );
    }

    private ConversationEntity findOwnedConversation(String conversationId, UserAccountEntity userAccount) {
        return conversationRepository.findByPublicIdAndUserAccount(parseUuid(conversationId), userAccount)
                .orElseThrow(() -> new BusinessException("CHAT_CONVERSATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "conversation not found"));
    }

    private UserAccountEntity getCurrentUserAccount() {
        AuthenticatedUser authenticatedUser = currentAuthenticatedUserProvider.getRequiredUser();
        return userAccountRepository.findByPublicId(authenticatedUser.userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "current user not found"));
    }

    private PageRequest toPageRequest(Integer pageNo, Integer pageSize) {
        int normalizedPageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        return PageRequest.of(normalizedPageNo - 1, normalizedPageSize);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "conversationId must be a valid uuid");
        }
    }

    private String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : "导诊咨询";
    }

    private String normalizeSceneType(String sceneType) {
        return StringUtils.hasText(sceneType) ? sceneType.trim() : "GENERAL";
    }
}
