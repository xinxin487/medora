package com.medora.appointment.waitlist;
import java.util.Optional;
public interface WaitlistIndex { void add(Long slotId, String entryId, double score); void remove(Long slotId, String entryId); Optional<String> popNext(Long slotId); }
