package com.medora.eval.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 10 W4 — 录制/回放 LlmClient 单测。
 *
 * <p>验证:
 * <ul>
 *   <li>RecordingLlmClient 首次调真实函数,写入文件;再次同 prompt 命中缓存不调真实函数</li>
 *   <li>ReplayingLlmClient 从录制文件读取,命中返回;未命中抛 ReplayMissException</li>
 *   <li>跨进程:用 RecordingLlmClient 录 → 用 ReplayingLlmClient 回放,完全无网络</li>
 * </ul>
 */
class RecordReplayTest {

    @TempDir
    Path tempDir;

    @Test
    void recordingClientCallsRealOnceThenCaches() throws Exception {
        Path recording = tempDir.resolve("rec.json");
        AtomicInteger callCount = new AtomicInteger();
        LlmClient client = new RecordingLlmClient(
                (sys, usr) -> {
                    callCount.incrementAndGet();
                    return "llm response for " + usr;
                },
                new LlmRecorderStore(recording)
        );

        String r1 = client.complete("sys", "hello");
        String r2 = client.complete("sys", "hello");

        assertThat(r1).isEqualTo("llm response for hello");
        assertThat(r2).isEqualTo(r1);
        assertThat(callCount.get()).isEqualTo(1); // 第二次命中缓存,真实函数只调 1 次
        assertThat(recording).exists();
    }

    @Test
    void recordingClientDifferentPromptsCacheSeparately() throws Exception {
        Path recording = tempDir.resolve("rec.json");
        AtomicInteger callCount = new AtomicInteger();
        LlmClient client = new RecordingLlmClient(
                (sys, usr) -> { callCount.incrementAndGet(); return "resp " + usr; },
                new LlmRecorderStore(recording)
        );

        client.complete("sys", "msg1");
        client.complete("sys", "msg2");

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void replayingClientReadsFromRecording() throws Exception {
        Path recording = tempDir.resolve("rec.json");
        AtomicInteger callCount = new AtomicInteger();

        // 录制阶段
        new RecordingLlmClient(
                (sys, usr) -> { callCount.incrementAndGet(); return "REAL: " + usr; },
                new LlmRecorderStore(recording)
        ).complete("system", "user-message");

        // 回放阶段(全新对象,无真实函数)
        LlmClient replaying = new ReplayingLlmClient(new LlmRecorderStore(recording));
        String replayed = replaying.complete("system", "user-message");

        assertThat(replayed).isEqualTo("REAL: user-message");
        assertThat(callCount.get()).isEqualTo(1); // 真实函数只在录制阶段调过一次
    }

    @Test
    void replayingClientThrowsOnMiss() throws Exception {
        Path recording = tempDir.resolve("rec.json");
        // 录制 prompt1
        new RecordingLlmClient(
                (sys, usr) -> "response",
                new LlmRecorderStore(recording)
        ).complete("sys", "prompt1");

        // 回放不同的 prompt2 → 未命中
        ReplayingLlmClient replaying = new ReplayingLlmClient(new LlmRecorderStore(recording));
        assertThatThrownBy(() -> replaying.complete("sys", "prompt2"))
                .isInstanceOf(ReplayingLlmClient.ReplayMissException.class)
                .hasMessageContaining("no recording");
    }

    @Test
    void promptHasherIsStableAcrossRuns() {
        String h1 = PromptHasher.hash("sys", "usr");
        String h2 = PromptHasher.hash("sys", "usr");
        String h3 = PromptHasher.hash("sys", "different");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
        assertThat(h1).hasSize(16); // 8 字节 = 16 hex 字符
    }
}
