package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.CanonicalValueBytes;
import java.util.*;

/** Effective ordering and exact values are fenced; unrelated subjects' sequence increments are irrelevant. */
public final class WeaverProjectionFingerprint {
    private WeaverProjectionFingerprint() { }
    public static String of(final Collection<WeaverProjection> projections) {
        if (projections.size() > 32) throw new IllegalArgumentException("Projection subject capacity");
        final List<Object> rows = new ArrayList<>();
        projections.stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).forEach(projection -> {
            final Map<String, Object> fields = new TreeMap<>();
            projection.values().forEach((key, value) -> fields.put(key, Map.of("type", value.type().canonical(), "payload", value.payload())));
            rows.add(Map.of("id", projection.projectionId().toString(), "action", projection.actionId(), "lifetime", projection.lifetime().name(),
                    "expires", projection.expiresAt().orElse(-1), "fields", fields));
        });
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CanonicalValueBytes.encode(Map.of("schema", 1, "projections", rows)))); }
        catch (final java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
