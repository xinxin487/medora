package com.medora.agent.context;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationCompressorTest {
    @Test void retainsNewestTurnsWhenCompressionFallsBack() {
        var compression = new ConversationCompressor().compress(List.of("one", "two", "three"), 2);
        assertThat(compression.recentTurns()).containsExactly("two", "three");
        assertThat(compression.confirmedFacts()).isEmpty();
    }
}
