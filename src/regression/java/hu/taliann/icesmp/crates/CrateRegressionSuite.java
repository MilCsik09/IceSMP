package hu.taliann.icesmp.crates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dependency-free crate validation, selection, key-consumption and rollback regressions. */
public final class CrateRegressionSuite {

    private CrateRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        validatesWeightsAndAmounts();
        validatesCommandTemplates();
        consumesExactKeysAcrossStacks();
        boundsMassOpenWithoutPartialKeyUse();
        selectsWeightedRewardsDeterministically();
        recordsCooldownsStatsAndExactRollback();
        resetsAndRestoresStats();
        rejectsLedgerOverflowAndStaleRollback();
        verifiesProductionWiringAndFoliaGuards();
        System.out.println("Native crate regression suite passed.");
    }

    private static void validatesWeightsAndAmounts() {
        check(CrateRules.positiveWeight(1.5D) == 1.5D, "positive weight changed");
        expectThrows(IllegalArgumentException.class, () -> CrateRules.positiveWeight(0.0D));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.positiveWeight(-1.0D));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.positiveWeight(Double.NaN));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.positiveWeight(Double.POSITIVE_INFINITY));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.positiveWeight(CrateRules.MAX_WEIGHT + 1.0D));

        check(CrateRules.itemAmount(64, 1) == 64, "item amount changed");
        expectThrows(IllegalArgumentException.class, () -> CrateRules.itemAmount(0, 1));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.itemAmount(CrateRules.MAX_REWARD_ITEM_AMOUNT + 1, 1));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.boundedPositiveInt(CrateRules.MAX_RECIPE_REWARD_AMOUNT + 1, 1,
                        CrateRules.MAX_RECIPE_REWARD_AMOUNT, "amount"));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.currencyAmount(Double.NaN));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.currencyAmount(Double.NEGATIVE_INFINITY));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.cooldownMillis(-0.01D));
        expectThrows(IllegalArgumentException.class, () -> CrateRules.cooldownMillis(Double.POSITIVE_INFINITY));
    }

    private static void validatesCommandTemplates() {
        final String command = CrateRules.validateCommand(
                "give {player} minecraft:diamond {amount} # {uuid} {crate}");
        check(CrateRules.renderCommand(command, "Alice", "uuid", "rare", 3)
                        .equals("give Alice minecraft:diamond 3 # uuid rare"),
                "documented placeholders rendered incorrectly");
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("give {world} minecraft:diamond 1"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("give {PLAYER} minecraft:diamond 1"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("give {player minecraft:diamond 1"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("/give {player} minecraft:diamond 1"));
        expectThrows(IllegalArgumentException.class,
                () -> CrateRules.validateCommand("say ok\nop @a"));
    }

    private static void consumesExactKeysAcrossStacks() {
        final List<KeyConsumption.Take> plan = KeyConsumption.plan(List.of(2, 0, 4, 7), 9);
        check(plan.equals(List.of(new KeyConsumption.Take(0, 2),
                        new KeyConsumption.Take(2, 4), new KeyConsumption.Take(3, 3))),
                "multi-stack exact key plan is wrong");
        check(KeyConsumption.plan(List.of(2, 3), 6).isEmpty(),
                "insufficient key plan must not partially consume");
        check(KeyConsumption.plan(List.of(64), 64)
                        .equals(List.of(new KeyConsumption.Take(0, 64))),
                "single-stack exact plan is wrong");
    }

    private static void boundsMassOpenWithoutPartialKeyUse() {
        check(CrateRules.maxOpenable(9, 2, 10, true, 4) == 4,
                "mass-open must respect per-crate maximum");
        check(CrateRules.maxOpenable(7, 3, 10, true, 10) == 2,
                "mass-open must only include fully funded openings");
        check(CrateRules.maxOpenable(100, 2, 10, false, 10) == 1,
                "disabled mass-open must allow exactly one opening");
        check(CrateRules.maxOpenable(1, 2, 10, true, 10) == 0,
                "insufficient keys must yield no opening");
    }

    private static void selectsWeightedRewardsDeterministically() {
        final List<WeightedSelector.Weighted<String>> entries = List.of(
                new WeightedSelector.Weighted<>(1.0D, "a"),
                new WeightedSelector.Weighted<>(3.0D, "b"),
                new WeightedSelector.Weighted<>(6.0D, "c"));
        check(WeightedSelector.select(entries, 0.0D).equals("a"), "lower edge wrong");
        check(WeightedSelector.select(entries, 0.099999D).equals("a"), "first range wrong");
        check(WeightedSelector.select(entries, 0.10D).equals("b"), "second lower edge wrong");
        check(WeightedSelector.select(entries, 0.399999D).equals("b"), "second range wrong");
        check(WeightedSelector.select(entries, 0.40D).equals("c"), "third lower edge wrong");
        check(WeightedSelector.select(entries, Math.nextDown(1.0D)).equals("c"), "upper edge wrong");
        expectThrows(IllegalArgumentException.class, () -> WeightedSelector.select(entries, 1.0D));
        expectThrows(IllegalArgumentException.class,
                () -> new WeightedSelector.Weighted<>(Double.NaN, "bad"));
    }

    private static void recordsCooldownsStatsAndExactRollback() {
        final CrateLedger ledger = new CrateLedger();
        final UUID player = UUID.randomUUID();
        final CrateLedger.Mutation first = ledger.record(player, "Alice", "rare", 2, 1_000L, 5_000L);
        check(ledger.count(player, "rare") == 2L, "opening statistic not recorded");
        check(ledger.total(player) == 2L, "total statistic wrong");
        check(ledger.remainingCooldown(player, "rare", 3_000L) == 3_000L, "cooldown wrong");
        check(player.equals(ledger.findByName("alice")), "last-known-name lookup failed");
        ledger.rollback(first);
        check(ledger.count(player, "rare") == 0L, "rollback did not restore count");
        check(ledger.remainingCooldown(player, "rare", 1_000L) == 0L,
                "rollback did not restore cooldown");
    }

    private static void resetsAndRestoresStats() {
        final CrateLedger ledger = new CrateLedger();
        final UUID player = UUID.randomUUID();
        ledger.record(player, "Bob", "common", 3, 100L, 0L);
        ledger.record(player, "Bob", "rare", 2, 200L, 10L);
        final CrateLedger.ResetToken one = ledger.reset(player, "rare");
        check(ledger.count(player, "rare") == 0L && ledger.count(player, "common") == 3L,
                "per-crate reset removed wrong data");
        ledger.rollbackReset(one);
        check(ledger.count(player, "rare") == 2L && ledger.count(player, "common") == 3L,
                "per-crate reset rollback failed");
        final CrateLedger.ResetToken all = ledger.reset(player, null);
        check(ledger.total(player) == 0L, "full reset failed");
        ledger.rollbackReset(all);
        check(ledger.total(player) == 5L, "full reset rollback failed");

        final Map<UUID, CrateLedger.PlayerSnapshot> snapshot = ledger.snapshot();
        final CrateLedger restored = new CrateLedger();
        restored.replace(snapshot);
        check(restored.count(player, "rare") == 2L && restored.count(player, "common") == 3L,
                "restart snapshot round-trip failed");
    }

    private static void rejectsLedgerOverflowAndStaleRollback() {
        final UUID player = UUID.randomUUID();
        final CrateLedger overflow = new CrateLedger();
        overflow.replace(Map.of(player, new CrateLedger.PlayerSnapshot("Overflow",
                Map.of("rare", Long.MAX_VALUE), Map.of())));
        expectThrows(ArithmeticException.class,
                () -> overflow.record(player, "Overflow", "rare", 1, 1L, 0L));

        final CrateLedger cooldownOverflow = new CrateLedger();
        expectThrows(ArithmeticException.class,
                () -> cooldownOverflow.record(player, "Alice", "rare", 1, Long.MAX_VALUE, 1L));
        check(cooldownOverflow.total(player) == 0L
                        && cooldownOverflow.lastKnownName(player) == null,
                "failed cooldown computation must not partially mutate ledger state");

        final CrateLedger totalOverflow = new CrateLedger();
        totalOverflow.replace(Map.of(player, new CrateLedger.PlayerSnapshot("Overflow",
                Map.of("common", Long.MAX_VALUE, "rare", 0L), Map.of())));
        expectThrows(ArithmeticException.class,
                () -> totalOverflow.record(player, "Overflow", "rare", 1, 1L, 0L));
        check(totalOverflow.count(player, "rare") == 0L,
                "failed total computation must not partially mutate target count");

        final CrateLedger ledger = new CrateLedger();
        final CrateLedger.Mutation old = ledger.record(player, "Alice", "rare", 1, 1L, 0L);
        ledger.record(player, "Alice", "rare", 1, 2L, 0L);
        expectThrows(IllegalStateException.class, () -> ledger.rollback(old));
    }

    private static void verifiesProductionWiringAndFoliaGuards() throws IOException {
        final String manager = source("src/main/java/hu/taliann/icesmp/managers/CrateManager.java");
        final String listener = source("src/main/java/hu/taliann/icesmp/listeners/CrateListener.java");
        final String command = source("src/main/java/hu/taliann/icesmp/commands/CrateCommand.java");
        final String core = source("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String keyFactory = source("src/main/java/hu/taliann/icesmp/items/CrateKeyFactory.java");
        final String browser = source("src/main/java/hu/taliann/icesmp/gui/CrateBrowserGUI.java");

        check(listener.contains("event.getHand() != EquipmentSlot.HAND"),
                "off-hand duplicate interaction gate missing");
        check(manager.contains("implements PersistentStore, PlayerStateCleanup"),
                "crate manager must reuse persistence and cleanup contracts");
        check(manager.contains("YamlStore.saveAtomic(storageFile, yaml)"),
                "crate state must use atomic YAML persistence");
        check(manager.contains("yaml.set(\"blocks\", blocks)"),
                "crate block registry must serialize in the list shape read on restart");
        check(manager.contains("final List<Map<?, ?>> rawBlocks = yaml.getMapList(\"blocks\")"),
                "restart block registry decoder missing");
        check(manager.indexOf("writeStateLocked()") < manager.indexOf("finalizeOpen(player, pending)"),
                "statistics/cooldown must persist before key/reward side effects");
        check(manager.contains("KeyConsumption.plan(amounts, pending.keysRequired)"),
                "exact multi-stack key consumption planner missing");
        check(manager.contains("currencyManager.addBalances(player.getUniqueId(), currencyRewards)"),
                "currency rewards must use one wallet batch mutation");
        check(manager.contains("player.getInventory().setStorageContents(originalStorage)"),
                "currency failure must restore exact key inventory snapshot");
        check(manager.contains("Bukkit.getGlobalRegionScheduler().run(plugin"),
                "command rewards/broadcasts must use the global scheduler");
        check(command.contains("sendToSender(sender"),
                "async admin callbacks must hop back to the sender owner");
        check(core.contains("crateManager::shutdown") && core.contains("crateManager,"),
                "crate manager must join shutdown and shared persistent-store lifecycle");
        check(keyFactory.contains("getPersistentDataContainer().set(crateKeyIdKey"),
                "crate key must use the existing PDC identity");
        check(browser.contains("Read-only native `/crates` list and reward preview"),
                "native list/preview GUI missing");

        final List<String> crateSources = List.of(manager, listener, command, core, keyFactory, browser,
                source("src/main/java/hu/taliann/icesmp/gui/CrateSpinGUI.java"),
                source("src/main/java/hu/taliann/icesmp/listeners/CrateBrowserGUIListener.java"));
        for (final String code : crateSources) {
            check(!code.contains("Bukkit.getScheduler()"), "forbidden Bukkit scheduler introduced");
            check(!code.contains("new BukkitRunnable"), "forbidden BukkitRunnable introduced");
            check(!code.contains("new Thread("), "raw thread introduced");
            check(!code.contains("new Timer("), "raw timer introduced");
        }
    }

    private static String source(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type,
                                                         final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
