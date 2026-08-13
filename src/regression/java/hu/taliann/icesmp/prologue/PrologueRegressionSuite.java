package hu.taliann.icesmp.prologue;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pure math + source-contract regressions for the Season 0 authority and recovery flow. */
public final class PrologueRegressionSuite {
    private PrologueRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        progressionCeilingAndCatchUp();
        participantScalingIsBounded();
        authorityAndRecoveryContracts();
        worldGateContracts();
        rehearsalAndFoliaContracts();
        System.out.println("Prologue regression suite passed.");
    }

    private static void progressionCeilingAndCatchUp() {
        final int capXp = PrologueProgression.experienceAtLevelStart(25, 100, 20);
        check(capXp > 0, "level-25 threshold must be positive");
        check(PrologueProgression.clampExperienceToLevelCap(capXp + 1_000_000, 25, 100, 20) == capXp,
                "Season 0 XP can bank above the level-25 boundary");
        check(PrologueProgression.clampExperienceToLevelCap(capXp - 10, 25, 100, 20) == capXp - 10,
                "valid XP below the cap is mutated");
        check(PrologueProgression.applyMultiplier(100, 1.75D) == 175,
                "Season 1 catch-up multiplier is not deterministic");
        check(PrologueProgression.applyMultiplier(100, 1.0D) == 100,
                "disabled catch-up changes XP");
    }

    private static void participantScalingIsBounded() {
        check(PrologueScaling.effectivePlayers(0, 5, 45) == 5,
                "participant scaling ignores configured minimum");
        check(PrologueScaling.effectivePlayers(99, 5, 45) == 45,
                "participant scaling ignores configured maximum");
        final int ten = PrologueScaling.mobCount(4, 10, 5, 45, 0.4D, 4, 28);
        final int fortyFive = PrologueScaling.mobCount(4, 45, 5, 45, 0.4D, 4, 28);
        check(ten >= 4 && fortyFive >= ten && fortyFive <= 28,
                "mob scaling is not monotonic/bounded");
        final double boss = PrologueScaling.bossHealth(500.0D, 45, 5, 45, 0.075D, 4.0D);
        check(boss >= 500.0D && boss <= 2_000.0D,
                "boss scaling exceeds configured multiplier bound");
    }

    private static void authorityAndRecoveryContracts() throws Exception {
        final String manager = source("src/main/java/hu/taliann/icesmp/prologue/PrologueManager.java");
        check(manager.contains("YamlStore.registerCriticalWrite(storageFile)"),
                "Prologue world state is not a critical durable store");
        check(manager.contains("bossDefeated = true") && manager.contains("finaleVictory = true")
                        && manager.contains("unlockGateAfterVictory")
                        && manager.contains("rewardPlanCreated") && manager.contains("rewardsCommitted")
                        && manager.contains("seasonOneStarted"),
                "critical finale transaction checkpoints are missing");
        check(manager.contains("restoreLocked(before)"),
                "failed Prologue persistence does not roll back in-memory state");
        check(manager.contains("if (!gateUnlocked || !rewardsCommitted || !seasonOneStarted)"),
                "COMPLETED can be committed before irreversible transition requirements");

        final String finale = source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleManager.java");
        check(finale.indexOf("recordBossVictory") < finale.indexOf("unlockGateAfterVictory")
                        && finale.indexOf("unlockGateAfterVictory") < finale.indexOf("markRewardPlanCreated")
                        && finale.indexOf("markRewardPlanCreated") < finale.indexOf("markRewardsCommitted"),
                "boss victory/gate/reward persistence ordering is unsafe");
        check(finale.contains("state.bossDefeated()") && finale.contains("recovery:boss-defeated"),
                "boss-death recovery can respawn a defeated boss");
        check(finale.contains("publishExtraordinaryOnce") && finale.contains("recordPrologueOnce"),
                "historical chronicle/monument one-shot hooks are not wired");

        final String rewards = source("src/main/java/hu/taliann/icesmp/prologue/PrologueRewardService.java");
        check(rewards.contains("repository().cached(playerId).isEmpty()")
                        && rewards.contains("grantEligibleWhenProfileReady")
                        && rewards.contains("prologue.finaleParticipants().contains(playerId)"),
                "offline eligible participants have no replay-safe Profile v2 delivery path");
        check(!rewards.contains("PersistentDataContainer") && !rewards.contains("player.yml"),
                "prestige state bypasses PlayerProfile v2 authority");

        final String transition = source("src/main/java/hu/taliann/icesmp/prologue/PrologueSeasonTransition.java");
        check(transition.contains("prologue-season-transition.yml")
                        && transition.contains("YamlStore.registerCriticalWrite(receiptFile)")
                        && transition.contains("season-one-start")
                        && transition.contains("season.number\", 1")
                        && transition.contains("season.start\", startTimestamp"),
                "Season 1 fresh-start timestamp is not crash-stable");
    }

    private static void worldGateContracts() throws Exception {
        final String portal = source("src/main/java/hu/taliann/icesmp/listeners/PortalGuardListener.java");
        check(portal.contains("CreateReason.FIRE") && portal.contains("NETHER_PORTAL")
                        && portal.contains("World.Environment.NETHER")
                        && portal.contains("PrologueContentPolicy.netherTraversalAvailable")
                        && portal.contains("PrologueWorldAccess.within")
                        && portal.contains("Permissions.TERRITORY_BYPASS"),
                "single-gate Nether travel authority is incomplete");
        check(portal.contains("END_PORTAL_FRAME") && portal.contains("END_PORTAL"),
                "Prologue integration weakens the End owner policy");

        final String worldAccess = source("src/main/java/hu/taliann/icesmp/prologue/PrologueWorldAccess.java");
        check(worldAccess.contains("prologue-gate") && worldAccess.contains("prologue-gathering")
                        && worldAccess.contains("prologue-breach") && worldAccess.contains("prologue-boss"),
                "builder-defined Prologue anchors are incomplete");
        check(!worldAccess.matches("(?s).*new Location\\([^,]+,\\s*-?\\d+.*"),
                "authoritative map coordinates leaked into Prologue runtime");

        final String job = source("src/main/java/hu/taliann/icesmp/managers/JobManager.java");
        check(job.contains("gateway.operation(player.getUniqueId(), operationId)")
                        && job.contains("PrologueContentPolicy.catchUpMultiplier")
                        && job.contains("PrologueContentPolicy.clampClassExperience"),
                "central class-XP path does not own cap/catch-up/replay semantics");
        final String spec = source("src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileSpecializationProgressStore.java");
        check(spec.contains("PrologueContentPolicy.specializationAvailable"),
                "Profile v2 memory specialization path bypasses the Prologue policy");
        final String ritual = source("src/main/java/hu/taliann/icesmp/listeners/RitualListener.java");
        check(ritual.contains("PrologueContentPolicy.relicAcquisitionAvailable"),
                "normal relic ritual path bypasses Prologue policy");
        final String overlay = source("src/main/java/hu/taliann/icesmp/prologue/PrologueRuntimeConfigOverlay.java");
        check(overlay.contains("loot.blueprint-drop.chance\", 0.0D")
                        && overlay.contains("loot.boss-drop.chance\", 0.0D")
                        && overlay.contains("applyRarityCeiling"),
                "Season 0 loot/blueprint ceiling is incomplete");
    }

    private static void rehearsalAndFoliaContracts() throws Exception {
        final String command = source("src/main/java/hu/taliann/icesmp/commands/PrologueCommand.java");
        check(command.contains("--rehearsal") && command.contains("gate open --force")
                        && command.contains("pause") && command.contains("resume")
                        && command.contains("abort"),
                "live-ops/rehearsal control surface is incomplete");
        final String encounter = source("src/main/java/hu/taliann/icesmp/prologue/PrologueEncounterEngine.java");
        check(encounter.contains("getRegionScheduler().run")
                        && encounter.contains("getScheduler().runAtFixedRate")
                        && encounter.contains("player.getScheduler().run")
                        && encounter.contains("EventSpawnGuard.prepare")
                        && encounter.contains("event.getDrops().clear()"),
                "encounter engine violates Folia/event-spawn/no-power-loot contracts");
        final String ceasefire = source("src/main/java/hu/taliann/icesmp/prologue/PrologueCeasefireListener.java");
        check(ceasefire.contains("EventPriority.HIGHEST") && ceasefire.contains("event.setCancelled(true)")
                        && !ceasefire.contains("TerritoryManager"),
                "finale ceasefire is not a transient event-context override");
        final String runtime = source("src/main/java/hu/taliann/icesmp/prologue/PrologueRuntime.java");
        check(runtime.contains("world-events.season.enabled\", false")
                        && runtime.contains("world-events.season-finale.enabled\", false")
                        && runtime.contains("community-goals.enabled\", false"),
                "normal Season 1 league/finale can drift during Prologue");
    }

    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of(relative)).replace("\r\n", "\n");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
