package hu.taliann.icesmp.pve;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-contract gates for Folia scheduling, lifecycle cleanup and durable reward ordering. */
public final class MobRuntimeSourceRegressionSuite {
    private static int assertions;

    private MobRuntimeSourceRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final String scaling = read("src/main/java/hu/taliann/icesmp/managers/MobScalingManager.java");
        final String runtime = read("src/main/java/hu/taliann/icesmp/pve/MobAbilityRuntime.java");
        final String boss = read("src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java");
        final String delivery = read("src/main/java/hu/taliann/icesmp/pve/EncounterRewardDeliveryService.java");
        final String invasion = read("src/main/java/hu/taliann/icesmp/managers/InvasionManager.java");
        final String cultists = read("src/main/java/hu/taliann/icesmp/managers/CultistEventManager.java");
        final String wildHunt = read("src/main/java/hu/taliann/icesmp/managers/WildHuntManager.java");
        final String authoredSpawns = read("src/main/java/hu/taliann/icesmp/pve/AuthoredCreatureSpawnService.java");
        final String content = read("src/main/resources/config/mob-templates.yml");
        final String profiles = read("src/main/java/hu/taliann/icesmp/pve/CreatureProfileService.java");
        final String species = read("src/main/java/hu/taliann/icesmp/pve/CreatureSpeciesRegistry.java");
        final String loot = read("src/main/java/hu/taliann/icesmp/listeners/MobLootListener.java");
        final String soulstone = read("src/main/java/hu/taliann/icesmp/listeners/SoulstoneListener.java");
        final String classXp = read("src/main/java/hu/taliann/icesmp/listeners/ClassXpListener.java");

        check(scaling.contains("MobProgressionPolicy.resolve")
                        && scaling.contains("normalMaximum()")
                        && scaling.contains("depthBonusLevels")
                        && scaling.contains("biomeBonusLevels")
                        && scaling.contains("zoneBonusLevels"),
                "runtime scaling bypasses the hybrid policy");
        check(scaling.contains("maximum-absolute-health")
                        && scaling.contains("maximum-absolute-damage")
                        && !scaling.contains("healthPerLevel")
                        && !scaling.contains("damagePerLevel"),
                "legacy additive HP/damage scaling survived");
        check(scaling.contains("promotedRank") && scaling.contains("rollAffixes")
                        && scaling.contains("EliteAffix.validate")
                        && scaling.contains("SpawnReason.NATURAL"),
                "natural Veteran/Elite promotion is not spawn-policy bounded");

        check(runtime.contains("getScheduler().runAtFixedRate")
                        && runtime.contains("getScheduler().runDelayed")
                        && runtime.contains("getRegionScheduler().runDelayed")
                        && !runtime.contains("Bukkit.getScheduler")
                        && !runtime.contains(".join()"),
                "mob ability runtime violates Folia scheduling rules");
        final int startCast = runtime.indexOf("private boolean startCast");
        check(startCast >= 0, "common cast admission must report whether scheduling started");
        final int targetSnapshot = runtime.indexOf("private Location targetSnapshot", startCast);
        final String castLifecycle = runtime.substring(startCast, targetSnapshot);
        final int telegraph = castLifecycle.indexOf("telegraph(mob, chosen, target)");
        final int execution = castLifecycle.indexOf("execute(mob, chosen, target, state)");
        check(telegraph >= 0 && execution >= 0 && telegraph < execution,
                "dangerous ability executes before its vanilla telegraph");
        check(runtime.contains("MAX_ACTIVE_MOBS = 2048")
                        && runtime.contains("maximum-summons-per-cast")
                        && runtime.contains("summon-lifespan-ticks")
                        && runtime.contains("states.remove"),
                "ability/summon lifecycle is not bounded or cleaned");
        check(runtime.contains("projectile.setPickupStatus")
                        && runtime.contains("player.getScheduler().run")
                        && !runtime.contains("createExplosion"),
                "ability/affix runtime can leak projectiles, cross-region mutation or terrain damage");

        check(boss.contains("EncounterScalingPolicy.snapshot")
                        && boss.contains("ContributionLedger")
                        && boss.contains("recordBossDamage")
                        && boss.contains("recordBossTanking"),
                "world boss did not migrate to snapshot contribution authority");
        check(boss.contains("ledger.claimSettlement")
                        && boss.contains("delivery.activate")
                        && !boss.contains("killer-loot-rolls")
                        && !boss.contains("dropItemNaturally"),
                "world boss personal settlement remains killer-only or unsafe-drop based");
        final int deliveryMethod = delivery.indexOf("private void deliver");
        final int playerdataCommit = delivery.indexOf("player.saveData();", deliveryMethod);
        check(delivery.indexOf("operations.prepare") < deliveryMethod
                        && playerdataCommit > deliveryMethod
                        && delivery.indexOf("commit(player, operation);", playerdataCommit)
                        > playerdataCommit,
                "personal reward does not preserve receipt -> playerdata -> commit ordering");
        check(delivery.contains("if (!canFit(player, reward))")
                        && delivery.contains("receiptCount")
                        && delivery.contains("EncounterRewardRecoveryPolicy.decide")
                        && delivery.contains("Decision.MANUAL_REVIEW")
                        && delivery.contains("byTypeAndStatus")
                        && !delivery.contains("dropItemNaturally"),
                "full inventory/restart reward recovery can lose or duplicate an item");
        check(delivery.contains("operations.prepared(")
                        && delivery.contains("ELIGIBILITY_TYPE")
                        && delivery.contains("operations.rollback("),
                "restart does not abort exact-before contribution candidates");
        check(invasion.contains("Request.template(")
                        && cultists.contains("Request.template(")
                        && wildHunt.contains("Request.template(")
                        && !invasion.contains("Request.generic(")
                        && !cultists.contains("Request.generic(")
                        && !wildHunt.contains("Request.generic(")
                        && authoredSpawns.contains("scaling.forceTemplate")
                        && authoredSpawns.contains("scaling.forceRankedLevel")
                        && !invasion.contains("forceRankedLevel")&&!cultists.contains("forceRankedLevel")
                        && !wildHunt.contains("forceRankedLevel"),
                "existing invasion, cultist and wild-hunt events bypass the common canonical rank authority");

        final int templateCount = occurrences(content, "schema-version: 2");
        check(templateCount >= 55 && templateCount <= 100,
                "canonical creature and authored PvE content escaped the reviewed bounded template scope");
        check(occurrences(content, "kind:") >= 6
                        && content.contains("rank: VETERAN")
                        && content.contains("rank: CHAMPION")
                        && content.contains("rank: WORLD_BOSS"),
                "pilot lacks reusable abilities and rank vertical slice");
        check(content.contains("VOLATILE") && content.contains("VAMPIRIC")
                        && content.contains("SHIELDED") && content.contains("FROSTBOUND"),
                "pilot lacks four elite affixes");
        check(runtime.contains("recoveryUntilTick") && runtime.contains("interruptible()")
                        && runtime.contains("maximumTechniques")
                        && runtime.contains("rank-abilities"),
                "rank techniques lack recovery, interrupt counterplay or bounded rank defaults");
        check(profiles.contains("EntityDamageByEntityEvent")
                        && profiles.contains("responsiblePlayer")
                        && profiles.contains("SpawnReason.DEFAULT")
                        && !profiles.contains("SpawnReason.CHUNK_GEN")
                        && profiles.contains("instanceof Tameable")
                        && profiles.contains("!ageable.isAdult()")
                        && profiles.contains("maximumCandidates")
                        && profiles.contains("maximumAssistants")
                        && profiles.contains("ally.getScheduler().run")
                        && !profiles.contains("Bukkit.getScheduler"),
                "unified provocation is not player-owned, tame/baby-safe, bounded or Folia-safe");
        check(profiles.contains("runtime.enterCombat")
                        && profiles.contains("runtime.trigger")
                        && runtime.contains("authoredCombat")
                        && runtime.contains("castEpoch")
                        && runtime.contains("disengage"),
                "passive reactions bypass the common technique/cast/disengage lifecycle");
        check(species.contains("EntityType.values()")
                        && species.contains("type.isAlive()")
                        && species.contains("type.isSpawnable()")
                        && species.contains("nonCombatFallback"),
                "species inventory is hardcoded or fails open on unknown Paper types");
        check(loot.contains("CreatureProfileService")
                        && soulstone.contains("authoredRewardEligible")
                        && classXp.contains("authoredRewardEligible"),
                "combat capability can still become an implicit gear, soulstone or XP faucet");
        check(!Files.exists(Path.of(
                        "src/main/java/hu/taliann/icesmp/pve/WildlifeRetaliationService.java"))
                        && !Files.exists(Path.of(
                        "src/main/java/hu/taliann/icesmp/pve/WildlifeRetaliationPolicy.java")),
                "legacy wildlife retaliation remains a second active truth source");

        System.out.println("Mob runtime source regression suite passed. assertions=" + assertions);
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int occurrences(final String source, final String token) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
