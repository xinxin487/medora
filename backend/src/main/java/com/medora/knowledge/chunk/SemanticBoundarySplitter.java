package com.medora.knowledge.chunk;

import com.medora.ai.embedding.EmbeddingService;
import com.medora.ai.embedding.EmbeddingVector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Chooses the largest adjacent semantic similarity drop below a threshold; callers may fall back to structure rules. */
@Component
public class SemanticBoundarySplitter {

    private final EmbeddingService embeddingService;

    public SemanticBoundarySplitter(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /** Returns the number of units that belong on the left side, or -1 when no semantic cut is preferred. */
    public int preferredBoundaryForUnits(List<String> units, double threshold) {
        if (units == null || units.size() < 2) {
            return -1;
        }
        List<Double> similarities = new ArrayList<>(units.size() - 1);
        EmbeddingVector previous = embeddingService.embed(units.getFirst());
        for (int index = 1; index < units.size(); index++) {
            EmbeddingVector current = embeddingService.embed(units.get(index));
            similarities.add(cosine(previous.values(), current.values()));
            previous = current;
        }
        int similarityIndex = preferredBoundary(similarities, threshold);
        return similarityIndex < 0 ? -1 : similarityIndex + 1;
    }
    public int preferredBoundary(List<Double> adjacentSimilarities, double threshold) {
        if (adjacentSimilarities == null || adjacentSimilarities.isEmpty()) return -1;
        int boundary = -1; double largestDrop = 0;
        for (int i = 0; i < adjacentSimilarities.size(); i++) {
            double previous = i == 0 ? 1.0 : adjacentSimilarities.get(i - 1);
            double current = adjacentSimilarities.get(i);
            double drop = previous - current;
            if (current < threshold && drop > largestDrop) { largestDrop = drop; boundary = i; }
        }
        return boundary;
    }

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("embedding dimensions must match");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
