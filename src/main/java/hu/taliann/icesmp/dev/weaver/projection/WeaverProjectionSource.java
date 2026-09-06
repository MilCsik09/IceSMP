package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.WeaverValue;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.*;
import java.util.function.Function;

/** Read-only effective view; canonical stores and reward identities have no write port here. */
public interface WeaverProjectionSource {
    List<WeaverProjection> active(String consumerId, SubjectRef subject, long now);
    default Optional<WeaverValue> scalar(final String consumerId, final SubjectRef subject, final String field, final long now) {
        return active(consumerId, subject, now).stream().filter(projection -> projection.values().containsKey(field))
                .max(Comparator.comparingLong(WeaverProjection::sequence)).map(projection -> projection.values().get(field));
    }
    default Map<String, WeaverValue> latestPerMember(final String consumerId, final SubjectRef subject, final Set<String> fields,
                                                    final Function<WeaverValue, String> memberIdentity, final long now) {
        final Map<String, WeaverValue> result = new LinkedHashMap<>();
        active(consumerId, subject, now).stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).forEach(projection -> {
            final Set<String> members = new HashSet<>();
            for (final String field : fields.stream().sorted().toList()) {
                final WeaverValue value = projection.values().get(field); if (value == null) continue;
                final String member = memberIdentity.apply(value);
                if (member == null || member.isBlank() || member.length() > 256 || !members.add(member)) throw new IllegalArgumentException("Ambiguous projection member identity");
                result.put(member, value);
            }
        }); return Map.copyOf(result);
    }
}
