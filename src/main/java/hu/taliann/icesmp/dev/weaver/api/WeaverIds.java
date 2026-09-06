package hu.taliann.icesmp.dev.weaver.api;

public final class WeaverIds {
    private WeaverIds() {}
    public static String descriptor(final String value) {
        if (value == null || !value.matches("[a-z0-9_.-]{3,96}")) throw new IllegalArgumentException("Invalid Weaver descriptor id");
        return value;
    }
    public static String parameter(final String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid Weaver parameter id");
        return value;
    }
    public static String content(final String value) {
        if (value == null || !value.matches("[a-z0-9_.:/-]{1,128}")) throw new IllegalArgumentException("Invalid catalog content id");
        return value;
    }
}
