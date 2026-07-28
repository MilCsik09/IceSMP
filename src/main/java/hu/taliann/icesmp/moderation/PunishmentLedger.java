package hu.taliann.icesmp.moderation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Dependency-free, strictly validated native punishment ledger. */
public final class PunishmentLedger {

    private final Map<UUID, PunishmentRecord> records = new LinkedHashMap<>();

    public PunishmentLedger() {
    }

    public PunishmentLedger(final Collection<PunishmentRecord> source) {
        Objects.requireNonNull(source, "source");
        for (final PunishmentRecord record : source) {
            if (records.putIfAbsent(record.id(), Objects.requireNonNull(record, "record")) != null) {
                throw new IllegalArgumentException("duplicate punishment id: " + record.id());
            }
        }
        validateInvariants();
    }

    public PunishmentLedger copy() {
        return new PunishmentLedger(records.values());
    }

    public List<PunishmentRecord> snapshot() {
        return records.values().stream()
                .sorted(Comparator.comparingLong(PunishmentRecord::createdAtMillis)
                        .thenComparing(record -> record.id().toString()))
                .toList();
    }

    public Optional<PunishmentRecord> active(final UUID targetId, final PunishmentType.Family family,
                                             final long nowMillis) {
        return records.values().stream()
                .filter(record -> record.targetId().equals(targetId))
                .filter(record -> record.type().family() == family)
                .filter(record -> record.isLogicallyActive(nowMillis))
                .max(Comparator.comparingLong(PunishmentRecord::createdAtMillis));
    }

    public List<PunishmentRecord> activeAll(final long nowMillis) {
        return records.values().stream()
                .filter(record -> record.isLogicallyActive(nowMillis))
                .sorted(Comparator.comparingLong(PunishmentRecord::createdAtMillis).reversed())
                .toList();
    }

    public List<PunishmentRecord> history(final UUID targetId) {
        return records.values().stream()
                .filter(record -> record.targetId().equals(targetId))
                .sorted(Comparator.comparingLong(PunishmentRecord::createdAtMillis).reversed()
                        .thenComparing(record -> record.id().toString()))
                .toList();
    }

    public long countIssued(final UUID targetId, final PunishmentType.Family family) {
        return records.values().stream()
                .filter(record -> record.targetId().equals(targetId))
                .filter(record -> record.type().family() == family)
                .count();
    }

    public PunishmentRecord issue(final PunishmentType type, final UUID targetId, final String targetName,
                                  final UUID administratorId, final String administratorName,
                                  final String reason, final long createdAtMillis, final Long expiresAtMillis) {
        Objects.requireNonNull(type, "type");
        // A logically expired restriction must be materialized as EXPIRED before a replacement
        // is inserted; otherwise a restart would see two ACTIVE records and correctly fail closed.
        expireDue(createdAtMillis);
        if (type.isRevocationAction()) {
            throw new IllegalArgumentException("use revoke() for unmute/unban");
        }
        if (type.isRestriction() && active(targetId, type.family(), createdAtMillis).isPresent()) {
            throw new IllegalStateException("target already has active " + type.family() + " punishment");
        }
        final PunishmentState state = type.isRestriction() ? PunishmentState.ACTIVE : PunishmentState.RECORDED;
        final PunishmentRecord record = new PunishmentRecord(UUID.randomUUID(), type, targetId, targetName,
                administratorId, administratorName, reason, createdAtMillis, expiresAtMillis, state,
                null, null, null, "", false, null);
        records.put(record.id(), record);
        return record;
    }

    public RevocationResult revoke(final UUID targetId, final PunishmentType.Family family,
                                   final UUID administratorId, final String administratorName,
                                   final String reason, final long atMillis) {
        if (family == PunishmentType.Family.NONE) {
            throw new IllegalArgumentException("cannot revoke NONE family");
        }
        final PunishmentRecord active = active(targetId, family, atMillis)
                .orElseThrow(() -> new IllegalStateException("no active " + family + " punishment"));
        final PunishmentRecord revoked = active.revoked(administratorId, administratorName, atMillis, reason);
        records.put(revoked.id(), revoked);
        final PunishmentType actionType = family == PunishmentType.Family.MUTE
                ? PunishmentType.UNMUTE : PunishmentType.UNBAN;
        final PunishmentRecord action = new PunishmentRecord(UUID.randomUUID(), actionType,
                active.targetId(), active.targetName(), administratorId, administratorName, reason,
                atMillis, null, PunishmentState.RECORDED, null, null, null, "", false, active.id());
        records.put(action.id(), action);
        return new RevocationResult(revoked, action);
    }

    public int expireDue(final long nowMillis) {
        final List<PunishmentRecord> due = new ArrayList<>();
        for (final PunishmentRecord record : records.values()) {
            if (record.state() == PunishmentState.ACTIVE && record.type().isTemporary()
                    && record.expiresAtMillis() != null && record.expiresAtMillis() <= nowMillis) {
                due.add(record);
            }
        }
        for (final PunishmentRecord record : due) {
            records.put(record.id(), record.expired(nowMillis));
        }
        return due.size();
    }

    private void validateInvariants() {
        final Map<String, UUID> activeFamilies = new LinkedHashMap<>();
        final Map<UUID, UUID> revocationActions = new LinkedHashMap<>();
        for (final PunishmentRecord record : records.values()) {
            if (record.state() == PunishmentState.ACTIVE) {
                final String key = record.targetId() + ":" + record.type().family();
                final UUID previous = activeFamilies.putIfAbsent(key, record.id());
                if (previous != null) {
                    throw new IllegalArgumentException("contradictory active punishments: " + previous
                            + " and " + record.id());
                }
            }
            if (record.type().isRevocationAction()) {
                final PunishmentRecord linked = records.get(record.linkedPunishmentId());
                final boolean familyMatches = linked != null
                        && ((record.type() == PunishmentType.UNMUTE
                        && linked.type().family() == PunishmentType.Family.MUTE)
                        || (record.type() == PunishmentType.UNBAN
                        && linked.type().family() == PunishmentType.Family.BAN));
                if (linked == null || linked.state() != PunishmentState.REVOKED
                        || !linked.targetId().equals(record.targetId()) || !familyMatches
                        || !Objects.equals(linked.revokedAtMillis(), record.createdAtMillis())
                        || !Objects.equals(linked.revokedById(), record.administratorId())
                        || !linked.revokedByName().equals(record.administratorName())
                        || !linked.revocationReason().equals(record.reason())) {
                    throw new IllegalArgumentException("invalid revocation link: " + record.id());
                }
                final UUID previous = revocationActions.putIfAbsent(linked.id(), record.id());
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate revocation actions: " + previous
                            + " and " + record.id());
                }
            }
        }
        for (final PunishmentRecord record : records.values()) {
            if (record.state() == PunishmentState.REVOKED && !revocationActions.containsKey(record.id())) {
                throw new IllegalArgumentException("revoked punishment has no audit action: " + record.id());
            }
        }
    }

    public record RevocationResult(PunishmentRecord revoked, PunishmentRecord action) {
    }
}
