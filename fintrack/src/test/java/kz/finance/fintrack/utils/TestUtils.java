package kz.finance.fintrack.utils;

import java.lang.reflect.Field;

public final class TestUtils {
    private TestUtils() {}

    public static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
