package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.UUID;

/** Audit stores bounded identities and outcomes, never provider payload or hidden inspection details. */
public record WeaverAuditEntry(UUID operationId, UUID actorId, String providerId, String actionId,
                               IntegrityMode integrityMode, AuditOutcome outcome, long createdAt) {
    public WeaverAuditEntry {
        java.util.Objects.requireNonNull(operationId); WeaverIds.descriptor(providerId); WeaverIds.descriptor(actionId);
        java.util.Objects.requireNonNull(integrityMode); java.util.Objects.requireNonNull(outcome);
        if (!HiddenDevAuthority.isDeveloper(actorId) || createdAt < 0) throw new IllegalArgumentException("Invalid audit identity");
    }
    public String key() { return operationId + ":" + outcome.name(); }
}
