package com.medora.chat.cache;

import com.medora.memory.service.ConversationSummaryService.RecentMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisContextCacheService {

    private static final String MESSAGE_LIST_KEY_PREFIX = "chat:ctx:";
    private static final int MAX_MESSAGES = 12;

    private final RedisTemplate<String, Object> redisTemplate;
    private final int defaultTtlSeconds;

    public RedisContextCacheService(RedisTemplate<String, Object> redisTemplate,
                                    com.medora.ai.config.AiProperties aiProperties) {
        this.redisTemplate = redisTemplate;
        this.defaultTtlSeconds = aiProperties.getContextCacheTtlSeconds();
    }

    private String messageListKey(String conversationPublicId) {
        return MESSAGE_LIST_KEY_PREFIX + conversationPublicId + ":messages";
    }

    /** Push a message (role + content pair) to the conversation's Redis list.
     *  The list is trimmed to MAX_MESSAGES and its TTL is reset on each write. */
    public void pushMessage(String conversationPublicId, String role, String content) {
        String key = messageListKey(conversationPublicId);
        redisTemplate.opsForList().rightPush(key, new RecentMessage(role, content));
        redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
        redisTemplate.expire(key, defaultTtlSeconds, TimeUnit.SECONDS);
    }

    /** Get cached recent messages for a conversation. Returns null if the cache is absent or empty. */
    public List<RecentMessage> getRecentMessages(String conversationPublicId) {
        String key = messageListKey(conversationPublicId);
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return raw.stream()
                .filter(RecentMessage.class::isInstance)
                .map(RecentMessage.class::cast)
                .toList();
    }

    /** Delete the entire conversation cache (e.g. when conversation is closed). */
    public void deleteContext(String conversationPublicId) {
        redisTemplate.delete(messageListKey(conversationPublicId));
    }
}
