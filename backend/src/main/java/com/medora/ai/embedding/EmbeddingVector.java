package com.medora.ai.embedding;

import java.util.Arrays;

public record EmbeddingVector(
        double[] values,
        String model
) {
    public EmbeddingVector {
        values = values == null ? new double[0] : Arrays.copyOf(values, values.length);
    }

    @Override
    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
