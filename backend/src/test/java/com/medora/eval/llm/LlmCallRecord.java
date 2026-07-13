package com.medora.eval.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Phase 10 W4 — LLM 调用录制集合。
 *
 * <p>每次 LLM 调用按 (systemPrompt + userPrompt) 的 SHA-256 作为 key,
 * 这样回放时只要 prompt 不变就能命中,稳定且可复现。
 *
 * <p>录制文件格式为 JSON:
 * <pre>
 * { "records": [
 *   { "key": "a1b2...", "systemPrompt": "...", "userPrompt": "...", "response": "..." },
 *   ...
 * ]}
 * </pre>
 */
public class LlmCallRecord {

    private List<Entry> records = new ArrayList<>();

    public List<Entry> getRecords() { return records; }
    public void setRecords(List<Entry> records) {
        this.records = records == null ? new ArrayList<>() : records;
    }

    public int size() { return records.size(); }

    public Entry find(String key) {
        for (Entry e : records) {
            if (Objects.equals(e.key, key)) return e;
        }
        return null;
    }

    public void add(Entry entry) {
        if (entry == null || entry.key == null) return;
        // 同 key 已存在则覆盖(避免重复录制时膨胀)
        for (int i = 0; i < records.size(); i++) {
            if (Objects.equals(records.get(i).key, entry.key)) {
                records.set(i, entry);
                return;
            }
        }
        records.add(entry);
    }

    public static class Entry {
        public String key;
        public String systemPrompt;
        public String userPrompt;
        public String response;

        public Entry() {}

        public Entry(String key, String systemPrompt, String userPrompt, String response) {
            this.key = key;
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            this.response = response;
        }
    }
}
