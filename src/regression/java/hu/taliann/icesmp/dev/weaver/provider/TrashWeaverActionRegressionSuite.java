package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.trash.TrashDeveloperReceipt;
import java.util.*;
import static hu.taliann.icesmp.dev.weaver.provider.TrashWeaverActions.*;

/** Detached adapter contracts; physical native item/WAL observations run in the Paper/Folia probe. */
public final class TrashWeaverActionRegressionSuite {
    private static int assertions;
    private static final UUID ACTOR = HiddenDevAuthority.PRIMARY_DEVELOPER;
    private static final ItemSlotRef BEFORE = subject("a".repeat(64));
    private static ItemSlotRef subject(String fingerprint) {
        return new ItemSlotRef(ACTOR, new WeaverSlot(WeaverSlot.Kind.INVENTORY, 3), Optional.empty(), Optional.empty(), OptionalLong.empty(), fingerprint);
    }
    public static void main(String[] args) {
        final var types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        for (final var entry : KINDS.entrySet()) {
            final var descriptor = descriptor(entry.getKey(), entry.getValue());
            check(descriptor.requiresJournal() && descriptor.revisionScope().equals(SCOPE)
                    && descriptor.integrityModes().equals(Set.of(IntegrityMode.SANDBOX))
                    && descriptor.lifetimes().equals(Set.of(Lifetime.ONE_SHOT))
                    && descriptor.subjects().equals(Set.of(WeaverSubjectKind.ITEM_SLOT)), "native item action weakened its authority/scope");
            final var before = Map.of(INVENTORY, scalar("c".repeat(64)));
            final var after = Map.of(INVENTORY, scalar("d".repeat(64)), AFTER_SUBJECT, subjectValue(subject("b".repeat(64))));
            final UUID operation = UUID.randomUUID();
            final var forward = receipt(operation, entry.getKey(), BEFORE, before, after, false, 1);
            forward.after().values().forEach(types::validate);
            check(WeaverUndoSubject.resolve(forward).equals(subject("b".repeat(64)))
                    && forward.afterFingerprint().equals(fingerprint(WeaverUndoSubject.resolve(forward), after))
                    && !forward.afterFingerprint().equals(fingerprint(BEFORE, after)), "Undo reused the stale original item fingerprint");
            check(forward.undo().isPresent() == descriptor.undoable(), "forward native receipt lost declared Undo");
            if (entry.getValue() == TrashDeveloperReceipt.Kind.INDIVIDUALIZE) check(descriptor.irreversibleReason().isPresent(), "identity allocation hides permanence");
            else {
                final var inverse = receipt(UUID.randomUUID(), entry.getKey(), BEFORE, before, after, true, 1);
                check(inverse.undo().isEmpty(), "inverse promised another native reversal");
            }
            final var payload = new HashMap<String, Object>(Map.of("kind", "trash_item", "operation", operation.toString(),
                    "instance", UUID.randomUUID().toString(), "source", 3, "before", "c".repeat(64),
                    "native_kind", entry.getValue().name(), "reverses", ""));
            check(recoveryFields(operation(operation, entry.getKey(), payload, Optional.empty())).get("source").equals(3L), "canonical recovery plan refused");
            for (final var invalid : List.<Map.Entry<String, Object>>of(Map.entry("kind", "unrelated"), Map.entry("operation", UUID.randomUUID().toString()),
                    Map.entry("source", -1), Map.entry("source", 41), Map.entry("source", 3.5), Map.entry("source", 4294967299L),
                    Map.entry("before", "stale"), Map.entry("instance", "invalid"), Map.entry("extra", "ignored"),
                    Map.entry("native_kind", "REVERT"), Map.entry("reverses", UUID.randomUUID().toString()))) {
                final var changed = new HashMap<>(payload); changed.put(invalid.getKey(), invalid.getValue());
                rejects(() -> recoveryFields(operation(operation, entry.getKey(), changed, Optional.empty())));
            }
            if (entry.getValue() != TrashDeveloperReceipt.Kind.INDIVIDUALIZE) {
                final UUID original = UUID.randomUUID(); final var inverse = new HashMap<>(payload);
                inverse.put("native_kind", "REVERT"); inverse.put("reverses", original.toString());
                final var claim = new WeaverUndoClaim(original, 1, fingerprint(BEFORE, before));
                check(recoveryFields(operation(operation, entry.getKey(), inverse, Optional.of(claim))).get("reverses").equals(original.toString()), "exact inverse recovery claim refused");
            }
        }
        check(requireSubject(BEFORE, ACTOR).equals(BEFORE), "primary personal item refused");
        rejects(() -> requireSubject(BEFORE, UUID.randomUUID()));
        rejects(() -> requireSubject(new PlayerRef(ACTOR), ACTOR));
        rejects(() -> requireSubject(new ItemSlotRef(UUID.randomUUID(), BEFORE.slot(), Optional.empty(), Optional.empty(), OptionalLong.empty(), BEFORE.fingerprint()), ACTOR));
        rejects(() -> requireSubject(new ItemSlotRef(ACTOR, WeaverSlot.named(WeaverSlot.Kind.CURSOR), Optional.empty(), Optional.empty(), OptionalLong.empty(), BEFORE.fingerprint()), ACTOR));
        for (int slot = 0; slot < 36; slot++) check(index(new WeaverSlot(WeaverSlot.Kind.INVENTORY, slot), 7) == slot, "inventory index rebind");
        for (int held = 0; held < 9; held++) check(index(WeaverSlot.named(WeaverSlot.Kind.MAIN_HAND), held) == held, "held slot rebind");
        final var equipment = Map.of(WeaverSlot.Kind.BOOTS, 36, WeaverSlot.Kind.LEGGINGS, 37, WeaverSlot.Kind.CHESTPLATE, 38,
                WeaverSlot.Kind.HELMET, 39, WeaverSlot.Kind.OFF_HAND, 40);
        equipment.forEach((kind, expected) -> check(index(WeaverSlot.named(kind), 7) == expected, "equipment slot rebind"));
        rejects(() -> index(WeaverSlot.named(WeaverSlot.Kind.CURSOR), 7)); rejects(() -> index(BEFORE.slot(), 9));
        rejects(() -> inventoryFacts(new org.bukkit.inventory.ItemStack[40]));
        check(inventoryFacts(new org.bukkit.inventory.ItemStack[41]).get(INVENTORY).payload().get("value").toString().length() == 64, "unbounded inventory digest");
        System.out.println("Trash WW action contracts passed. assertions=" + assertions);
    }
    private static WeaverOperationRecord operation(UUID id, String action, Map<String, Object> payload, Optional<WeaverUndoClaim> claim) {
        return new WeaverOperationRecord(id, ACTOR, "trash", new ActionRequest(action, Map.of(), Lifetime.ONE_SHOT, IntegrityMode.SANDBOX), BEFORE,
                fingerprint(BEFORE, Map.of(INVENTORY, scalar("c".repeat(64)))), Optional.empty(), new OperationRecoveryPayload(1, payload),
                OperationStatus.PREPARED, 0, 1, 1, Optional.empty(), false, claim);
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("invalid native contract accepted"); }
        catch (IllegalArgumentException | WeaverDomainRejection expected) { assertions++; }
    }
}
