package com.medora.knowledge.chunk;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalDocumentChunkerTest {
    @Test void keepsFaqPairsAndTableRowsInsideParentChildStructure() {
        List<DocumentChunk> chunks = new StructureAwareSplitter().split("""
                # 挂号规则
                | 时段 | 说明 |
                | 上午 | 可预约 |
                | 下午 | 可预约 |

                ## FAQ
                Q: 如何退号？
                A: 就诊前两小时可退号。
                Q: 如何挂号？
                A: 选择科室和时段。
                """);
        assertThat(chunks).anyMatch(c -> c.chunkType().equals("PARENT"));
        assertThat(chunks).filteredOn(c -> c.chunkType().equals("CHILD"))
                .allSatisfy(c -> assertThat(c.parentOrdinal()).isGreaterThanOrEqualTo(0));
        assertThat(chunks).anyMatch(c -> c.content().contains("Q: 如何退号？") && c.content().contains("A: 就诊前两小时可退号。"));
    }
}
