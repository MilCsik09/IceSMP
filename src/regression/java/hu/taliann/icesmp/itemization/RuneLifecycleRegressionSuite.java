package hu.taliann.icesmp.itemization;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Behavioral rune socket, cursor atomicity, mutation lifecycle and recovery regression. */
public final class RuneLifecycleRegressionSuite {
    private static int assertions;

    private RuneLifecycleRegressionSuite() { }

    public static void main(final String[] args) {
        insertRemoveReplacePreserveCanonicalState();
        invalidTransitionsFailClosed();
        everyRuneActionUsesTheExactSnapshotRecoveryContract();
        cursorRehomeIsAllOrNothing();
        schedulerRejectionReleasesTransientLock();
        System.out.println("Rune lifecycle regression suite passed. assertions=" + assertions);
    }

    private static void insertRemoveReplacePreserveCanonicalState() {
        final ItemTemplate template = template();
        final ItemMutationService mutations = new ItemMutationService();
        final ItemInstance base = instance();

        final RuneMutationPolicy.Result inserted = RuneMutationPolicy.apply(base.runes(), 2,
                RuneMutationPolicy.Action.INSERT, -1, "runa_fagy");
        final UUID insertReceipt = UUID.fromString("00000000-0000-0000-0000-000000000801");
        final ItemInstance withFirst = mutations.changeRunes(template, base, insertReceipt,
                inserted.runes(), 10L);
        final ItemInstance withTwo = mutations.changeRunes(template, withFirst,
                UUID.fromString("00000000-0000-0000-0000-000000000802"),
                RuneMutationPolicy.apply(withFirst.runes(), 2, RuneMutationPolicy.Action.INSERT,
                        -1, "runa_vihar").runes(), 11L);
        check(withTwo.itemId().equals(base.itemId()) && withTwo.origin().equals(base.origin())
                        && withTwo.ascension().equals(base.ascension()),
                "insert preserves UUID, provenance and ascension");

        final RuneMutationPolicy.Result removed = RuneMutationPolicy.apply(withTwo.runes(), 2,
                RuneMutationPolicy.Action.REMOVE, 1, "");
        final ItemInstance afterRemove = mutations.changeRunes(template, withTwo,
                UUID.fromString("00000000-0000-0000-0000-000000000803"), removed.runes(), 12L);
        check(removed.removedRune().equals("runa_vihar")
                        && afterRemove.runes().equals(List.of("runa_fagy")),
                "selected remove destroys only the selected rune and preserves the other socket");

        final RuneMutationPolicy.Result replaced = RuneMutationPolicy.apply(afterRemove.runes(), 2,
                RuneMutationPolicy.Action.REPLACE, 0, "runa_arnyek");
        final UUID replaceReceipt = UUID.fromString("00000000-0000-0000-0000-000000000804");
        final ItemInstance afterReplace = mutations.changeRunes(template, afterRemove,
                replaceReceipt, replaced.runes(), 13L);
        check(replaced.removedRune().equals("runa_fagy")
                        && afterReplace.runes().equals(List.of("runa_arnyek"))
                        && afterReplace.itemId().equals(base.itemId()),
                "replace is one canonical transition with the same item UUID");
        final ItemInstance retried = mutations.changeRunes(template, afterReplace,
                replaceReceipt, List.of("runa_masolat"), 14L);
        check(retried.equals(afterReplace),
                "a duplicate operation receipt cannot replace or consume a rune twice");
        check(afterReplace.history().get(afterReplace.history().size() - 1).detail()
                        .equals("replace:runa_fagy->runa_arnyek"),
                "bounded history identifies the atomic replacement transition");
    }

    private static void invalidTransitionsFailClosed() {
        expectFailure(() -> RuneMutationPolicy.apply(List.of(), 0,
                RuneMutationPolicy.Action.INSERT, -1, "runa_fagy"),
                "insert rejects a template without sockets");
        expectFailure(() -> RuneMutationPolicy.apply(List.of("runa_fagy"), 2,
                RuneMutationPolicy.Action.REMOVE, 1, ""),
                "remove rejects an empty selected socket");
        expectFailure(() -> RuneMutationPolicy.apply(List.of("runa_fagy", "runa_vihar"), 2,
                RuneMutationPolicy.Action.REPLACE, 0, "runa_vihar"),
                "replace rejects a duplicate rune in the other socket");
        expectFailure(() -> RuneMutationPolicy.apply(List.of("runa_fagy"), 2,
                RuneMutationPolicy.Action.REPLACE, 0, "runa_fagy"),
                "replace rejects a no-op candidate");
        expectFailure(() -> RuneMutationPolicy.apply(List.of(), 2,
                        RuneMutationPolicy.Action.INSERT, 0, "runa_fagy", ArmorFamily.CLOTH,
                        (rune, family) -> family != ArmorFamily.CLOTH),
                "optional family compatibility hook can fail closed");
        check(RuneMutationPolicy.apply(List.of(), 2, RuneMutationPolicy.Action.INSERT,
                        0, "runa_fagy", ArmorFamily.PLATE,
                        RuneMutationPolicy.unrestrictedFamilies()).runes().equals(List.of("runa_fagy")),
                "bundled runes remain family-unrestricted");
    }

    private static void everyRuneActionUsesTheExactSnapshotRecoveryContract() {
        final List<String> before = List.of("same-item@revision-7", "payment:present");
        final List<String> after = List.of("same-item@revision-8", "payment:consumed");
        for (final RuneMutationPolicy.Action action : RuneMutationPolicy.Action.values()) {
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(
                            ItemMutationFaultMatrix.Operation.RUNE,
                            ItemMutationFaultMatrix.FailurePoint.AFTER_PREPARE, before, after))
                            == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                    action + " exact-before aborts without payment loss");
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(
                            ItemMutationFaultMatrix.Operation.RUNE,
                            ItemMutationFaultMatrix.FailurePoint.AFTER_INVENTORY_PUBLISH, before, after))
                            == ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER,
                    action + " exact-after commits once");
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(
                            ItemMutationFaultMatrix.Operation.RUNE,
                            ItemMutationFaultMatrix.FailurePoint.MIXED_INVENTORY, before, after))
                            == ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW,
                    action + " mixed item/payment witness fails closed");
        }
        final List<String> benignNoop = List.of("-", "-");
        check(ItemMutationRecoveryPolicy.decide(benignNoop, benignNoop, benignNoop)
                        == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                "production-shaped benign before==after witness is safely abortable rather than manual-review dead-end");
        check(ItemMutationRecoveryPolicy.decide(before, before, before)
                        == ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW,
                "malformed synthetic equal witness remains fail-closed");
    }

    private static void cursorRehomeIsAllOrNothing() {
        check(!AtomicCursorRehome.hasCapacity(64, 1, 0, 64),
                "63/64 partial stack plus fully occupied storage fails the production preflight");
        final FakeAtomicStep rejected = new FakeAtomicStep(
                new int[]{63, 64, 64}, 64, false, false);
        final int[] rejectedBefore = rejected.storage.clone();
        check(!AtomicCursorRehome.executeAtomic(rejected),
                "partial-stack/full-inventory rune rehome fails atomically");
        check(Arrays.equals(rejectedBefore, rejected.storage),
                "failed capacity preflight leaves storage byte-for-byte equivalent");
        check(rejected.cursor == 64 && rejected.persistCalls == 0,
                "failed capacity preflight leaves cursor unchanged and performs no durable write");

        check(AtomicCursorRehome.hasCapacity(64, 0, 1, 64),
                "one empty storage slot is exactly sufficient for a 64-stack cursor");
        final FakeAtomicStep accepted = new FakeAtomicStep(
                new int[]{64, 64, 0}, 64, false, false);
        check(AtomicCursorRehome.executeAtomic(accepted),
                "exactly sufficient storage rehomes the complete cursor stack");
        check(accepted.cursor == 0 && Arrays.equals(accepted.storage, new int[]{64, 64, 64}),
                "successful rehome clears cursor only after the full storage transfer");
        check(accepted.persistCalls == 1,
                "successful rehome persists exactly once");

        final FakeAtomicStep divergent = new FakeAtomicStep(
                new int[]{63, 0, 64}, 64, false, true);
        final int[] divergentBefore = divergent.storage.clone();
        check(!AtomicCursorRehome.executeAtomic(divergent),
                "unexpected Bukkit-style partial add is rolled back when leftovers remain");
        check(Arrays.equals(divergentBefore, divergent.storage) && divergent.cursor == 64,
                "partial add rollback restores exact storage and cursor snapshots");

        final FakeAtomicStep saveFailure = new FakeAtomicStep(
                new int[]{64, 0, 64}, 64, true, false);
        final int[] saveFailureBefore = saveFailure.storage.clone();
        check(!AtomicCursorRehome.executeAtomic(saveFailure),
                "saveData-equivalent failure rejects the rehome");
        check(Arrays.equals(saveFailureBefore, saveFailure.storage),
                "persistence failure rolls storage back to its exact pre-state");
        check(saveFailure.cursor == 64,
                "persistence failure restores the cursor pre-state");
        check(saveFailure.persistCalls == 2,
                "persistence failure performs one best-effort durable rollback save");
    }

    private static void schedulerRejectionReleasesTransientLock() {
        final UUID playerId = UUID.fromString("00000000-0000-0000-0000-0000000008aa");
        final Set<UUID> locks = ConcurrentHashMap.newKeySet();
        locks.add(playerId);
        ItemMutationCoordinator.runGuarded(
                () -> { throw new IllegalStateException("simulated entity scheduler rejection"); },
                () -> locks.remove(playerId));
        check(!locks.contains(playerId),
                "direct scheduler rejection executes the retired cleanup and cannot leak inFlight");

        locks.add(playerId);
        ItemMutationCoordinator.runGuarded(() -> locks.remove(playerId),
                () -> locks.remove(playerId));
        check(!locks.contains(playerId),
                "entity-retirement cleanup is idempotent and a reconnect can acquire a new operation lock");
    }

    private static final class FakeAtomicStep implements AtomicCursorRehome.AtomicStep {
        private int[] storage;
        private final int[] beforeStorage;
        private int cursor;
        private final int beforeCursor;
        private final boolean failFirstPersist;
        private final boolean forcePartialAdd;
        private int persistCalls;

        private FakeAtomicStep(final int[] storage, final int cursor,
                               final boolean failFirstPersist, final boolean forcePartialAdd) {
            this.storage = storage.clone();
            this.beforeStorage = storage.clone();
            this.cursor = cursor;
            this.beforeCursor = cursor;
            this.failFirstPersist = failFirstPersist;
            this.forcePartialAdd = forcePartialAdd;
        }

        @Override
        public boolean preflight() {
            int merge = 0;
            int empty = 0;
            for (final int amount : storage) {
                if (amount == 0) empty++;
                else if (amount < 64) merge += 64 - amount;
            }
            return AtomicCursorRehome.hasCapacity(cursor, merge, empty, 64);
        }

        @Override
        public boolean addAll() {
            int remaining = cursor;
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                if (storage[i] <= 0 || storage[i] >= 64) continue;
                final int moved = Math.min(remaining, 64 - storage[i]);
                storage[i] += moved;
                remaining -= moved;
                if (forcePartialAdd) return false;
            }
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                if (storage[i] != 0) continue;
                final int moved = Math.min(remaining, 64);
                storage[i] = moved;
                remaining -= moved;
                if (forcePartialAdd) return false;
            }
            return remaining == 0;
        }

        @Override public void clearCursor() { cursor = 0; }
        @Override public void restoreMemory() {
            storage = beforeStorage.clone();
            cursor = beforeCursor;
        }
        @Override public void persist() {
            persistCalls++;
            if (failFirstPersist && persistCalls == 1) {
                throw new IllegalStateException("simulated saveData failure");
            }
        }
    }

    private static ItemTemplate template() {
        return new ItemTemplate("rune_lifecycle", ItemTemplate.CURRENT_SCHEMA, 1,
                "Rune lifecycle", List.of(), ItemRarity.RARE, 20,
                ItemTemplate.Family.WEAPON, ItemTemplate.Slot.MAIN_HAND, "IRON_SWORD",
                "", "", 0, Set.of(), Set.of(), Set.of(), 0.0D, 0.0D,
                Map.of("attack_damage", 2.0D), Map.of(), 2, "", "",
                ItemTemplate.BindPolicy.NONE, ItemTemplate.TradePolicy.TRADEABLE,
                ItemTemplate.SalvagePolicy.MATERIALS, Set.of("profession:test"), Set.of(),
                Set.of("mining:test"), Set.of("smithing:test"), Map.of(), List.of());
    }

    private static ItemInstance instance() {
        return new ItemInstance(UUID.fromString("00000000-0000-0000-0000-000000000800"),
                ItemInstance.CURRENT_SCHEMA, "rune_lifecycle", 1, 20, Map.of(), List.of(),
                ItemInstance.AscensionState.base(), new ItemInstance.Origin("profession:craft",
                "rune-regression", UUID.fromString("00000000-0000-0000-0000-000000000899"),
                "Kovács", "armorer", "Glatziendorf", true, 1L),
                Set.of(), 0L, List.of(), ItemInstance.MutationState.fresh());
    }

    private static void expectFailure(final Runnable action, final String message) {
        assertions++;
        try { action.run(); }
        catch (final IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
