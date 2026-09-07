package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.area.WeaverAreaRecoveryEvidence;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;

/** Projection targets and capacity are acknowledged before stages; legacy data cannot invent child revisions. */
public final class WeaverOperationScope {
    public static final String RESERVATIONS = "weaver.projection_reservations";
    private WeaverOperationScope() { }
    public static Map<SubjectRef, String> fingerprints(final SubjectRef subject, final String before, final OperationRecoveryPayload payload) {
        final Map<SubjectRef, String> result = new HashMap<>(); result.put(subject, before);
        if (payload.fields().containsKey(WeaverAreaRecoveryEvidence.KEY)) {
            if (!(subject instanceof AreaRef area)) throw new IllegalArgumentException("AREA scope on another subject");
            result.putAll(WeaverAreaRecoveryEvidence.decode(area, payload.fields().get(WeaverAreaRecoveryEvidence.KEY)).beforeFingerprints());
        }
        return Map.copyOf(result);
    }
    public static boolean contains(final WeaverOperationRecord operation, final SubjectRef target) {
        return fingerprints(operation.subject(), operation.beforeFingerprint(), operation.recoveryPayload()).containsKey(target);
    }
    public static boolean matches(final WeaverOperationRecord operation, final SubjectRef target, final String before) {
        return before.equals(fingerprints(operation.subject(), operation.beforeFingerprint(), operation.recoveryPayload()).get(target));
    }
    public static Map<SubjectRef, Integer> reservations(final WeaverOperationRecord operation) {
        return reservations(operation.subject(), operation.beforeFingerprint(), operation.request().lifetime(), operation.recoveryPayload());
    }
    public static Map<SubjectRef, Integer> reservations(final SubjectRef subject, final String before, final Lifetime lifetime, final OperationRecoveryPayload payload) {
        if (!payload.fields().containsKey(RESERVATIONS)) return lifetime == Lifetime.ONE_SHOT ? Map.of() : Map.of(subject, 1);
        final Map<String, Object> data = WeaverJournalCodec.map(payload.fields().get(RESERVATIONS));
        if (!data.keySet().equals(Set.of("schema", "targets")) || integer(data.get("schema")) != 1
                || !(data.get("targets") instanceof List<?> rows) || rows.size() > 128) throw new IllegalArgumentException("Projection reservation schema");
        final Map<SubjectRef, Integer> result = new HashMap<>();
        for (final Object value : rows) {
            final var row = WeaverJournalCodec.map(value);
            if (!row.keySet().equals(Set.of("ref", "count")) || result.putIfAbsent(SubjectKeyCodec.decodePayload(WeaverJournalCodec.map(row.get("ref"))), integer(row.get("count"))) != null) throw new IllegalArgumentException("Projection reservation duplicate/unknown target");
        }
        validate(result, fingerprints(subject, before, payload).keySet(), lifetime); return Map.copyOf(result);
    }
    public static OperationRecoveryPayload attach(final SubjectRef subject, final String before, final Lifetime lifetime, final OperationRecoveryPayload payload,
            final Optional<Map<SubjectRef, Integer>> requested) {
        if (payload.fields().containsKey(RESERVATIONS)) throw new IllegalArgumentException("Provider supplied reserved projection evidence");
        final Map<SubjectRef, Integer> defaults = new HashMap<>();
        if (lifetime != Lifetime.ONE_SHOT) {
            if (subject instanceof AreaRef area && payload.fields().containsKey(WeaverAreaRecoveryEvidence.KEY)) {
                final var selection = WeaverAreaRecoveryEvidence.decode(area, payload.fields().get(WeaverAreaRecoveryEvidence.KEY));
                if (!selection.beforeFingerprints().keySet().equals(Set.copyOf(selection.targets()))) throw new WeaverDomainRejection("AREA_REVISION_EVIDENCE_REQUIRED");
                selection.targets().forEach(target -> defaults.put(target, 1));
            } else defaults.put(subject, 1);
        }
        final Map<SubjectRef, Integer> selected = requested.orElse(defaults);
        validate(selected, fingerprints(subject, before, payload).keySet(), lifetime);
        final Map<String, Object> fields = new HashMap<>(payload.fields());
        fields.put(RESERVATIONS, Map.of("schema", 1, "targets", selected.entrySet().stream().sorted(Comparator.comparing(entry -> SubjectKeyCodec.encode(entry.getKey())))
                .map(entry -> Map.of("ref", SubjectKeyCodec.payload(entry.getKey()), "count", entry.getValue())).toList()));
        return new OperationRecoveryPayload(payload.schemaVersion(), fields);
    }
    public static WeaverEffectIntent intent(final WeaverOperationRecord operation, final WeaverEffectIntent supplied) {
        final Set<WeaverInfluenceTarget> targets = new HashSet<>(supplied.targets());
        reservations(operation).keySet().forEach(ref -> targets.add(WeaverInfluenceTarget.subject(ref)));
        if (operation.request().integrityMode() == IntegrityMode.SANDBOX) {
            targets.add(WeaverInfluenceTarget.subject(operation.subject()));
            fingerprints(operation.subject(), operation.beforeFingerprint(), operation.recoveryPayload()).keySet().stream()
                    .filter(ref -> ref instanceof EntityRef || ref instanceof PlayerRef).forEach(ref -> targets.add(WeaverInfluenceTarget.subject(ref)));
        }
        return new WeaverEffectIntent(targets);
    }
    private static void validate(final Map<SubjectRef, Integer> reservations, final Set<SubjectRef> scope, final Lifetime lifetime) {
        if (reservations.size() > 128 || !scope.containsAll(reservations.keySet()) || lifetime == Lifetime.ONE_SHOT && !reservations.isEmpty()
                || reservations.values().stream().anyMatch(count -> count == null || count < 1 || count > 32)
                || reservations.values().stream().mapToInt(Integer::intValue).sum() > 128) throw new IllegalArgumentException("Projection reservation bounds or foreign target");
    }
    private static int integer(final Object value) {
        if (!(value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException("Projection reservation integer"); return Math.toIntExact(((Number) value).longValue());
    }
}
