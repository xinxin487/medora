package com.medora.eval.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 10 W4 — 录制文件的读写。
 *
 * <p>格式:JSON(便于 diff/grep 检查),pretty-print 便于人工 review。
 */
public class LlmRecorderStore {

    private final ObjectMapper mapper;
    private final Path recordingFile;

    public LlmRecorderStore(Path recordingFile) {
        this.recordingFile = recordingFile;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public LlmCallRecord load() throws IOException {
        if (!Files.isRegularFile(recordingFile)) {
            return new LlmCallRecord();
        }
        return mapper.readValue(recordingFile.toFile(), LlmCallRecord.class);
    }

    public void save(LlmCallRecord record) throws IOException {
        if (recordingFile.getParent() != null) {
            Files.createDirectories(recordingFile.getParent());
        }
        mapper.writeValue(recordingFile.toFile(), record);
    }

    public Path recordingFile() {
        return recordingFile;
    }
}
