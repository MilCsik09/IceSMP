package hu.taliann.icesmp.itemization;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Behavioral rune socket, identity, receipt and crash-recovery regression. */
public final class RuneLifecycleRegressionSuite {
    private static int assertions;

    private RuneLifecycleRegressionSuite() { }

    public static void main(final String[] args) {
        insertRemoveReplacePreserveCanonicalState();
        invalidTransitionsFailClosed();
        everyRuneActionUsesTheExactSnapshotRecoveryContract();
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
