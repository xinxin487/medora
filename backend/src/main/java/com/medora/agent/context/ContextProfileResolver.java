package com.medora.agent.context;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ContextProfileResolver {
    public ContextProfile resolve(String message, String sceneType) {
        String text = (message == null ? "" : message).toLowerCase(Locale.ROOT);
        if (contains(text, "挂号", "预约", "号源", "医生", "门诊", "帮我约", "我想约", "能约", "约个", "约一下", "约诊", "约号")) return ContextProfile.BOOKING;
        if (contains(text, "怎么治", "规则", "知识", "注意事项", "能不能", "取消")) return ContextProfile.KNOWLEDGE;
        if (contains(text, "疼", "发烧", "咳嗽", "头痛", "不舒服", "症状")) return ContextProfile.TRIAGE;
        return ContextProfile.GENERAL;
    }
    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }
}
