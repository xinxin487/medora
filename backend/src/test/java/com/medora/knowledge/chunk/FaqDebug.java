package com.medora.knowledge.chunk;

import java.util.List;

public class FaqDebug {
    public static void main(String[] args) {
        String c = "Q：如何取消挂号？\nA：请至少提前一天在线上操作取消。\n\nQ：挂号后可以更换医生吗？\nA：请在就诊当天到医院窗口办理。\n\nQ：在线支付后如何获取发票？\nA：可在医院自助机打印电子发票。";
        System.out.println("=== Direct split ===");
        String[] blocks = c.split("\\R{2,}");
        System.out.println("blocks=" + blocks.length);
        for (int i = 0; i < blocks.length; i++) {
            String trimmed = blocks[i].trim();
            System.out.println("[" + i + "] trimmed='" + trimmed.substring(0, Math.min(10, trimmed.length())) + "'");
            System.out.println("  isQuestion=" + trimmed.matches("^(Q[：:]|问[：:]).*"));
            System.out.println("  isAnswer=" + trimmed.matches("^(A[：:]|答[：:]).*"));
        }

        System.out.println("\n=== Using chunker ===");
        com.medora.ai.config.AiProperties props = new com.medora.ai.config.AiProperties();
        DocumentChunker chunker = new DocumentChunker(props);
        System.out.println("isFaq=" + DocumentChunker.isFaqContent(c));
        List<DocumentChunk> chunks = chunker.chunk(c);
        System.out.println("chunks=" + chunks.size());
        for (DocumentChunk chunk : chunks) {
            System.out.println("  chunk: [" + chunk.content().substring(0, Math.min(30, chunk.content().length())) + "...]");
        }
    }
}
