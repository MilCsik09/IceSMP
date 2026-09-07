package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.OperationRecoveryPayload;
import hu.taliann.icesmp.dev.weaver.subject.SubjectKeyCodec;
import java.util.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;

/**
 * Decodes the bounded durable envelope without executing provider or live catalog code.
 * Unknown value schemas remain opaque history. Admission and consumers validate current semantics.
 * Unknown journal fields, lossy numbers and arbitrary object deserialization are still rejected.
 */
public final class WeaverJournalCodec {
    public WeaverJournalCodec() { }
    /** Source compatibility for the original codec wiring; registry availability is not storage validity. */
    public WeaverJournalCodec(final WeaverTypeRegistry types) { Objects.requireNonNull(types); }
    public Map<String, Object> encodeState(final WeaverJournalState state) {
        final Map<String, Object> operations = new TreeMap<>();
        state.operations().forEach((id, record) -> operations.put(id.toString(), operation(record)));
        final WeaverEffectCodec effects = new WeaverEffectCodec(this);
        final Map<String, Object> intents = new TreeMap<>(), projections = new TreeMap<>(), influences = new TreeMap<>(), deltas = new TreeMap<>();
        state.intents().forEach((id, value) -> intents.put(id.toString(), effects.intent(value)));
        state.projections().forEach((id, value) -> projections.put(id.toString(), effects.projection(value)));
        state.influences().forEach((id, value) -> influences.put(id.toString(), effects.influence(value)));
        state.effectDeltas().forEach((id, value) -> deltas.put(id.toString(), effects.delta(value)));
        return Map.of("schema-version", 3, "revision", state.revision(), "operations", operations, "projection-sequence", state.projectionSequence(),
                "intents", intents, "projections", projections, "influences", influences, "effect-deltas", deltas);
    }
    public WeaverJournalState decodeState(final Map<String, Object> data) {
        final long version = number(data, "schema-version");
        if (version == 1) keys(data, "schema-version", "revision", "operations");
        else if (version == 2) keys(data, "schema-version", "revision", "operations", "projection-sequence", "intents", "projections", "influences");
        else if (version == 3) keys(data, "schema-version", "revision", "operations", "projection-sequence", "intents", "projections", "influences", "effect-deltas");
        else throw new IllegalArgumentException("Unknown journal schema");
        final Map<UUID, WeaverOperationRecord> operations = new HashMap<>(); final Map<UUID, WeaverReceipt> receipts = new HashMap<>();
        final Map<String, Object> values = map(data.get("operations"));
        if (values.size() > WeaverJournalState.MAX_OPERATIONS) throw new IllegalArgumentException("Journal capacity exceeded");
        values.forEach((id, value) -> {
            final WeaverOperationRecord record = operation(map(value), version);
            if (!id.equals(record.operationId().toString()) || operations.put(record.operationId(), record) != null) throw new IllegalArgumentException("Duplicate operation identity");
            record.receipt().ifPresent(receipt -> { if (receipts.put(receipt.receiptId(), receipt) != null) throw new IllegalArgumentException("Duplicate receipt identity"); });
        });
        final Map<UUID, WeaverEffectIntent> intents = new HashMap<>();
        final Map<UUID, WeaverProjection> projections = new HashMap<>(); final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>();
        if (version == 1) {
            for (final WeaverOperationRecord operation : operations.values()) {
                if (operation.request().integrityMode() != IntegrityMode.SANDBOX || operation.status() == OperationStatus.ABORTED) continue;
                if (operation.request().lifetime() != Lifetime.ONE_SHOT) throw new IllegalArgumentException("Legacy persistent/session operation requires explicit recovery migration");
                final WeaverInfluenceTarget target = WeaverInfluenceTarget.subject(operation.subject());
                if (operation.receipt().isEmpty() && (operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.NEEDS_REVIEW)) {
                    intents.put(operation.operationId(), new WeaverEffectIntent(Set.of(target)));
                }
                if (operation.receipt().isPresent()) {
                    final WeaverReceipt receipt = operation.receipt().get();
                    final DeveloperInfluence origin = new DeveloperInfluence(operation.operationId(), IntegrityMode.SANDBOX, operation.request().actionId(), operation.actorId(), receipt.createdAt());
                    final UUID id = UUID.nameUUIDFromBytes(("weaver-schema1:" + operation.operationId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    final WeaverInfluenceRecord applied = WeaverInfluenceRecord.applied(origin, target, operation.request().lifetime() != Lifetime.ONE_SHOT && operation.status() != OperationStatus.COMPENSATED);
                    influences.put(id, new WeaverInfluenceRecord(id, origin, target, applied.active(), target.monotonic() ? 0 : Math.addExact(operation.updatedAt(), PlayerQuarantine.MINIMUM_TAIL_MILLIS)));
                }
            }
        } else {
            final WeaverEffectCodec effects = new WeaverEffectCodec(this);
            final Map<String, Object> intentRows = map(data.get("intents")), projectionRows = map(data.get("projections")), influenceRows = map(data.get("influences"));
            if (intentRows.size() > WeaverJournalState.MAX_OPERATIONS || projectionRows.size() > 1280 || influenceRows.size() > WeaverJournalState.MAX_INFLUENCES) throw new IllegalArgumentException("Effect capacity exceeded");
            intentRows.forEach((id, value) -> { final UUID decoded = uuid(Map.of("id", id), "id"); intents.put(decoded, effects.intent(value)); });
            projectionRows.forEach((id, value) -> { final WeaverProjection decoded = effects.projection(map(value)); if (!id.equals(decoded.projectionId().toString())) throw new IllegalArgumentException("Projection key mismatch"); projections.put(decoded.projectionId(), decoded); });
            influenceRows.forEach((id, value) -> { final WeaverInfluenceRecord decoded = effects.influence(map(value)); if (!id.equals(decoded.id().toString())) throw new IllegalArgumentException("Influence key mismatch"); influences.put(decoded.id(), decoded); });
        }
        final Map<UUID, WeaverEffectDelta> deltas = new HashMap<>();
        if (version >= 3) {
            final Map<String, Object> encoded = map(data.get("effect-deltas")); if (encoded.size() > WeaverJournalState.MAX_RECEIPTS) throw new IllegalArgumentException("Effect delta cap");
            final WeaverEffectCodec effects = new WeaverEffectCodec(this);
            encoded.forEach((id, value) -> deltas.put(uuid(Map.of("id", id), "id"), effects.delta(map(value))));
        } else operations.forEach((id, operation) -> { if (operation.receipt().isPresent()) deltas.put(id, WeaverEffectDelta.unavailable()); });
        return new WeaverJournalState(number(data, "revision"), operations, receipts, version == 1 ? 0 : number(data, "projection-sequence"), intents, projections, influences, deltas);
    }
    public Map<String, Object> encodeAudit(final Map<String, WeaverAuditEntry> audit) {
        final Map<String, Object> entries = new TreeMap<>();
        audit.forEach((id, entry) -> entries.put(id, Map.of("operation", entry.operationId().toString(), "actor", entry.actorId().toString(),
                "provider", entry.providerId(), "action", entry.actionId(), "mode", entry.integrityMode().name(), "outcome", entry.outcome().name(), "created", entry.createdAt())));
        return Map.of("schema-version", 1, "entries", entries);
    }
    public Map<String, WeaverAuditEntry> decodeAudit(final Map<String, Object> data) {
        keys(data, "schema-version", "entries"); schema(data);
        final Map<String, Object> entries = map(data.get("entries")); final Map<String, WeaverAuditEntry> result = new HashMap<>();
        if (entries.size() > 10_000) throw new IllegalArgumentException("Audit capacity exceeded");
        entries.forEach((id, value) -> {
            final Map<String, Object> row = map(value); keys(row, "operation", "actor", "provider", "action", "mode", "outcome", "created");
            final WeaverAuditEntry entry = new WeaverAuditEntry(uuid(row, "operation"), uuid(row, "actor"), text(row, "provider"), text(row, "action"),
                    IntegrityMode.valueOf(text(row, "mode")), AuditOutcome.valueOf(text(row, "outcome")), number(row, "created"));
            if (!id.equals(entry.key())) throw new IllegalArgumentException("Audit identity mismatch"); result.put(id, entry);
        });
        return Map.copyOf(result);
    }
    private Map<String, Object> operation(final WeaverOperationRecord record) {
        final Map<String, Object> result = new TreeMap<>();
        result.put("id", record.operationId().toString()); result.put("actor", record.actorId().toString()); result.put("provider", record.providerId());
        result.put("request", request(record.request())); result.put("subject", SubjectKeyCodec.encode(record.subject()));
        result.put("before", record.beforeFingerprint()); result.put("after", record.afterFingerprint().orElse(""));
        result.put("recovery", Map.of("schema", record.recoveryPayload().schemaVersion(), "fields", record.recoveryPayload().fields()));
        result.put("status", record.status().name()); result.put("revision", record.revision()); result.put("prepared", record.preparedAt());
        result.put("updated", record.updatedAt()); result.put("receipt", record.receipt().map(this::receipt).orElseGet(Map::of)); result.put("pending-audit", record.pendingAudit());
        result.put("undo-claim", record.undoClaim().map(claim -> Map.<String, Object>of("receipt", claim.receiptId().toString(), "revision", claim.operationRevision(), "expected", claim.expectedFingerprint())).orElseGet(Map::of));
        return Map.copyOf(result);
    }
    private WeaverOperationRecord operation(final Map<String, Object> row, final long version) {
        final Map<String, Object> fields = new HashMap<>(row); if (version >= 3) fields.remove("undo-claim");
        keys(fields, "id", "actor", "provider", "request", "subject", "before", "after", "recovery", "status", "revision", "prepared", "updated", "receipt", "pending-audit");
        final Map<String, Object> undo = version >= 3 ? map(row.get("undo-claim")) : Map.of();
        if (!undo.isEmpty()) keys(undo, "receipt", "revision", "expected");
        final Map<String, Object> recovery = map(row.get("recovery")); keys(recovery, "schema", "fields");
        final Map<String, Object> receipt = map(row.get("receipt")); final String after = text(row, "after");
        return new WeaverOperationRecord(uuid(row, "id"), uuid(row, "actor"), text(row, "provider"), request(map(row.get("request"))),
                SubjectKeyCodec.decode(text(row, "subject")), text(row, "before"), after.isEmpty() ? Optional.empty() : Optional.of(after),
                new OperationRecoveryPayload(Math.toIntExact(number(recovery, "schema")), map(recovery.get("fields"))), OperationStatus.valueOf(text(row, "status")),
                number(row, "revision"), number(row, "prepared"), number(row, "updated"), receipt.isEmpty() ? Optional.empty() : Optional.of(receipt(receipt)), bool(row, "pending-audit"),
                undo.isEmpty() ? Optional.empty() : Optional.of(new WeaverUndoClaim(uuid(undo, "receipt"), number(undo, "revision"), text(undo, "expected"))));
    }
    private Map<String, Object> request(final ActionRequest request) {
        return Map.of("action", request.actionId(), "parameters", values(request.parameters()), "lifetime", request.lifetime().name(), "mode", request.integrityMode().name());
    }
    private ActionRequest request(final Map<String, Object> row) {
        keys(row, "action", "parameters", "lifetime", "mode");
        return new ActionRequest(text(row, "action"), readValues(map(row.get("parameters"))), Lifetime.valueOf(text(row, "lifetime")), IntegrityMode.valueOf(text(row, "mode")));
    }
    private Map<String, Object> receipt(final WeaverReceipt receipt) {
        final Map<String, Object> row = new TreeMap<>();
        row.put("id", receipt.receiptId().toString()); row.put("operation", receipt.operationId().toString()); row.put("provider", receipt.providerId()); row.put("action", receipt.actionId());
        row.put("subject", SubjectKeyCodec.encode(receipt.subject())); row.put("risk", receipt.risk().name()); row.put("lifetime", receipt.lifetime().name()); row.put("mode", receipt.integrityMode().name());
        row.put("before-fingerprint", receipt.beforeFingerprint()); row.put("after-fingerprint", receipt.afterFingerprint()); row.put("before", values(receipt.before())); row.put("after", values(receipt.after()));
        row.put("undo", receipt.undo().map(undo -> Map.<String, Object>of("action", undo.actionId(), "expected", undo.expectedCurrentFingerprint(), "parameters", values(undo.parameters()))).orElseGet(Map::of));
        row.put("created", receipt.createdAt()); row.put("status", receipt.status().name()); return Map.copyOf(row);
    }
    private WeaverReceipt receipt(final Map<String, Object> row) {
        keys(row, "id", "operation", "provider", "action", "subject", "risk", "lifetime", "mode", "before-fingerprint", "after-fingerprint", "before", "after", "undo", "created", "status");
        final Map<String, Object> undo = map(row.get("undo"));
        if (!undo.isEmpty()) keys(undo, "action", "expected", "parameters");
        return new WeaverReceipt(uuid(row, "id"), uuid(row, "operation"), text(row, "provider"), text(row, "action"), SubjectKeyCodec.decode(text(row, "subject")),
                RiskLevel.valueOf(text(row, "risk")), Lifetime.valueOf(text(row, "lifetime")), IntegrityMode.valueOf(text(row, "mode")), text(row, "before-fingerprint"), text(row, "after-fingerprint"),
                readValues(map(row.get("before"))), readValues(map(row.get("after"))), undo.isEmpty() ? Optional.empty() : Optional.of(new UndoSpec(text(undo, "action"), text(undo, "expected"), readValues(map(undo.get("parameters"))))),
                number(row, "created"), ReceiptStatus.valueOf(text(row, "status")));
    }
    Map<String, Object> values(final Map<String, WeaverValue> values) {
        final Map<String, Object> result = new TreeMap<>();
        values.forEach((id, value) -> { result.put(id, Map.of("type", value.type().canonical(), "payload", value.payload(), "provider", value.sourceProvider(),
                "facet", value.sourceFacet(), "capabilities", value.sourceCapabilities().stream().sorted().toList(), "captured", value.capturedAt())); });
        return Map.copyOf(result);
    }
    Map<String, WeaverValue> readValues(final Map<String, Object> values) {
        if (values.size() > 128) throw new IllegalArgumentException("Typed fact cap");
        final Map<String, WeaverValue> result = new TreeMap<>();
        values.forEach((id, item) -> {
            final Map<String, Object> value = map(item); keys(value, "type", "payload", "provider", "facet", "capabilities", "captured");
            final Set<String> capabilities = new HashSet<>();
            if (!(value.get("capabilities") instanceof List<?> list) || list.size() > 32) throw new IllegalArgumentException("Capability encoding");
            for (final Object capability : list) if (!(capability instanceof String text) || !capabilities.add(text)) throw new IllegalArgumentException("Capability encoding");
            final WeaverValue decoded = new WeaverValue(WeaverTypeId.parse(text(value, "type")), map(value.get("payload")), text(value, "provider"), text(value, "facet"), capabilities, number(value, "captured"));
            result.put(id, decoded);
        });
        return Map.copyOf(result);
    }
    public static Map<String, Object> map(final Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected mapping");
        final Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> { if (!(key instanceof String text) || item == null) throw new IllegalArgumentException("Invalid mapping field"); result.put(text, item); });
        return Collections.unmodifiableMap(result);
    }
    private static void schema(final Map<String, Object> data) { if (number(data, "schema-version") != 1) throw new IllegalArgumentException("Unknown journal schema"); }
    static void keys(final Map<String, Object> data, final String... keys) {
        if (!data.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Unknown or missing journal fields");
    }
    static String text(final Map<String, Object> data, final String key) {
        if (!(data.get(key) instanceof String value)) throw new IllegalArgumentException("Expected text"); return value;
    }
    static long number(final Map<String, Object> data, final String key) {
        final Object value = data.get(key);
        if (value instanceof Integer integer) return integer.longValue();
        if (value instanceof Long number) return number;
        throw new IllegalArgumentException("Expected exact integer");
    }
    static UUID uuid(final Map<String, Object> data, final String key) {
        final String text = text(data, key); final UUID value = UUID.fromString(text);
        if (!value.toString().equals(text)) throw new IllegalArgumentException("Noncanonical UUID"); return value;
    }
    static boolean bool(final Map<String, Object> data, final String key) {
        if (!(data.get(key) instanceof Boolean value)) throw new IllegalArgumentException("Expected boolean"); return value;
    }
}
