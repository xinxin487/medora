package com.medora.knowledge.chunk;

import java.util.List;

/** Chooses the largest adjacent semantic similarity drop below a threshold; callers may fall back to structure rules. */
public class SemanticBoundarySplitter {
    public int preferredBoundary(List<Double> adjacentSimilarities, double threshold) {
        if (adjacentSimilarities == null || adjacentSimilarities.size() < 2) return -1;
        int boundary = -1; double largestDrop = 0;
        for (int i = 1; i < adjacentSimilarities.size(); i++) {
            double previous = adjacentSimilarities.get(i - 1), current = adjacentSimilarities.get(i);
            double drop = previous - current;
            if (current < threshold && drop > largestDrop) { largestDrop = drop; boundary = i; }
        }
        return boundary;
    }
}
