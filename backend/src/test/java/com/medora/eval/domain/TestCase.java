package com.medora.eval.domain;

import java.util.List;
import java.util.Map;

/**
 * Phase 10 — 单个评测 case 的完整定义。
 *
 * <p>一个 case 描述:
 * <ul>
 *   <li>用户消息 + 上下文预设(用户 ID、模拟当前时间、对话历史)</li>
 *   <li>期望行为(路由、工具调用、回复匹配器、安全结果)</li>
 *   <li>元数据(分类、严重级别、来源、标签)</li>
 * </ul>
 *
 * <p>case 一旦入 git,id 必须稳定,用作 baseline 对比的 key。
 */
public record TestCase(
        String id,
        Category category,
        String userMessage,
        ContextPreset context,
        Expectations expectations,
        CaseMetadata metadata
) {
    public TestCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("TestCase.id must not be blank");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("TestCase.userMessage must not be blank: id=" + id);
        }
        if (category == null) {
            category = Category.ROUTING;
        }
        if (context == null) {
            context = ContextPreset.EMPTY;
        }
        if (expectations == null) {
            expectations = Expectations.EMPTY;
        }
        if (metadata == null) {
            metadata = CaseMetadata.SEED;
        }
    }

    public enum Category {
        ROUTING,
        TRIAGE,
        BOOKING,
        KNOWLEDGE,
        SAFETY
    }

    public record ContextPreset(
            Long userId,
            String mockedNowIso,
            List<Map<String, String>> conversationHistory
    ) {
        public static final ContextPreset EMPTY = new ContextPreset(
                1L, null, List.of());

        public ContextPreset {
            if (userId == null) {
                userId = 1L;
            }
            if (conversationHistory == null) {
                conversationHistory = List.of();
            }
        }
    }

    public record Expectations(
            RouteExpectation route,
            List<ToolCallExpectation> tools,
            List<ReplyMatcher> replyMatchers,
            String safety
    ) {
        public static final Expectations EMPTY = new Expectations(
                null, List.of(), List.of(), null);

        public Expectations {
            if (tools == null) {
                tools = List.of();
            }
            if (replyMatchers == null) {
                replyMatchers = List.of();
            }
        }
    }

    public record RouteExpectation(
            List<String> expectedChain
    ) {
        public RouteExpectation {
            if (expectedChain == null) {
                expectedChain = List.of();
            }
        }
    }

    public record ToolCallExpectation(
            String toolName,
            String expectStatus,
            Map<String, Object> paramMatchers
    ) {
        public ToolCallExpectation {
            if (paramMatchers == null) {
                paramMatchers = Map.of();
            }
        }
    }

    public record ReplyMatcher(
            String type,
            String value,
            double weight
    ) {
        public ReplyMatcher {
            if (weight <= 0) {
                weight = 1.0;
            }
            if (type == null || type.isBlank()) {
                type = "contains";
            }
        }
    }

    public record CaseMetadata(
            String severity,
            List<String> tags,
            String source
    ) {
        public static final CaseMetadata SEED = new CaseMetadata("normal", List.of(), "SEED");

        public CaseMetadata {
            if (severity == null || severity.isBlank()) {
                severity = "normal";
            }
            if (tags == null) {
                tags = List.of();
            }
            if (source == null || source.isBlank()) {
                source = "SEED";
            }
        }
    }
}
