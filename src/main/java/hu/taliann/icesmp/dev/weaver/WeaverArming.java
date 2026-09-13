package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.ActionDescriptor;
import hu.taliann.icesmp.dev.weaver.api.IntegrityMode;
import hu.taliann.icesmp.dev.weaver.api.RiskLevel;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class WeaverArming {
    public enum ArmingCapability { DESTRUCTIVE, LIVE_GM, CANONICAL }
    private record Grant(UUID session, long expiresAt) {}
    private final Map<ArmingCapability, Grant> grants = new EnumMap<>(ArmingCapability.class);
    private final LongSupplier monotonicMillis;
    public WeaverArming(final LongSupplier monotonicMillis) { this.monotonicMillis = java.util.Objects.requireNonNull(monotonicMillis); }
    public synchronized void grant(final WeaverAuthorityToken authority, final Set<ArmingCapability> capabilities, final long ttlMillis) {
        authority.requireValid();
        if (ttlMillis < 1 || ttlMillis > 30_000 || capabilities.isEmpty()) throw new IllegalArgumentException("Invalid arming TTL/capabilities");
        final long expires = Math.addExact(monotonicMillis.getAsLong(), ttlMillis);
        capabilities.forEach(capability -> grants.put(capability, new Grant(authority.session(), expires)));
    }
    public synchronized boolean consume(final UUID session, final Set<ArmingCapability> required) {
        final long now = monotonicMillis.getAsLong();
        final boolean valid = required.stream().allMatch(capability -> {
            final Grant grant = grants.get(capability);
            return grant != null && grant.session().equals(session) && now < grant.expiresAt();
        });
        grants.clear();
        return valid;
    }
    public synchronized Set<ArmingCapability> active(final UUID session) {
        final long now = monotonicMillis.getAsLong();
        grants.values().removeIf(grant -> !grant.session().equals(session) || now >= grant.expiresAt());
        return Set.copyOf(grants.keySet());
    }
    public synchronized void clear() { grants.clear(); }
    public static Set<ArmingCapability> required(final ActionDescriptor action, final IntegrityMode mode) {
        final Set<ArmingCapability> required = EnumSet.noneOf(ArmingCapability.class);
        if (mode == IntegrityMode.LIVE_GM && action.risk().ordinal() >= RiskLevel.MUTATING.ordinal()) required.add(ArmingCapability.LIVE_GM);
        if (action.risk() == RiskLevel.DESTRUCTIVE) required.add(ArmingCapability.DESTRUCTIVE);
        if (action.risk() == RiskLevel.CANONICAL) { required.add(ArmingCapability.CANONICAL); required.add(ArmingCapability.LIVE_GM); }
        return Set.copyOf(required);
    }
}
