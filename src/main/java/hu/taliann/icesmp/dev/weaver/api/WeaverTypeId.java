package hu.taliann.icesmp.dev.weaver.api;

public record WeaverTypeId(String namespace, String path, int schemaVersion) {
    public WeaverTypeId {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]{1,48}")
                || path == null || !path.matches("[a-z0-9_./-]{1,96}") || schemaVersion < 1 || schemaVersion > 1000) {
            throw new IllegalArgumentException("Invalid Weaver type identity");
        }
    }
    public String canonical() { return namespace + ":" + path + "@" + schemaVersion; }
    public static WeaverTypeId parse(final String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+@[1-9][0-9]{0,3}")) {
            throw new IllegalArgumentException("Invalid canonical Weaver type");
        }
        final int colon = value.indexOf(':');
        final int at = value.lastIndexOf('@');
        return new WeaverTypeId(value.substring(0, colon), value.substring(colon + 1, at), Integer.parseInt(value.substring(at + 1)));
    }
}
