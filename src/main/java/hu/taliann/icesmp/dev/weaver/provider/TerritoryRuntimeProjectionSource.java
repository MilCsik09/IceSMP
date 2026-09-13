package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.territory.*;
import hu.taliann.icesmp.territory.TerritoryProtectionPolicy.*;
import java.util.*;
import java.util.function.*;

/** Journal-backed read port. Protection policy, canonical storage and reward identity stay native. */
final class TerritoryRuntimeProjectionSource implements TerritoryRuleProjectionSource {
    static final String CONSUMER = "territory.protection", FIELD = "territory.rule_overlay";
    static final WeaverTypeId TYPE = WeaverTypeId.parse("icesmp:territory_rule_projection@1");
    private final WeaverProjectionSource projections;
    private final Function<String, Territory> canonical;
    private final LongSupplier clock;
    TerritoryRuntimeProjectionSource(WeaverProjectionSource projections, Function<String, Territory> canonical, LongSupplier clock) {
        this.projections = Objects.requireNonNull(projections); this.canonical = Objects.requireNonNull(canonical); this.clock = Objects.requireNonNull(clock);
    }
    List<WeaverProjection> active(SubjectRef subject) { return projections.active(CONSUMER, subject, clock.getAsLong()); }
    @Override public Overlay resolve(UUID worldId, String territoryId, Rule rule) {
        final var candidates = active(new WorldRef(worldId)).stream()
                .filter(p -> p.values().containsKey(FIELD)).sorted(Comparator.comparingLong(WeaverProjection::sequence).reversed()).toList();
        for (final var projection : candidates) {
            final var value = projection.values().get(FIELD); codec().validate(value.payload()).requireValid();
            if (!TYPE.equals(value.type())) throw new WeaverDomainRejection("PROJECTION_VALUE_INVALID");
            final var f = value.payload();
            if ("ALLOW".equals(f.get("overlay")) && projection.influence().mode() != IntegrityMode.LIVE_GM) throw new WeaverDomainRejection("ALLOW_REQUIRES_LIVE_GM");
            if (!territoryId.equals(f.get("territory")) || !rule.name().equals(f.get("rule"))) continue;
            final Territory zone = canonical.apply(territoryId);
            if (zone == null || !TerritoryRevision.fingerprint(zone).equals(f.get("canonical"))) {
                throw new WeaverDomainRejection("TERRITORY_PROJECTION_DRIFT");
            }
            return Overlay.valueOf((String) f.get("overlay"));
        }
        return Overlay.INHERIT;
    }
    static WeaverTypeCodec codec() {
        return new WeaverTypeCodec() {
            public WeaverTypeId type() { return TYPE; }
            public ValidationResult validate(Map<String, Object> f) {
                try {
                    if (!f.keySet().equals(Set.of("territory", "canonical", "rule", "overlay"))
                            || !(f.get("territory") instanceof String id) || id.isBlank() || id.length() > 256
                            || !(f.get("canonical") instanceof String revision) || !revision.matches("[0-9a-f]{64}")) return ValidationResult.rejected("INVALID_TERRITORY_PROJECTION");
                    Rule.valueOf((String) f.get("rule")); Overlay.valueOf((String) f.get("overlay"));
                    return ValidationResult.accepted();
                } catch (RuntimeException invalid) { return ValidationResult.rejected("INVALID_TERRITORY_PROJECTION"); }
            }
            public byte[] canonicalBytes(Map<String, Object> fields) { validate(fields).requireValid(); return CanonicalValueBytes.encode(fields); }
        };
    }
}
