package com.medora.appointment.hold;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RedisSlotLock implements SlotLock {
    private static final Duration TTL = Duration.ofSeconds(15);
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisSlotLock(RedisTemplate<String, Object> redisTemplate) { this.redisTemplate = redisTemplate; }

    @Override
    public Lease acquire(UUID slotId) {
        String key = "slot-lock:" + slotId;
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, owner, TTL);
        if (!Boolean.TRUE.equals(acquired)) throw new IllegalStateException("slot lock is already held");
        return () -> redisTemplate.execute(RELEASE_IF_OWNER, List.of(key), owner);
    }
}
