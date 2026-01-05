package kz.finance.fintrack.utils;

import java.time.Instant;

public final class AppleTime {
    private AppleTime() {}

    public static Instant msToInstant(Long ms) {
        if (ms == null || ms <= 0) return null;
        return Instant.ofEpochMilli(ms);
    }
}
