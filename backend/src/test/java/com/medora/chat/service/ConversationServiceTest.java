package com.medora.chat.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.chat.api.request.CreateConversationRequest;
import com.medora.chat.domain.ConversationEntity;
import com.medora.chat.repository.ChatMessageRepository;
import com.medora.chat.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    private CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private UserAccountRepository userAccountRepository;
    private ConversationRepository conversationRepository;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        currentAuthenticatedUserProvider = mock(CurrentAuthenticatedUserProvider.class);
        userAccountRepository = mock(UserAccountRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        service = new ConversationService(
                currentAuthenticatedUserProvider,
                userAccountRepository,
                conversationRepository,
                mock(ChatMessageRepository.class)
        );
    }

    @Test
    void shouldCreateConversationWithDefaults() {
        UUID userPublicId = UUID.randomUUID();
        UserAccountEntity user = new UserAccountEntity();
        ReflectionTestUtils.setField(user, "id", 10L);
        ReflectionTestUtils.setField(user, "publicId", userPublicId);

        when(currentAuthenticatedUserProvider.getRequiredUser())
                .thenReturn(new AuthenticatedUser(userPublicId, List.of("USER")));
        when(userAccountRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(conversationRepository.save(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "publicId", UUID.randomUUID());
            return entity;
        });

        var response = service.create(new CreateConversationRequest(null, null));

        assertEquals("导诊咨询", response.title());
        assertEquals("GENERAL", response.sceneType());
        assertEquals("ACTIVE", response.status());
    }
}
