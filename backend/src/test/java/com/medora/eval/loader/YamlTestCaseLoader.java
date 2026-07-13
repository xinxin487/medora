package com.medora.eval.loader;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.medora.eval.domain.TestCase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Phase 10 — 从 YAML 文件加载测试用例。
 *
 * <p>支持两种输入:
 * <ul>
 *   <li>单个 .yaml/.yml 文件:可以是单个 case 或 case 列表</li>
 *   <li>目录:递归读取所有 .yaml/.yml 文件</li>
 * </ul>
 *
 * <p>YAML schema 详见 {@code src/test/resources/eval/cases/*.yaml}。
 */
public class YamlTestCaseLoader {

    private final ObjectMapper yamlMapper;

    public YamlTestCaseLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public List<TestCase> load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        File file = path.toFile();
        if (file.isDirectory()) {
            return loadDirectory(file);
        }
        if (!file.isFile()) {
            throw new IOException("test case path is neither file nor directory: " + path);
        }
        return loadSingleFile(file);
    }

    private List<TestCase> loadDirectory(File dir) throws IOException {
        List<TestCase> all = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            List<Path> files = walk
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .toList();
            for (Path f : files) {
                all.addAll(loadSingleFile(f.toFile()));
            }
        }
        return all;
    }

    private List<TestCase> loadSingleFile(File file) throws IOException {
        // 多文档 YAML(用 --- 分隔)逐个反序列化为 TestCase;
        // 单文档 YAML 也能用同一个 MappingIterator 走通(只产出一个元素)。
        List<TestCase> cases = new ArrayList<>();
        try (MappingIterator<TestCase> it = yamlMapper.readerFor(TestCase.class).readValues(file)) {
            while (it.hasNextValue()) {
                TestCase tc = it.nextValue();
                if (tc != null) {
                    cases.add(tc);
                }
            }
        } catch (IOException ex) {
            throw new IOException("failed to parse YAML file: " + file.getAbsolutePath() + " — " + ex.getMessage(), ex);
        }
        return cases;
    }
}
