package com.medora.eval.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Phase 10 W4 — 回放型 LlmClient。
 *
 * <p>从录制文件读取所有 entries,按 hash 命中返回。无网络调用,CI 用。
 *
 * <p>找不到 key 时抛 {@link ReplayMissException},让上层判断是降级还是 fail-fast。
 */
public class ReplayingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ReplayingLlmClient.class);

    private final LlmCallRecord record;

    public ReplayingLlmClient(LlmRecorderStore store) throws IOException {
        this.record = store.load();
        log.info("ReplayingLlmClient loaded {} entries from {}", record.size(), store.recordingFile());
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws ReplayMissException {
        String key = PromptHasher.hash(systemPrompt, userPrompt);
        LlmCallRecord.Entry hit = record.find(key);
        if (hit == null) {
            throw new ReplayMissException("no recording for key=" + key
                    + " systemPrompt.prefix=" + snippet(systemPrompt)
                    + " userPrompt.prefix=" + snippet(userPrompt));
        }
        return hit.response;
    }

    @Override
    public String describe() {
        return "replaying(" + record.size() + " entries)";
    }

    private static String snippet(String s) {
        if (s == null) return "(null)";
        return s.length() <= 60 ? s : s.substring(0, 60) + "...";
    }

    public static class ReplayMissException extends RuntimeException {
        public ReplayMissException(String message) {
            super(message);
        }
    }
}
