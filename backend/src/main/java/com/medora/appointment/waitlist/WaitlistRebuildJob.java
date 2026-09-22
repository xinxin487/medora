package com.medora.appointment.waitlist;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component public class WaitlistRebuildJob {
 private final WaitlistEntryRepository entries; private final WaitlistIndex index;
 public WaitlistRebuildJob(WaitlistEntryRepository entries,WaitlistIndex index){this.entries=entries;this.index=index;}
 @Scheduled(cron="${medora.appointment.waitlist-rebuild-cron:0 10 3 * * *}") public void rebuild(){
  for(var entry:entries.findByStatusOrderByPriorityDescJoinedAtAsc("WAITING")) index.add(entry.getSlotId(),String.valueOf(entry.getId()),score(entry));
 }
 private double score(WaitlistEntryEntity entry){return (100-entry.getPriority())*1_000_000_000_000d+entry.getJoinedAt().toInstant().toEpochMilli();}
}
