package com.medora.eval.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medora.eval.domain.EvalRun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 10 — 把 EvalRun 序列化为结构化 JSON。
 *
 * <p>用途:
 * <ul>
 *   <li>作为 baseline 入 git(主线 commit 的报告快照)</li>
 *   <li>供后续 DiffReporter 对比</li>
 *   <li>供 CI 解析</li>
 * </ul>
 */
public class JsonReportWriter {

    private final ObjectMapper mapper;

    public JsonReportWriter() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String toJson(EvalRun run) throws IOException {
        return mapper.writeValueAsString(run);
    }

    public void write(EvalRun run, Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        mapper.writeValue(output.toFile(), run);
    }
}
