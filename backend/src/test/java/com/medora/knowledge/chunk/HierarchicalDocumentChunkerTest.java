package com.medora.knowledge.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.ai.config.AiProperties;
import com.medora.ai.embedding.EmbeddingService;
import com.medora.ai.embedding.EmbeddingVector;
import com.medora.knowledge.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalDocumentChunkerTest {
    @Test void keepsFaqPairsAndTableRowsInsideParentChildStructure() {
        List<DocumentChunk> chunks = splitter(new HashMap<>()).split("""
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

    @Test void usesEmbeddingSimilarityToChooseAChildBoundary() {
        Map<String, double[]> vectors = new HashMap<>();
        vectors.put("呼吸门诊提供咳嗽和哮喘评估。", new double[]{1, 0});
        vectors.put("胸闷患者也可以到呼吸门诊就诊。", new double[]{.99, .01});
        vectors.put("退号需在就诊前一天办理。", new double[]{0, 1});
        vectors.put("超时退号请联系人工窗口。", new double[]{.01, .99});

        List<DocumentChunk> children = splitter(vectors).split("""
                # 门诊指南

                呼吸门诊提供咳嗽和哮喘评估。

                胸闷患者也可以到呼吸门诊就诊。

                退号需在就诊前一天办理。

                超时退号请联系人工窗口。
                """).stream().filter(chunk -> "CHILD".equals(chunk.chunkType())).toList();

        assertThat(children).hasSize(2);
        assertThat(children.getFirst().content()).contains("咳嗽", "胸闷").doesNotContain("退号");
        assertThat(children.getLast().content()).contains("退号", "人工窗口");
    }

    @Test void fallsBackToOrderedRuleBoundariesWhenEmbeddingFails() {
        EmbeddingService failing = new EmbeddingService(new AiProperties(), new ObjectMapper()) {
            @Override public EmbeddingVector embed(String input) { throw new IllegalStateException("offline"); }
        };
        RagProperties properties = smallChunkProperties();

        List<DocumentChunk> children = new StructureAwareSplitter(
                new SemanticBoundarySplitter(failing), properties).split("""
                # 门诊指南

                第一段。

                第二段。
                """).stream().filter(chunk -> "CHILD".equals(chunk.chunkType())).toList();

        assertThat(children).isNotEmpty();
        assertThat(children.stream().map(DocumentChunk::index).toList()).isSorted();
    }

    @Test void splitsTwoNaturalUnitsWhenTheirSimilarityIsBelowThreshold() {
        Map<String, double[]> vectors = new HashMap<>();
        vectors.put("呼吸科处理咳嗽。", new double[]{1, 0});
        vectors.put("退号请到窗口办理。", new double[]{0, 1});

        List<DocumentChunk> children = splitter(vectors).split("""
                # 指南

                呼吸科处理咳嗽。

                退号请到窗口办理。
                """).stream().filter(chunk -> "CHILD".equals(chunk.chunkType())).toList();

        assertThat(children).hasSize(2);
    }

    @Test void overlapNeverPushesAChildPastTheConfiguredMaximum() {
        RagProperties properties = smallChunkProperties();
        properties.setChildMaxTokens(15);
        properties.setChildOverlapTokens(2);
        EmbeddingService equalEmbeddings = new EmbeddingService(new AiProperties(), new ObjectMapper()) {
            @Override public EmbeddingVector embed(String input) {
                return new EmbeddingVector(new double[]{1, 1}, "test");
            }
        };

        List<DocumentChunk> children = new StructureAwareSplitter(
                new SemanticBoundarySplitter(equalEmbeddings), properties).split("""
                # 指南

                甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉

                一二三四五六七八九十甲二丙二丁二戊二己二庚二
                """).stream().filter(chunk -> "CHILD".equals(chunk.chunkType())).toList();

        assertThat(children).hasSize(2).allMatch(chunk -> chunk.tokenCount() <= 15);
        assertThat(children.getLast().content()).startsWith("午未申酉");
    }

    @Test void forceSplitsOversizedProseWithoutSentenceBoundaries() {
        RagProperties properties = smallChunkProperties();
        properties.setChildMaxTokens(10);
        EmbeddingService equalEmbeddings = new EmbeddingService(new AiProperties(), new ObjectMapper()) {
            @Override public EmbeddingVector embed(String input) {
                return new EmbeddingVector(new double[]{1, 1}, "test");
            }
        };

        List<DocumentChunk> children = new StructureAwareSplitter(
                new SemanticBoundarySplitter(equalEmbeddings), properties).split("""
                # 指南

                甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥天地玄黄宇宙洪荒
                """).stream().filter(chunk -> "CHILD".equals(chunk.chunkType())).toList();

        assertThat(children).hasSizeGreaterThan(1).allMatch(chunk -> chunk.tokenCount() <= 10);
    }

    private StructureAwareSplitter splitter(Map<String, double[]> vectors) {
        EmbeddingService embeddings = new EmbeddingService(new AiProperties(), new ObjectMapper()) {
            @Override public EmbeddingVector embed(String input) {
                return new EmbeddingVector(vectors.getOrDefault(input, new double[]{1, 1}), "test");
            }
        };
        return new StructureAwareSplitter(new SemanticBoundarySplitter(embeddings), smallChunkProperties());
    }

    private RagProperties smallChunkProperties() {
        RagProperties properties = new RagProperties();
        properties.setChildMinTokens(1);
        properties.setChildMaxTokens(100);
        properties.setChildOverlapTokens(0);
        properties.setSemanticBoundaryThreshold(.5);
        return properties;
    }
}
