package hu.taliann.icesmp.classspec.domain;

/** Durable/reconstructed diagnostics shown by /spec info; never enables gameplay. */
public record ProfileDiagnostics(String quarantineReason, String sessionBlockReason) {

    public ProfileDiagnostics {
        quarantineReason = clean(quarantineReason);
        sessionBlockReason = clean(sessionBlockReason);
    }

    public static ProfileDiagnostics none() {
        return new ProfileDiagnostics("", "");
    }

    public boolean sessionBlocked() {
        return !sessionBlockReason.isEmpty();
    }

    private static String clean(final String value) {
        return value == null ? "" : value.trim();
    }
}
