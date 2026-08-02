package com.xm2nd.tlmmimotts.selftest;

/**
 * 无第三方测试依赖的断言工具。
 */
public final class SelftestUtil {
    private SelftestUtil() {
    }

    /** 断言条件成立，否则抛 AssertionError */
    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void checkEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "（期望: " + expected + "，实际: " + actual + "）");
        }
    }

    public static void checkContains(String text, String expectedPart, String message) {
        if (text == null || !text.contains(expectedPart)) {
            throw new AssertionError(message + "（未包含: " + expectedPart + "，实际: " + text + "）");
        }
    }

    public static void checkNotContains(String text, String forbidden, String message) {
        if (text != null && text.contains(forbidden)) {
            throw new AssertionError(message + "（不应包含: " + forbidden + "）");
        }
    }

    public static void checkThrows(Class<? extends Throwable> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return;
            }
            throw new AssertionError(message + "（抛出的异常类型不符，期望 " + expected.getSimpleName()
                    + "，实际 " + t.getClass().getSimpleName() + ": " + t.getMessage() + "）", t);
        }
        throw new AssertionError(message + "（未抛出 " + expected.getSimpleName() + "）");
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
