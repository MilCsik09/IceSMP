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
        final String content = read("src/main/resources/config/mob-templates.yml");

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
        check(runtime.indexOf("telegraph(mob, chosen, target)")
                        < runtime.indexOf("execute(mob, chosen, target)"),
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
                        && delivery.contains("hasReceipt")
                        && delivery.contains("byTypeAndStatus")
                        && !delivery.contains("dropItemNaturally"),
                "full inventory/restart reward recovery can lose or duplicate an item");
        check(delivery.contains("operations.prepared(")
                        && delivery.contains("ELIGIBILITY_TYPE")
                        && delivery.contains("operations.rollback("),
                "restart does not abort exact-before contribution candidates");
        check(invasion.contains("MobRank.CHAMPION")
                        && invasion.contains("forceRankedLevel")
                        && cultists.contains("MobRank.VETERAN")
                        && wildHunt.contains("MobRank.ELITE"),
                "existing invasion, cultist and wild-hunt events bypass canonical ranks");

        check(occurrences(content, "schema-version: 1") == 6,
                "pilot content must contain exactly six reviewed MobTemplates");
        check(occurrences(content, "kind:") >= 6
                        && content.contains("rank: VETERAN")
                        && content.contains("rank: CHAMPION")
                        && content.contains("rank: WORLD_BOSS"),
                "pilot lacks reusable abilities and rank vertical slice");
        check(content.contains("VOLATILE") && content.contains("VAMPIRIC")
                        && content.contains("SHIELDED") && content.contains("FROSTBOUND"),
                "pilot lacks four elite affixes");

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
