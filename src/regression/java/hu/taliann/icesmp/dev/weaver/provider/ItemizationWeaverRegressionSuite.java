package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.itemization.ItemDeveloperMutation;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import static hu.taliann.icesmp.dev.weaver.provider.ItemizationWeaverActions.*;

/** Detached manifests, typed requests and persisted recovery contracts; no connected mutation claim. */
public final class ItemizationWeaverRegressionSuite {
    private static int assertions;
    private static final UUID ACTOR = HiddenDevAuthority.PRIMARY_DEVELOPER;
    private static final UUID ITEM = UUID.randomUUID();
    private static ItemSlotRef slotSubject(WeaverSlot slot) {
        return new ItemSlotRef(ACTOR, slot, Optional.of("fixture"), Optional.of(ITEM), OptionalLong.of(4), "a".repeat(64));
    }
    public static void main(String[] args) {
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        types.register(ScalarTypeCodec.reference(ItemizationWeaverProvider.RUNE, id -> id.equals("runa_fagy")));
        check(KINDS.size() == 10, "all ten declared native actions present");
        for (final var kind : ItemDeveloperMutation.Kind.values()) {
            final var descriptor = descriptor(kind);
            check(descriptor.requiresJournal() && descriptor.subjects().equals(Set.of(WeaverSubjectKind.ITEM_SLOT))
                    && descriptor.lifetimes().equals(Set.of(Lifetime.ONE_SHOT)) && descriptor.revisionScope().equals(SCOPE), "native action requires exact durable single-item scope");
            final boolean canonical = !kind.prototype() && kind != ItemDeveloperMutation.Kind.REFRESH_PRESENTATION;
            check(canonical ? descriptor.risk() == RiskLevel.CANONICAL && descriptor.rateCost() == 10
                    && descriptor.integrityModes().equals(Set.of(IntegrityMode.LIVE_GM))
                    : descriptor.risk() == RiskLevel.MUTATING, "canonical action retains canonical confirmation/cost");
            check(kind == ItemDeveloperMutation.Kind.REFRESH_PRESENTATION ? !descriptor.undoable() && descriptor.irreversibleReason().isPresent()
                    : descriptor.undoable() && descriptor.irreversibleReason().isEmpty(), "native compensation is declared for nine mutations; presentation remains explicit");
            final var parameters = new HashMap<String, WeaverValue>();
            if (kind.addRune()) parameters.put("rune", new WeaverValue(ItemizationWeaverProvider.RUNE, Map.of("id", "runa_fagy"), "item", ItemizationWeaverProvider.FACET, Set.of("item.rune"), 0));
            final var mode = descriptor.integrityModes().contains(IntegrityMode.SANDBOX) ? IntegrityMode.SANDBOX : IntegrityMode.LIVE_GM;
            final var request = new ActionRequest(descriptor.id(), parameters, Lifetime.ONE_SHOT, mode);
            check(request(descriptor, request, types).kind() == kind, "typed default request reaches intended native kind");
            final var unknown = new HashMap<>(parameters); unknown.put("unregistered", scalar("injection"));
            rejects(() -> request(descriptor, new ActionRequest(descriptor.id(), unknown, Lifetime.ONE_SHOT, mode), types));
            final var ref = slotSubject(new WeaverSlot(WeaverSlot.Kind.INVENTORY, 3));
            final UUID operation = UUID.randomUUID();
            final var payload = payload(operation, kind);
            final var pending = operation(operation, descriptor.id(), ref, mode, payload);
            check(recoveryFields(pending).get("native_kind").equals(kind.name()), "valid native recovery identity accepted");
            for (final var change : List.<Map.Entry<String, Object>>of(Map.entry("source", -1L), Map.entry("source", 0L), Map.entry("source", Long.MAX_VALUE),
                    Map.entry("source", 3.0D), Map.entry("target", 41L), Map.entry("operation", UUID.randomUUID().toString()),
                    Map.entry("native_kind", "WRONG"), Map.entry("before", "z".repeat(64)), Map.entry("result_item", "bad-uuid"), Map.entry("unexpected", 1))) {
                final var altered = new HashMap<>(payload); altered.put(change.getKey(), change.getValue());
                rejects(() -> recoveryFields(operation(operation, descriptor.id(), ref, mode, altered)));
            }
            if (kind != ItemDeveloperMutation.Kind.REFRESH_PRESENTATION) rejects(() -> recoveryFields(operation(operation, descriptor.id(), ref,
                    mode == IntegrityMode.SANDBOX ? IntegrityMode.LIVE_GM : IntegrityMode.SANDBOX, payload)));
            final var afterRef = new ItemSlotRef(ACTOR, ref.slot(), ref.logicalId(), ref.instanceId(), OptionalLong.of(5), "b".repeat(64));
            final var before = Map.of(INVENTORY, scalar("c".repeat(64)));
            final var after = Map.of(INVENTORY, scalar("d".repeat(64)), AFTER_SUBJECT,
                    new WeaverValue(WeaverUndoSubject.TYPE, SubjectKeyCodec.payload(afterRef), "item", ItemizationWeaverProvider.FACET, Set.of(WeaverUndoSubject.CAPABILITY), 0));
            final var receipt = receipt(operation, descriptor, request, ref, before, after, true, 101);
            check(receipt.receiptId().equals(operation) && receipt.subject().equals(ref) && receipt.undo().isPresent() == descriptor.undoable()
                    && WeaverUndoSubject.resolve(receipt).equals(afterRef) && receipt.afterFingerprint().equals(fingerprint(afterRef, after)), "actual resulting slot has its own fingerprint without rebinding original operation");
            final var legacyReceipt = receipt(operation, descriptor, request, ref, before, after, false, 101);
            check(legacyReceipt.undo().isEmpty(), "previous schema recovery does not invent a new Undo promise");
            final var modern = new HashMap<>(payload); modern.put("reverses", "");
            check(recoveryFields(versioned(pending, 2, modern, Optional.empty())).get("reverses").equals(""), "modern forward recovery accepted");
            if (descriptor.undoable()) {
                check(receipt.undo().orElseThrow().parameters().equals(request.parameters()), "Undo retains original typed parameters");
                final UUID original = UUID.randomUUID();
                modern.put("reverses", original.toString()); modern.put("source", 3L); modern.put("target", 3L); modern.put("result_item", ITEM.toString());
                final var claim = new WeaverUndoClaim(original, 2, pending.beforeFingerprint());
                check(recoveryFields(versioned(pending, 2, modern, Optional.of(claim))).get("reverses").equals(original.toString()), "compensation recovery bound to the durable Undo claim");
                rejects(() -> recoveryFields(versioned(pending, 2, modern, Optional.empty())));
                rejects(() -> recoveryFields(versioned(pending, 1, modern, Optional.of(claim))));
                rejects(() -> recoveryFields(versioned(pending, 2, modern, Optional.of(new WeaverUndoClaim(UUID.randomUUID(), 2, pending.beforeFingerprint())))));
                final var compensation = receipt(UUID.randomUUID(), descriptor, request, afterRef, after,
                        kind == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE ? Map.of(INVENTORY, scalar("e".repeat(64))) : after, false, 102);
                check(compensation.undo().isEmpty() && (kind != ItemDeveloperMutation.Kind.CLONE_PROTOTYPE || !compensation.after().containsKey(AFTER_SUBJECT)),
                        "compensation cannot be undone recursively; prototype deletion does not invent an empty item identity");
            }
        }
        final var ref = slotSubject(new WeaverSlot(WeaverSlot.Kind.INVENTORY, 3));
        check(subject(ref, ACTOR).equals(ref), "primary native slot available");
        rejects(() -> subject(ref, UUID.randomUUID()));
        rejects(() -> subject(new ItemSlotRef(UUID.randomUUID(), ref.slot(), ref.logicalId(), ref.instanceId(), ref.revision(), ref.fingerprint()), ACTOR));
        rejects(() -> subject(slotSubject(WeaverSlot.named(WeaverSlot.Kind.CURSOR)), ACTOR));
        final var empty = new org.bukkit.inventory.ItemStack[41];
        check(scalarValue(inventoryFacts(empty), INVENTORY).equals(recordedInventoryFingerprint(Collections.nCopies(41, "-"))), "persisted and live empty inventory fingerprint use the same scope");
        typedRerollRefusesCoercion(types);
        System.out.println("Itemization Weaver contracts passed. assertions=" + assertions);
    }
    private static void typedRerollRefusesCoercion(WeaverTypeRegistry types) {
        final var descriptor = descriptor(ItemDeveloperMutation.Kind.REROLL_CANONICAL);
        for (final Object quality : List.of(-0.1D, 1.1D, Double.NaN, "0.5", 1L)) {
            rejects(() -> request(descriptor, new ActionRequest(descriptor.id(), Map.of("minimum_quality",
                    new WeaverValue(WeaverTypeId.parse("weaver:double@1"), Map.of("value", quality), "item", ItemizationWeaverProvider.FACET, Set.of(), 0)),
                    Lifetime.ONE_SHOT, IntegrityMode.LIVE_GM), types));
        }
        final var add = descriptor(ItemDeveloperMutation.Kind.ADD_RUNE_PROTOTYPE);
        rejects(() -> request(add, new ActionRequest(add.id(), Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), types));
        rejects(() -> request(add, new ActionRequest(add.id(), Map.of("rune", new WeaverValue(ItemizationWeaverProvider.RUNE,
                Map.of("id", "runa_unknown"), "item", ItemizationWeaverProvider.FACET, Set.of("item.rune"), 0)), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), types));
    }
    private static Map<String, Object> payload(UUID operation, ItemDeveloperMutation.Kind kind) {
        return Map.of("kind", "itemization", "operation", operation.toString(), "native_kind", kind.name(),
                "result_item", (kind == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE ? UUID.randomUUID() : ITEM).toString(),
                "source", 3L, "target", kind == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE ? 4L : 3L, "before", "c".repeat(64));
    }
    private static WeaverOperationRecord operation(UUID operation, String action, ItemSlotRef ref, IntegrityMode mode, Map<String, Object> payload) {
        return new WeaverOperationRecord(operation, ACTOR, "item", new ActionRequest(action, Map.of(), Lifetime.ONE_SHOT, mode), ref,
                fingerprint(ref, Map.of(INVENTORY, scalar("c".repeat(64)))), Optional.empty(), new OperationRecoveryPayload(1, payload),
                OperationStatus.PREPARED, 0, 100, 100, Optional.empty(), false);
    }
    private static WeaverOperationRecord versioned(WeaverOperationRecord original, int schema, Map<String, Object> payload, Optional<WeaverUndoClaim> claim) {
        return new WeaverOperationRecord(original.operationId(), original.actorId(), original.providerId(), original.request(), original.subject(),
                original.beforeFingerprint(), original.afterFingerprint(), new OperationRecoveryPayload(schema, payload), original.status(), original.revision(),
                original.preparedAt(), original.updatedAt(), original.receipt(), original.pendingAudit(), claim);
    }
    private static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | WeaverDomainRejection expected) { assertions++; return; }
        throw new AssertionError("invalid Itemization WW contract accepted");
    }
}
