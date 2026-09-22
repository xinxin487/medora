package com.medora.appointment.waitlist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;

@Service public class WaitlistService {
 private final WaitlistEntryRepository entries; private final WaitlistIndex index; private final Clock clock;
 public WaitlistService(WaitlistEntryRepository entries,WaitlistIndex index,Clock clock){this.entries=entries;this.index=index;this.clock=clock;}
 @Transactional public WaitlistEntryEntity join(Long userId,Long slotId,int priority){
  var saved=entries.save(WaitlistEntryEntity.waiting(userId,slotId,Math.max(0,Math.min(99,priority)),OffsetDateTime.now(clock)));
  try{index.add(slotId,String.valueOf(saved.getId()),score(saved));}catch(RuntimeException ignored){} return saved;
 }
 @Transactional public void cancel(Long userId,java.util.UUID entryId){
  var entry=entries.findByPublicId(entryId).orElseThrow(()->new IllegalArgumentException("waitlist entry not found"));
  if(!entry.getUserId().equals(userId)) throw new IllegalArgumentException("waitlist entry owner mismatch");
  entry.cancel(); try{index.remove(entry.getSlotId(),String.valueOf(entry.getId()));}catch(RuntimeException ignored){}
 }
 private double score(WaitlistEntryEntity e){return (100-e.getPriority())*1_000_000_000_000d+e.getJoinedAt().toInstant().toEpochMilli();}
}
