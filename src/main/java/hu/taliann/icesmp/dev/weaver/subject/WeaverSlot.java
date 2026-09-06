package hu.taliann.icesmp.dev.weaver.subject;

public record WeaverSlot(Kind kind, int index) {
    public enum Kind { INVENTORY, MAIN_HAND, OFF_HAND, CURSOR, HELMET, CHESTPLATE, LEGGINGS, BOOTS }
    public WeaverSlot {
        java.util.Objects.requireNonNull(kind);
        if (kind == Kind.INVENTORY ? index < 0 || index > 35 : index != -1) throw new IllegalArgumentException("Invalid Weaver slot");
    }
    public static WeaverSlot named(final Kind kind) { return new WeaverSlot(kind, -1); }
}
