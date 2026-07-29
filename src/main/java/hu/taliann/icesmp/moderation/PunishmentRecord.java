package hu.taliann.icesmp.moderation;

import java.util.Objects;
import java.util.UUID;

/** Immutable authoritative punishment ledger record. */
public record PunishmentRecord(
        UUID id,
        PunishmentType type,
        UUID targetId,
        String targetName,
        UUID administratorId,
        String administratorName,
        String reason,
        long createdAtMillis,
        Long expiresAtMillis,
        PunishmentState state,
        UUID revokedById,
        String revokedByName,
        Long revokedAtMillis,
        String revocationReason,
        boolean automaticExpiration,
        UUID linkedPunishmentId
) {

    public PunishmentRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(state, "state");
        targetName = normalizeName(targetName, "targetName");
        administratorName = normalizeName(administratorName, "administratorName");
        reason = normalizeText(reason);
        revokedByName = normalizeOptional(revokedByName);
        revocationReason = normalizeText(revocationReason);
        if (createdAtMillis <= 0L) {
            throw new IllegalArgumentException("createdAtMillis must be positive");
        }
        if (type.isTemporary()) {
            if (expiresAtMillis == null || expiresAtMillis <= createdAtMillis) {
                throw new IllegalArgumentException("temporary punishment requires a future expiry");
            }
        } else if (expiresAtMillis != null) {
            throw new IllegalArgumentException("non-temporary punishment cannot have an expiry");
        }
        if (type.isRestriction()) {
            if (state == PunishmentState.RECORDED) {
                throw new IllegalArgumentException("restriction cannot be RECORDED");
            }
        } else if (state != PunishmentState.RECORDED) {
            throw new IllegalArgumentException("non-restriction must be RECORDED");
        }
        if (state == PunishmentState.REVOKED) {
            if (revokedAtMillis == null || revokedAtMillis < createdAtMillis || revokedByName == null) {
                throw new IllegalArgumentException("revoked punishment requires revocation audit fields");
            }
            if (automaticExpiration) {
                throw new IllegalArgumentException("manual revocation cannot be automatic expiration");
            }
            if (type.isTemporary() && expiresAtMillis != null && revokedAtMillis >= expiresAtMillis) {
                throw new IllegalArgumentException("expired temporary punishment cannot be manually revoked");
            }
        } else if (state == PunishmentState.EXPIRED) {
            if (!type.isTemporary() || !automaticExpiration || revokedAtMillis == null
                    || expiresAtMillis == null || revokedAtMillis < expiresAtMillis) {
                throw new IllegalArgumentException("expired punishment requires a due automatic expiry audit");
            }
        } else if (revokedById != null || revokedByName != null || revokedAtMillis != null
                || !revocationReason.isBlank() || automaticExpiration) {
            throw new IllegalArgumentException("active/recorded entry cannot carry revocation fields");
        }
        if (type.isRevocationAction()) {
            if (linkedPunishmentId == null) {
                throw new IllegalArgumentException("unmute/unban requires linked punishment id");
            }
        } else if (linkedPunishmentId != null) {
            throw new IllegalArgumentException("only unmute/unban may link another punishment");
        }
    }

    public boolean isLogicallyActive(final long nowMillis) {
        return state == PunishmentState.ACTIVE
                && (expiresAtMillis == null || expiresAtMillis > nowMillis);
    }

    public PunishmentRecord revoked(final UUID adminId, final String adminName, final long atMillis,
                                    final String revocationReason) {
        if (state != PunishmentState.ACTIVE) {
            throw new IllegalStateException("only active punishment can be revoked");
        }
        return new PunishmentRecord(id, type, targetId, targetName, administratorId,
                administratorName, reason, createdAtMillis, expiresAtMillis, PunishmentState.REVOKED,
                adminId, normalizeName(adminName, "revokedByName"), atMillis,
                normalizeText(revocationReason), false, null);
    }

    public PunishmentRecord expired(final long atMillis) {
        if (state != PunishmentState.ACTIVE || !type.isTemporary() || expiresAtMillis == null
                || atMillis < expiresAtMillis) {
            throw new IllegalStateException("punishment is not due for expiration");
        }
        return new PunishmentRecord(id, type, targetId, targetName, administratorId,
                administratorName, reason, createdAtMillis, expiresAtMillis, PunishmentState.EXPIRED,
                null, "SYSTEM", atMillis, "Automatikus lejárat", true, null);
    }

    private static String normalizeName(final String value, final String field) {
        final String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException(field + " must be 1..64 characters");
        }
        return normalized;
    }

    private static String normalizeOptional(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("optional name must be 1..64 characters when present");
        }
        return normalized;
    }

    private static String normalizeText(final String value) {
        final String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("text field exceeds 512 characters");
        }
        return normalized;
    }
}
