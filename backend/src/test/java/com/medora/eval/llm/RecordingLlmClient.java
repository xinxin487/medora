package com.medora.eval.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.BiFunction;

/**
 * Phase 10 W4 — 录制型 LlmClient。
 *
 * <p>包装一个"真实 LLM 调用函数"(BiFunction&lt;system, user, response&gt;),
 * 首次跑时把响应写入录制文件;如果同 key 已存在,直接用录制结果(避免重复跑/烧 token)。
 *
 * <p>用法:
 * <pre>
 * LlmClient client = new RecordingLlmClient(
 *     (sys, usr) -> agentModelGateway.callOpenAiCompatible(...),
 *     Path.of("eval/recordings/judge.json")
 * );
 * </pre>
 *
 * <p>为什么用 BiFunction 而不是直接依赖 AgentModelGateway:
 * 把"调 LLM"的实现细节(模型选型、URL、超时)交给生产代码,录制器只负责"包装 + 缓存"。
 */
public class RecordingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RecordingLlmClient.class);

    private final BiFunction<String, String, String> realCall;
    private final LlmRecorderStore store;
    private final LlmCallRecord cache;

    public RecordingLlmClient(BiFunction<String, String, String> realCall, LlmRecorderStore store) throws IOException {
        this.realCall = realCall;
        this.store = store;
        this.cache = store.load();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String key = PromptHasher.hash(systemPrompt, userPrompt);
        LlmCallRecord.Entry hit = cache.find(key);
        if (hit != null) {
            log.debug("recording hit key={} file={}", key, store.recordingFile());
            return hit.response;
        }
        log.info("recording miss key={} file={} — calling real LLM", key, store.recordingFile());
        String response = realCall.apply(systemPrompt, userPrompt);
        if (response == null) {
            throw new IllegalStateException("real LLM call returned null for key=" + key);
        }
        cache.add(new LlmCallRecord.Entry(key, systemPrompt, userPrompt, response));
        store.save(cache);
        return response;
    }

    @Override
    public String describe() {
        return "recording:" + store.recordingFile();
    }

    public int recordedCount() {
        return cache.size();
    }
}
