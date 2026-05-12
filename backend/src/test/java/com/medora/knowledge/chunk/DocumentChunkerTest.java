package com.medora.knowledge.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {

    @Test
    void shouldChunkMedicalKnowledgeDocumentByParagraphs() {
        DocumentChunker chunker = new DocumentChunker();

        List<DocumentChunk> chunks = chunker.chunk("""
                # 挂号规则

                如需取消挂号，请在就诊前一天完成取消。超过可取消时间后，请联系医院窗口处理。

                # 科室说明

                长期咳嗽、胸闷、呼吸不适可优先咨询呼吸内科。
                """);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.getFirst().content().contains("取消挂号"));
    }
}
