package hu.taliann.icesmp.classspec.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Complete deterministic set of reasons why a loadout is sealed. */
public final class SealReason {
    private final Map<SealCause, String> causes;
    private final String detail;

    public SealReason(final SealCause cause, final String gateId, final String detail) {
        this(Map.of(Objects.requireNonNull(cause, "cause"), clean(gateId)), detail);
    }
    public SealReason(final Map<SealCause, String> causes, final String detail) {
        Objects.requireNonNull(causes, "causes");
        if (causes.isEmpty()) throw new IllegalArgumentException("A seal requires at least one cause");
        final EnumMap<SealCause, String> copy = new EnumMap<>(SealCause.class);
        for (final Map.Entry<SealCause, String> entry : causes.entrySet()) {
            final SealCause cause = Objects.requireNonNull(entry.getKey(), "seal cause");
            final String gateId = clean(entry.getValue());
            if (cause.gateRestorable() && gateId.isEmpty())
                throw new IllegalArgumentException("A restorable seal requires a stable gate id");
            copy.put(cause, gateId);
        }
        this.causes = Collections.unmodifiableMap(copy);
        this.detail = clean(detail);
    }
    public Set<SealCause> causes() { return causes.keySet(); }
    public Map<SealCause, String> gateIds() { return causes; }
    public SealCause cause() { return causes.keySet().iterator().next(); }
    public String gateId() {
        final StringJoiner joiner = new StringJoiner(",");
        causes.forEach((cause, id) -> joiner.add(cause.name() + "=" + id));
        return joiner.toString();
    }
    public String gateId(final SealCause cause) { return causes.get(cause); }
    public String detail() { return detail; }
    public boolean gateRestorableOnly() { return causes.keySet().stream().allMatch(SealCause::gateRestorable); }
    public boolean contains(final SealCause cause) { return causes.containsKey(cause); }
    private static String clean(final String value) {
        final String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > 512) throw new IllegalArgumentException("Seal detail exceeds 512 characters");
        return cleaned;
    }
    @Override public boolean equals(final Object other) {
        return this == other || other instanceof SealReason reason
                && causes.equals(reason.causes) && detail.equals(reason.detail);
    }
    @Override public int hashCode() { return Objects.hash(causes, detail); }
    @Override public String toString() { return "SealReason{" + gateId() + (detail.isEmpty()?"":", "+detail) + "}"; }
}
