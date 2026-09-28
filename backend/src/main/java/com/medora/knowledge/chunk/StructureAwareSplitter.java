package com.medora.knowledge.chunk;

import com.medora.knowledge.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Structure-first parent/child splitter with semantic child boundaries and deterministic fallback. */
@Component
public class StructureAwareSplitter {

    private static final Logger log = LoggerFactory.getLogger(StructureAwareSplitter.class);

    private final SemanticBoundarySplitter semanticBoundarySplitter;
    private final RagProperties properties;

    public StructureAwareSplitter(SemanticBoundarySplitter semanticBoundarySplitter, RagProperties properties) {
        this.semanticBoundarySplitter = semanticBoundarySplitter;
        this.properties = properties;
    }

    public List<DocumentChunk> split(String text) {
        String source = normalize(text);
        if (source.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> output = new ArrayList<>();
        int parentOrdinal = 0;
        for (Section section : sections(source)) {
            for (List<String> parentUnits : parentGroups(section.units())) {
                String parentContent = join(parentUnits);
                output.add(chunk(output.size(), section.path(), parentContent, "PARENT", parentOrdinal));
                for (String child : childContents(parentUnits)) {
                    output.add(chunk(output.size(), section.path(), child, "CHILD", parentOrdinal));
                }
                parentOrdinal++;
            }
        }
        return List.copyOf(output);
    }

    private List<String> childContents(List<String> units) {
        if (units.isEmpty()) {
            return List.of();
        }
        try {
            return semanticGroups(units);
        } catch (RuntimeException ex) {
            log.warn("Semantic chunk boundary detection failed; using deterministic structure fallback. reason={}",
                    ex.getMessage());
            return sizeBoundGroups(units);
        }
    }

    private List<String> semanticGroups(List<String> units) {
        int boundary = semanticBoundarySplitter.preferredBoundaryForUnits(
                units, properties.getSemanticBoundaryThreshold());
        if (boundary > 0 && boundary < units.size()
                && tokens(join(units.subList(0, boundary))) >= Math.max(1, properties.getChildMinTokens())
                && tokens(join(units.subList(boundary, units.size()))) >= Math.max(1, properties.getChildMinTokens())) {
            List<String> result = new ArrayList<>();
            result.addAll(sizeBoundGroups(units.subList(0, boundary)));
            result.addAll(sizeBoundGroups(units.subList(boundary, units.size())));
            return List.copyOf(result);
        }
        return sizeBoundGroups(units);
    }

    private List<String> sizeBoundGroups(List<String> units) {
        int maxTokens = Math.max(1, properties.getChildMaxTokens());
        int overlapTokens = Math.max(0, properties.getChildOverlapTokens());
        List<String> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String unit : units) {
            if (!current.isEmpty() && tokens(joinWith(current, unit)) > maxTokens) {
                String completed = join(current);
                result.add(completed);
                int availableOverlap = Math.max(0, maxTokens - tokens(unit));
                String overlap = overlapTail(completed, Math.min(overlapTokens, availableOverlap));
                current = overlap.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(overlap));
                if (!current.isEmpty() && tokens(joinWith(current, unit)) > maxTokens) {
                    current.clear();
                }
            }
            current.add(unit);
        }
        if (!current.isEmpty()) {
            result.add(join(current));
        }
        return List.copyOf(result);
    }

    private List<List<String>> parentGroups(List<String> units) {
        int maxTokens = Math.max(1, properties.getParentMaxTokens());
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String unit : units) {
            if (!current.isEmpty() && tokens(joinWith(current, unit)) > maxTokens) {
                groups.add(List.copyOf(current));
                current.clear();
            }
            current.add(unit);
        }
        if (!current.isEmpty()) {
            groups.add(List.copyOf(current));
        }
        return groups;
    }

    private List<Section> sections(String source) {
        List<Section> result = new ArrayList<>();
        String path = "全文";
        StringBuilder content = new StringBuilder();
        for (String line : source.split("\\R")) {
            if (line.startsWith("#")) {
                if (!content.isEmpty()) {
                    result.add(new Section(path, naturalUnits(content.toString())));
                    content.setLength(0);
                }
                path = line.replaceFirst("^#+\\s*", "").trim();
                continue;
            }
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(line);
        }
        if (!content.isEmpty()) {
            result.add(new Section(path, naturalUnits(content.toString())));
        }
        return result.stream().filter(section -> !section.units().isEmpty()).toList();
    }

    private List<String> naturalUnits(String content) {
        List<String> units = new ArrayList<>();
        for (String block : content.split("\\R{2,}")) {
            String value = block.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (containsFaq(value)) {
                units.addAll(faqPairs(value));
            } else if (tokens(value) > Math.max(1, properties.getChildMaxTokens())
                    && !isIndivisibleRecord(value)) {
                units.addAll(sentences(value));
            } else {
                units.add(value);
            }
        }
        return units;
    }

    private List<String> faqPairs(String value) {
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (isQuestion(trimmed) && !current.isEmpty()) {
                pairs.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(trimmed);
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString().trim());
        }
        return pairs;
    }

    private List<String> sentences(String value) {
        List<String> result = new ArrayList<>();
        for (String sentence : value.split("(?<=[。！？.!?])\\s*")) {
            if (!sentence.isBlank()) {
                result.addAll(hardSplit(sentence.trim()));
            }
        }
        return result.isEmpty() ? List.of(value) : result;
    }

    private List<String> hardSplit(String value) {
        int maxCodePoints = Math.max(2, properties.getChildMaxTokens() * 2);
        int total = value.codePointCount(0, value.length());
        if (total <= maxCodePoints) {
            return List.of(value);
        }
        List<String> result = new ArrayList<>();
        int offset = 0;
        while (offset < value.length()) {
            int remaining = value.codePointCount(offset, value.length());
            int take = Math.min(maxCodePoints, remaining);
            int end = value.offsetByCodePoints(offset, take);
            result.add(value.substring(offset, end));
            offset = end;
        }
        return result;
    }

    private boolean containsFaq(String value) {
        return value.lines().anyMatch(this::isQuestion);
    }

    private boolean isQuestion(String value) {
        return value.startsWith("Q:") || value.startsWith("Q：")
                || value.startsWith("问:") || value.startsWith("问：");
    }

    private boolean isIndivisibleRecord(String value) {
        List<String> lines = value.lines().filter(line -> !line.isBlank()).toList();
        return !lines.isEmpty() && (lines.stream().allMatch(line -> line.trim().startsWith("|"))
                || lines.stream().allMatch(line -> line.trim().matches("(?:[-*+]\\s+|\\d+[.\u3001)\uff09]\\s*).+")));
    }

    private String overlapTail(String content, int overlapTokens) {
        if (overlapTokens == 0) {
            return "";
        }
        int codePoints = content.codePointCount(0, content.length());
        int requestedCodePoints = Math.min(codePoints, overlapTokens * 2);
        int start = content.offsetByCodePoints(0, codePoints - requestedCodePoints);
        return content.substring(start).trim();
    }

    private String joinWith(List<String> values, String extra) {
        List<String> combined = new ArrayList<>(values);
        combined.add(extra);
        return join(combined);
    }

    private String join(List<String> values) {
        return String.join("\n\n", values).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\0", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }

    private DocumentChunk chunk(int index, String path, String content, String type, int parentOrdinal) {
        return new DocumentChunk(index, path, content, type, parentOrdinal, tokens(content), hash(content));
    }

    private int tokens(String value) {
        return Math.max(1, (int) Math.ceil(value.codePointCount(0, value.length()) / 2.0));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Section(String path, List<String> units) { }
}
