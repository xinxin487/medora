package com.medora.appointment.hold;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedisHoldExpiryIndex implements HoldExpiryIndex {
    private static final String KEY = "appointment-hold-expiry";
    private final RedisTemplate<String, Object> redis;

    public RedisHoldExpiryIndex(RedisTemplate<String, Object> redis) { this.redis = redis; }

    @Override public void schedule(String holdId, OffsetDateTime expiresAt) {
        redis.opsForZSet().add(KEY, holdId, expiresAt.toInstant().toEpochMilli());
    }

    @Override public void remove(String holdId) { redis.opsForZSet().remove(KEY, holdId); }

    @Override public Set<String> dueBefore(OffsetDateTime now, int limit) {
        Set<Object> members = redis.opsForZSet().rangeByScore(KEY, 0, now.toInstant().toEpochMilli(), 0, limit);
        return members == null ? Set.of() : members.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
