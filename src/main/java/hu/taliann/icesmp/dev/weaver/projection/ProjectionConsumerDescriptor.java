package hu.taliann.icesmp.dev.weaver.projection;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind;
import java.util.*;

/** Every effective field names its runtime reader and the canonical identities it cannot replace. */
public record ProjectionConsumerDescriptor(String id, String providerId, String runtimeReader, Set<String> actions,
                                           Set<WeaverSubjectKind> subjects, Map<String, WeaverTypeId> fields, Set<String> canonicalOnlyConsumers) {
    public ProjectionConsumerDescriptor {
        WeaverIds.descriptor(id); WeaverIds.descriptor(providerId); actions = Set.copyOf(actions); subjects = Set.copyOf(subjects);
        fields = Map.copyOf(fields); canonicalOnlyConsumers = Set.copyOf(canonicalOnlyConsumers);
        if (!id.startsWith(providerId + ".") || runtimeReader == null || !runtimeReader.matches("[a-zA-Z0-9_.$#]{3,256}")
                || actions.isEmpty() || actions.size() > 128 || subjects.isEmpty() || fields.isEmpty() || fields.size() > 128
                || canonicalOnlyConsumers.isEmpty() || canonicalOnlyConsumers.size() > 64) throw new IllegalArgumentException("Projection consumer lacks a bounded runtime contract");
        for (final String action : actions) { WeaverIds.descriptor(action); if (!action.startsWith(providerId + ".")) throw new IllegalArgumentException("Foreign projection action"); }
        for (final String field : fields.keySet()) { WeaverIds.descriptor(field); if (!field.startsWith(providerId + ".")) throw new IllegalArgumentException("Foreign projection field"); }
        canonicalOnlyConsumers.forEach(WeaverIds::descriptor);
    }
}
