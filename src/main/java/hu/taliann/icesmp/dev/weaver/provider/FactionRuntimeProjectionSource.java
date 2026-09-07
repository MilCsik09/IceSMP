package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.factions.*;
import hu.taliann.icesmp.factions.FactionPassivePolicy.ContentContext;
import java.util.*;
import java.util.function.LongSupplier;

/** Read-only adapter over journal projections; the faction policy still owns every combat decision. */
final class FactionRuntimeProjectionSource implements FactionMembershipProjectionSource, FactionContextProjectionSource {
    static final String MEMBERSHIP_CONSUMER = "faction.passives", CONTEXT_CONSUMER = "faction.targeting";
    static final String MEMBERSHIP = "faction.membership_override", ADD = "faction.context_add", REMOVE = "faction.context_remove";
    private final WeaverProjectionSource source;
    private final LongSupplier clock;
    FactionRuntimeProjectionSource(WeaverProjectionSource source, LongSupplier clock) {
        this.source = Objects.requireNonNull(source); this.clock = Objects.requireNonNull(clock);
    }
    List<WeaverProjection> active(SubjectRef subject) {
        return source.active(subject instanceof PlayerRef ? MEMBERSHIP_CONSUMER : CONTEXT_CONSUMER, subject, clock.getAsLong());
    }
    @Override public FactionMembership resolve(UUID playerId, FactionMembership canonical) {
        return membership(canonical, active(new PlayerRef(playerId)));
    }
    @Override public Set<ContentContext> resolve(UUID entityId, Set<ContentContext> canonical) {
        return contexts(canonical, active(new EntityRef(entityId)));
    }
    static FactionMembership membership(FactionMembership canonical, List<WeaverProjection> projections) {
        return projections.stream().filter(p -> p.values().containsKey(MEMBERSHIP)).max(Comparator.comparingLong(WeaverProjection::sequence))
                .map(p -> FactionMembership.citizen(FactionType.valueOf(id(p.values().get(MEMBERSHIP)).toUpperCase(Locale.ROOT)))).orElse(canonical);
    }
    static Set<ContentContext> contexts(Set<ContentContext> canonical, List<WeaverProjection> projections) {
        final Set<ContentContext> result = new HashSet<>(canonical);
        for (final var projection : projections.stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).toList()) {
            for (final String field : List.of(ADD, REMOVE)) {
                final WeaverValue value = projection.values().get(field); if (value == null) continue;
                final ContentContext context = ContentContext.valueOf(id(value).toUpperCase(Locale.ROOT));
                if (!FactionContextProjectionSource.projectable().contains(context)) throw new WeaverDomainRejection("CANONICAL_CONTEXT_ONLY");
                if (field.equals(ADD)) result.add(context); else result.remove(context);
            }
        }
        return FactionContextProjectionSource.validate(canonical, result);
    }
    static String id(WeaverValue value) {
        if (!(value.payload().get("id") instanceof String id)) throw new WeaverDomainRejection("PROJECTION_VALUE_INVALID"); return id;
    }
    static String fingerprint(Map<String, Object> fields) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CanonicalValueBytes.encode(fields))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
