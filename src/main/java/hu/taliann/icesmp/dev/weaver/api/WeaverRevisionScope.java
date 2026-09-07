package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.subject.SubjectKeyCodec;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import java.util.*;

/** An action declares its revision dependencies; stable subject identity and value schemas are always fenced. */
public record WeaverRevisionScope(int schemaVersion, Set<String> fields) {
    public WeaverRevisionScope {
        fields = Set.copyOf(fields); fields.forEach(WeaverIds::descriptor);
        if (schemaVersion < 1 || schemaVersion > 1000 || fields.size() > 64 || fields.isEmpty() && schemaVersion != 1) throw new IllegalArgumentException("Invalid revision scope");
    }
    public static WeaverRevisionScope full() { return new WeaverRevisionScope(1, Set.of()); }
    public SubjectSnapshot apply(final SubjectSnapshot snapshot) {
        if (fields.isEmpty()) return snapshot;
        try {
            final var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(CanonicalValueBytes.encode(Map.of("revision-scope", schemaVersion, "subject", SubjectKeyCodec.payload(snapshot.ref()))));
            for (final String field : fields.stream().sorted().toList()) {
                final WeaverValue value = snapshot.facts().get(field);
                if (value == null) throw new WeaverDomainRejection("REVISION_FIELD_UNAVAILABLE");
                digest.update(CanonicalValueBytes.encode(Map.of("field", field, "type", value.type().canonical())));
                digest.update(CanonicalValueBytes.encode(value.payload()));
            }
            return new SubjectSnapshot(snapshot.ref(), snapshot.capturedAt(), HexFormat.of().formatHex(digest.digest()), snapshot.facts());
        } catch (final java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
