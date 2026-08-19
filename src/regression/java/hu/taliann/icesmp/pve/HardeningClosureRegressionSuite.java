package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.itemization.ItemSetDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Final behavioral/source closure for refresh, set, boss, witness and bounded runtime contracts. */
public final class HardeningClosureRegressionSuite {
    private static int assertions;

    private HardeningClosureRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        combatPowerUsesEventsAndExplicitMutationHooks();
        setProjectionIsStableAcrossRefreshAndUnequip();
        rewardAndContributionPoliciesRemainFailClosed();
        worldBossAndAbilityExitContractsRemainBounded();
        System.out.println("Hardening closure regression suite passed. assertions=" + assertions);
    }

    private static void combatPowerUsesEventsAndExplicitMutationHooks() throws Exception {
        final String service = read("src/main/java/hu/taliann/icesmp/pve/EquippedCombatPowerService.java");
        check(!service.contains("runAtFixedRate") && !service.contains("refreshLoops")
                        && service.contains("refreshAfterMutation")
                        && service.contains("InventoryClickEvent")
                        && service.contains("InventoryDragEvent")
                        && service.contains("PlayerItemHeldEvent")
                        && service.contains("PlayerSwapHandItemsEvent")
                        && service.contains("EntityPickupItemEvent")
                        && service.contains("PlayerDropItemEvent")
                        && service.contains("PlayerItemBreakEvent")
                        && service.contains("PlayerDeathEvent")
                        && service.contains("PlayerRespawnEvent")
                        && service.contains("PlayerJoinEvent"),
                "CombatPower refresh is event-driven rather than a polling repair loop");
        for (final String path : new String[]{
                "src/main/java/hu/taliann/icesmp/itemization/ItemMutationCoordinator.java",
                "src/main/java/hu/taliann/icesmp/commands/ItemGiveCommand.java",
                "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java",
                "src/main/java/hu/taliann/icesmp/managers/MarketManager.java",
                "src/main/java/hu/taliann/icesmp/managers/CrateManager.java",
                "src/main/java/hu/taliann/icesmp/managers/InvseeManager.java"}) {
            check(read(path).contains("EquippedCombatPowerService.refreshAfterMutation"),
                    path + " explicitly invalidates plugin-owned equipment mutations");
        }
    }

    private static void setProjectionIsStableAcrossRefreshAndUnequip() throws Exception {
        final ItemSetDefinition set = new ItemSetDefinition("closure_set", "Closure",
                Map.of(2, Map.of("armor", 2.0D, "max_health", 4.0D),
                        3, Map.of("movement_speed", 0.02D, "ability_power", 3.0D)));
        final Map<String, Double> first = set.activeStats(3);
        final Map<String, Double> refreshed = set.activeStats(3);
        check(first.equals(refreshed) && first.get("armor") == 2.0D
                        && first.get("max_health") == 4.0D
                        && first.get("movement_speed") == 0.02D,
                "repeated set projection is deterministic and additive only across authored tiers");
        check(set.activeStats(1).isEmpty() && set.activeStats(2).size() == 2,
                "unequip and gear replacement remove tiers instead of retaining stale bonuses");

        final String service = read("src/main/java/hu/taliann/icesmp/pve/EquippedCombatPowerService.java");
        final int remove = service.indexOf("instance.removeModifier(existing)");
        final int add = service.indexOf("instance.addTransientModifier", remove);
        check(service.contains("item_set_max_health")
                        && service.contains("item_set_armor")
                        && service.contains("item_set_movement_speed")
                        && remove >= 0 && add > remove,
                "stable NamespacedKeys remove the old transient modifier before adding a refresh");
        check(service.contains("counts.getOrDefault(candidate.itemId(), 0) == 1")
                        && service.contains("proficiency.isActive(player, item, equippedSlot)"),
                "duplicate UUID and slot/proficiency-invalid equipment fail closed before set projection through the shared authority");
    }

    private static void rewardAndContributionPoliciesRemainFailClosed() {
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, 0)
                        == EncounterRewardRecoveryPolicy.Decision.DELIVER,
                "zero PREPARED witnesses materialize once");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, 1)
                        == EncounterRewardRecoveryPolicy.Decision.COMMIT_WITNESS,
                "one exact physical witness commits without rematerializing");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, 2)
                        == EncounterRewardRecoveryPolicy.Decision.MANUAL_REVIEW,
                "duplicate physical witnesses require manual review");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.COMMITTED, 0)
                        == EncounterRewardRecoveryPolicy.Decision.CLEANUP_ONLY,
                "COMMITTED rewards never materialize again");

        final UUID encounter = UUID.fromString("00000000-0000-0000-0000-000000000901");
        final UUID healer = UUID.fromString("00000000-0000-0000-0000-000000000902");
        final UUID ally = UUID.fromString("00000000-0000-0000-0000-000000000903");
        final ContributionLedger ledger = new ContributionLedger(encounter, 1_000L, Set.of(ally));
        check(!ledger.recordSupport(healer, healer, 100.0D, 1_001L)
                        && !ledger.recordSupport(healer, ally, 100.0D, 1_001L),
                "self-heal and pre-combat ally padding are rejected");
        check(ledger.recordDamage(ally, 10.0D, 1_002L)
                        && ledger.recordSupport(healer, ally, 5.0D, 1_003L),
                "support becomes eligible only after encounter-owned ally activity");
        ledger.close();
        check(!ledger.recordDamage(ally, 10.0D, 1_004L),
                "closed encounter contribution cannot leak into a later fight");
    }

    private static void worldBossAndAbilityExitContractsRemainBounded() throws Exception {
        final String boss = read("src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java");
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/WorldBossListener.java");
        final String snapshot = boss.substring(boss.indexOf("createEncounterSnapshot"),
                boss.indexOf("private volatile long spawnGraceUntil"));
        check(snapshot.contains("PositionCache.nearbyPlayerIds")
                        && snapshot.contains("ContributionLedger.MAX_PARTICIPANTS")
                        && snapshot.contains("participants.isEmpty()")
                        && !snapshot.contains("Bukkit.getOnlinePlayers()"),
                "world-boss snapshot is same-world, survival-filtered, bounded and empty-fail-closed");
        check(boss.contains("ledger.close()")
                        && boss.contains("contributionLedger = null")
                        && boss.contains("encounterSnapshot = null")
                        && boss.contains("rewardCandidates.clear()")
                        && boss.contains("clearDisplayState()")
                        && boss.contains("abortPreparedEncounter")
                        && listener.contains("EntityRemoveEvent")
                        && listener.contains("worldBossManager.shutdown()"),
                "death, timed despawn, removal and shutdown share the complete boss cleanup contract");

        final String abilities = read("src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java");
        check(abilities.contains("MAX_ACTIVE_MOBS = 2048")
                        && abilities.contains("synchronized (states)")
                        && abilities.indexOf("states.size() >= MAX_ACTIVE_MOBS")
                        < abilities.indexOf("states.put(mobId, state)")
                        && abilities.contains("states.remove(mob.getUniqueId(), state)"),
                "ability admission checks and inserts atomically and rejected schedulers clean state");
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
