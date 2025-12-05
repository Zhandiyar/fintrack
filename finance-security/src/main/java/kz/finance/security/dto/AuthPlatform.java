package kz.finance.security.dto;

import java.util.Locale;

public enum AuthPlatform {
    ANDROID,
    IOS,
    WEB;

    public static AuthPlatform from(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }

        String p = raw.toLowerCase(Locale.ROOT);

        if (p.contains("android")) return ANDROID;
        if (p.contains("ios")) return IOS;

        return WEB;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}