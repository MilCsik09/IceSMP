package hu.taliann.icesmp.pve;

/** Pure source-composition rule shared by runtime reconciliation and regressions. */
public final class DaylightProtectionPolicy {
    private DaylightProtectionPolicy() { }

    public static boolean protectedNow(final boolean authored, final boolean territory,
                                       final boolean event) {
        return authored || territory || event;
    }

    public static boolean shouldBurn(final boolean baseline, final boolean authored,
                                     final boolean territory, final boolean event) {
        return baseline && !protectedNow(authored, territory, event);
    }
}
