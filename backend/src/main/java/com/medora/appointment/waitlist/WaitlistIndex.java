package com.medora.appointment.waitlist;
import java.util.List;
public interface WaitlistIndex { void add(Long slotId, String entryId, double score); void remove(Long slotId, String entryId); List<String> popNext(Long slotId); }
