package com.medora.eval.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.medora.eval.domain.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 10 W4 — 把对抗 case 写成多文档 YAML,供人工 review。
 *
 * <p>格式与 src/test/resources/eval/cases/*.yaml 一致,reviewer 编辑后直接放到 cases 目录就能用。
 */
public class GeneratedCaseYamlWriter {

    private final ObjectMapper yaml;

    public GeneratedCaseYamlWriter() {
        YAMLFactory f = new YAMLFactory()
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yaml = new ObjectMapper(f);
    }

    public void writeMultiDoc(Iterable<TestCase> cases, Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Phase 10 对抗 case — 由 LLM 生成,需人工 review 后入库\n");
        sb.append("# 字段说明:补充 expectations.route / tools 后即可放入 src/test/resources/eval/cases/\n\n");
        for (TestCase tc : cases) {
            sb.append("---\n");
            sb.append(yaml.writeValueAsString(tc));
            sb.append("\n");
        }
        Files.writeString(output, sb.toString());
    }
}
