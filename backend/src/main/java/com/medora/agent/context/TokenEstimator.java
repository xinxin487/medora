package com.medora.agent.context;

public class TokenEstimator {
    /** A-stage estimate: Chinese-heavy text uses 2 code points/token; other text uses 4 chars/token. */
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        long codePoints = text.codePoints().count();
        long chinese = text.codePoints().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        return (int) Math.ceil((chinese * 2 >= codePoints ? codePoints / 2.0 : text.length() / 4.0));
    }
}
