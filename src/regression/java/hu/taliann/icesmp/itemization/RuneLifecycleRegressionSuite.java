package hu.taliann.icesmp.itemization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Behavioral rune socket, cursor atomicity, identity, receipt and crash-recovery regression. */
public final class RuneLifecycleRegressionSuite {
    private static int assertions;

    private RuneLifecycleRegressionSuite() { }

    public static void main(final String[] args) {
        insertRemoveReplacePreserveCanonicalState();
        invalidTransitionsFailClosed();
        everyRuneActionUsesTheExactSnapshotRecoveryContract();
        cursorRehomeIsAllOrNothing();
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
    }

    private static void cursorRehomeIsAllOrNothing() {
        // P0 reproducer: 63/64 partial stack + every other storage slot full + cursor 64.
        // Preflight must reject before Bukkit can partially merge one item.
        final ItemStack[] full = filledStorage();
        full[0] = new ItemStack(Material.PAPER, 63);
        final FakeAdapter rejected = new FakeAdapter(full, new ItemStack(Material.PAPER, 64), false);
        final ItemStack[] rejectedBefore = AtomicCursorRehome.cloneContents(rejected.storage);
        check(!AtomicCursorRehome.rehome(rejected, rejected.cursor),
                "partial-stack/full-inventory rune rehome fails atomically");
        check(Arrays.equals(rejectedBefore, rejected.storage),
                "failed partial-stack rehome leaves storage ItemStack-equivalent");
        check(rejected.cursor != null && rejected.cursor.getAmount() == 64,
                "failed partial-stack rehome leaves the complete cursor stack");

        // Exactly one empty 64-stack slot: all carried items fit and the cursor is cleared.
        final ItemStack[] exact = filledStorage();
        exact[7] = null;
        final FakeAdapter accepted = new FakeAdapter(exact, new ItemStack(Material.PAPER, 64), false);
        check(AtomicCursorRehome.rehome(accepted, accepted.cursor),
                "exactly sufficient storage rehomes the complete cursor stack");
        check(accepted.cursor == null && accepted.count(Material.PAPER) == 64,
                "successful rehome clears cursor only after the full storage transfer");

        // Persistence failure after the in-memory add must restore both storage and cursor exactly.
        final ItemStack[] saveFailureStorage = filledStorage();
        saveFailureStorage[12] = null;
        final FakeAdapter saveFailure = new FakeAdapter(saveFailureStorage,
                new ItemStack(Material.PAPER, 64), true);
        final ItemStack[] saveFailureBefore = AtomicCursorRehome.cloneContents(saveFailure.storage);
        check(!AtomicCursorRehome.rehome(saveFailure, saveFailure.cursor),
                "saveData-equivalent failure rejects the rehome");
        check(Arrays.equals(saveFailureBefore, saveFailure.storage),
                "persistence failure rolls storage back to its exact pre-state");
        check(saveFailure.cursor != null && saveFailure.cursor.getAmount() == 64,
                "persistence failure restores the cursor pre-state");
        check(saveFailure.persistCalls == 2,
                "persistence failure performs one best-effort durable rollback save");
    }

    private static ItemStack[] filledStorage() {
        final ItemStack[] result = new ItemStack[36];
        for (int slot = 0; slot < result.length; slot++) {
            result[slot] = new ItemStack(Material.COBBLESTONE, 64);
        }
        return result;
    }

    private static final class FakeAdapter implements AtomicCursorRehome.Adapter {
        private ItemStack[] storage;
        private ItemStack cursor;
        private final boolean failFirstPersist;
        private int persistCalls;

        private FakeAdapter(final ItemStack[] storage, final ItemStack cursor,
                            final boolean failFirstPersist) {
            this.storage = AtomicCursorRehome.cloneContents(storage);
            this.cursor = cursor == null ? null : cursor.clone();
            this.failFirstPersist = failFirstPersist;
        }

        @Override public ItemStack[] storageContents() { return storage; }

        @Override
        public Map<Integer, ItemStack> add(final ItemStack added) {
            int remaining = added.getAmount();
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                final ItemStack current = storage[slot];
                if (current == null || !current.isSimilar(added)
                        || current.getAmount() >= current.getMaxStackSize()) continue;
                final int move = Math.min(remaining, current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + move);
                remaining -= move;
            }
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                if (storage[slot] != null) continue;
                final ItemStack next = added.clone();
                final int move = Math.min(remaining, next.getMaxStackSize());
                next.setAmount(move);
                storage[slot] = next;
                remaining -= move;
            }
            if (remaining == 0) return Map.of();
            final ItemStack leftover = added.clone();
            leftover.setAmount(remaining);
            final Map<Integer, ItemStack> result = new LinkedHashMap<>();
            result.put(0, leftover);
            return result;
        }

        @Override public void restoreStorage(final ItemStack[] snapshot) {
            storage = AtomicCursorRehome.cloneContents(snapshot);
        }
        @Override public ItemStack cursor() { return cursor; }
        @Override public void setCursor(final ItemStack next) { cursor = next == null ? null : next.clone(); }
        @Override public void persist() {
            persistCalls++;
            if (failFirstPersist && persistCalls == 1) throw new IllegalStateException("simulated saveData failure");
        }

        private int count(final Material material) {
            int total = 0;
            for (final ItemStack item : storage) {
                if (item != null && item.getType() == material) total += item.getAmount();
            }
            return total;
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
