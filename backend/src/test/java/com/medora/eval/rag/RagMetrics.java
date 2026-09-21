package com.medora.eval.rag;

import java.util.List;

public final class RagMetrics {
    private RagMetrics() { }
    public static double recallAt5(String expected, List<String> actual) { return actual.stream().limit(5).anyMatch(expected::equals) ? 1.0 : 0.0; }
    public static double reciprocalRankAt5(String expected, List<String> actual) { for (int i=0;i<Math.min(5,actual.size());i++) if (expected.equals(actual.get(i))) return 1.0/(i+1); return 0; }
}
