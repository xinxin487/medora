package com.medora.appointment.waitlist;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisWaitlistIndexTest {

    @Test
    void claimsOnlyOneCandidateFromTheRedisQueue() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ZSetOperations<String, Object> zset = mock(ZSetOperations.class);
        ZSetOperations.TypedTuple<Object> candidate = mock(ZSetOperations.TypedTuple.class);
        when(candidate.getValue()).thenReturn("30");
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.popMin("waitlist:19", 1)).thenReturn(Set.of(candidate));

        Optional<String> claimed = new RedisWaitlistIndex(redis).popNext(19L);

        assertThat(claimed).contains("30");
        verify(zset).popMin("waitlist:19", 1);
    }
}
