package com.medora.appointment.waitlist;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Optional;
@Component public class RedisWaitlistIndex implements WaitlistIndex {
 private final RedisTemplate<String,Object> redis; public RedisWaitlistIndex(RedisTemplate<String,Object> redis){this.redis=redis;}
 private String key(Long slotId){return "waitlist:"+slotId;}
 public void add(Long slotId,String entryId,double score){redis.opsForZSet().add(key(slotId),entryId,score);}
 public void remove(Long slotId,String entryId){redis.opsForZSet().remove(key(slotId),entryId);}
 public Optional<String> popNext(Long slotId){var values=redis.opsForZSet().popMin(key(slotId),1); return values==null?Optional.empty():values.stream().findFirst().map(v->String.valueOf(v.getValue()));}
}
