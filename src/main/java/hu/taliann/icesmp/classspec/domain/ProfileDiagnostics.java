package hu.taliann.icesmp.classspec.domain;

/** Durable diagnostics. Quarantine evidence remains outside the canonical profile file. */
public record ProfileDiagnostics(String quarantineEvidenceId, String quarantineReason,
                                 String recoveryAuditId, String sessionBlockReason) {
    private static final int MAX_TEXT = 512;
    public ProfileDiagnostics {
        quarantineEvidenceId = clean(quarantineEvidenceId);
        quarantineReason = clean(quarantineReason);
        recoveryAuditId = clean(recoveryAuditId);
        sessionBlockReason = clean(sessionBlockReason);
    }
    public static ProfileDiagnostics none() { return new ProfileDiagnostics("", "", "", ""); }
    public boolean sessionBlocked() { return !sessionBlockReason.isEmpty(); }
    public boolean hasQuarantineEvidence() {
        return !quarantineEvidenceId.isEmpty() || !quarantineReason.isEmpty();
    }
    public ProfileDiagnostics withRecoveryAudit(final String auditId) {
        return new ProfileDiagnostics("", "", auditId, "");
    }
    public ProfileDiagnostics withSessionBlock(final String reason) {
        return new ProfileDiagnostics(quarantineEvidenceId, quarantineReason, recoveryAuditId, reason);
    }
    private static String clean(final String value) {
        final String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > MAX_TEXT)
            throw new IllegalArgumentException("Profile diagnostic exceeds " + MAX_TEXT + " characters");
        return cleaned;
    }
}
