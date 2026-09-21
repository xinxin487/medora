package com.medora.admin.api.response;

import java.util.List;
import java.util.Map;

/** Deliberately excludes prompts, messages, and memory values. */
public record AdminContextDiagnosticResponse(String traceId, String profile, Map<String, Integer> budget,
                                             Map<String, Integer> sourceCounts, Map<String, Integer> reasonCounts,
                                             List<String> itemFingerprints, boolean degraded) { }
