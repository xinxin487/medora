package com.medora.agent.context;

import com.medora.memory.service.MemoryService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MemoryContextProvider implements ContextProvider {
    private final MemoryService memories;
    private final ContextProfileResolver resolver;
    private final TokenEstimator estimator = new TokenEstimator();

    public MemoryContextProvider(MemoryService memories, ContextProfileResolver resolver) {
        this.memories = memories;
        this.resolver = resolver;
    }

    @Override
    public String sourceName() { return "MEMORY"; }

    @Override
    public Result load(ContextBuildRequest request) {
        ContextProfile profile = resolver.resolve(request.message(), request.sceneType());
        List<ContextItem> items = new ArrayList<>();
        List<ContextDecision> decisions = new ArrayList<>();
        for (MemoryService.MemoryContextCandidate candidate : memories.loadContextCandidates(request.userId())) {
            if (candidate.summary() == null || candidate.summary().isBlank()) continue;
            String fingerprint = ContextFingerprint.of(candidate.summary());
            int tokens = estimator.estimate(candidate.summary());
            String reason = null;
            if (!"ACTIVE".equals(candidate.status())) reason = "INACTIVE";
            else if (candidate.expiresAt() != null && !candidate.expiresAt().isAfter(OffsetDateTime.now()))
                reason = "EXPIRED";
            else if (!matches(profile, candidate.memoryType())) reason = "PROFILE_MISMATCH";
            if (reason != null) {
                decisions.add(new ContextDecision("MEMORY", fingerprint, tokens, false, reason));
            } else {
                items.add(new ContextItem("MEMORY", candidate.memoryType(), fingerprint,
                        candidate.summary(), candidate.confidence(), candidate.source(),
                        candidate.expiresAt(), tokens, candidate.confirmedAt()));
            }
        }
        return new Result("MEMORY", items, Map.of(), decisions);
    }

    private boolean matches(ContextProfile profile, String memoryType) {
        if (profile == ContextProfile.BOOKING) return true;
        return "PREFERRED_DEPARTMENT".equals(memoryType) || "PREFERRED_PERIOD".equals(memoryType);
    }
}
