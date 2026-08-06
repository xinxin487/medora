package com.medora.knowledge.chunk;

import com.medora.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker(new AiProperties());

    @Test
    void shouldChunkMedicalKnowledgeDocumentByParagraphs() {
        List<DocumentChunk> chunks = chunker.chunk("""
                # 挂号规则

                如需取消挂号，请在就诊前一天完成取消。超过可取消时间后，请联系医院窗口处理。

                # 科室说明

                长期咳嗽、胸闷、呼吸不适可优先咨询呼吸内科。
                """);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.getFirst().content().contains("取消挂号"));
    }

    @Test
    void shouldDetectFaqContent() {
        assertTrue(DocumentChunker.isFaqContent("Q：如何取消挂号？"));
        assertTrue(DocumentChunker.isFaqContent("Q:如何取消挂号？"));
        assertTrue(DocumentChunker.isFaqContent("问：如何取消挂号？"));
        assertTrue(DocumentChunker.isFaqContent("问:如何取消挂号？"));
        assertFalse(DocumentChunker.isFaqContent("普通文档内容"));
    }

    @Test
    void shouldKeepFaqPairAsSingleChunk() {
        String faqContent = "Q：如何取消挂号？\nA：请至少提前一天在线上操作取消。\n\n"
                + "Q：挂号后可以更换医生吗？\nA：请在就诊当天到医院窗口办理。\n\n"
                + "Q：在线支付后如何获取发票？\nA：可在医院自助机打印电子发票。";

        List<DocumentChunk> chunks = chunker.chunk(faqContent);

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).content().contains("Q：") && chunks.get(0).content().contains("A："));
        assertTrue(chunks.get(1).content().contains("更换医生"));
        assertTrue(chunks.get(2).content().contains("发票"));
    }
}
