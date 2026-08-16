package hu.taliann.icesmp.pve;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dependency-free progression, authored-content and encounter regressions. */
public final class MobEncounterDomainRegressionSuite {
    private static int assertions;

    private MobEncounterDomainRegressionSuite() { }

    public static void main(final String[] args) {
        progressionCoversOneToSeventyWithSeparateCurves();
        hybridPrecedenceAndBonusesAreBounded();
        authoredTemplateAbilityAndAffixInvariantsHold();
        encounterSnapshotUsesDiminishingStableScaling();
        contributionIsBoundedMeaningfulAndIdempotent();
        combatPowerRemainsAContextEstimate();
        System.out.println("Mob/Encounter domain regression suite passed. assertions=" + assertions);
    }

    private static void progressionCoversOneToSeventyWithSeparateCurves() {
        final MobProgressionPolicy.Tuning tuning = MobProgressionPolicy.Tuning.defaults();
        double previousHealth = 0.0D;
        double previousDamage = 0.0D;
        for (final int level : List.of(1, 10, 25, 50, 70)) {
            final var stats = MobProgressionPolicy.scale(20.0D, 4.0D, level,
                    1.0D, 1.0D, tuning);
            check(stats.maximumHealth() > previousHealth, "health curve is monotonic at " + level);
            check(stats.attackDamage() > previousDamage, "damage curve is monotonic at " + level);
            check(stats.healthMultiplier() >= stats.damageMultiplier(),
                    "health grows at least as fast as damage at " + level);
            previousHealth = stats.maximumHealth();
            previousDamage = stats.attackDamage();
        }
        final var cap = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, null, null, 50, 20, 20, 20, 20, false), tuning);
        check(cap.level() == 70 && cap.capped(), "generic survival progression hard-caps at 70");
        final var authoredBoss = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                90, null, null, 1, 0, 0, 0, 0, true), tuning);
        check(authoredBoss.level() == 90
                        && authoredBoss.source() == MobProgressionPolicy.Source.ENCOUNTER_OVERRIDE,
                "authored boss display level may exceed the survival cap");
    }

    private static void hybridPrecedenceAndBonusesAreBounded() {
        final var tuning = MobProgressionPolicy.Tuning.defaults();
        check(MobProgressionPolicy.wildernessLevel(0.0D, 500.0D, 50) == 1,
                "world spawn starts at level one");
        check(MobProgressionPolicy.wildernessLevel(99_999.0D, 500.0D, 50) == 50,
                "distance component stops at normal level fifty");
        check(MobProgressionPolicy.depthBonus(-48, 32, 16, 8) == 5,
                "underground depth contributes a bounded danger bonus");

        final var wilderness = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, null, null, 25, 3, 2, 5, 4, false), tuning);
        check(wilderness.level() == 39 && wilderness.appliedBonus() == 14,
                "territory, biome, depth and event layers compose over wilderness");
        final var template = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, null, 30, 49, 2, 2, 3, 2, false), tuning);
        check(template.level() == 39
                        && template.source() == MobProgressionPolicy.Source.MOB_TEMPLATE,
                "explicit MobTemplate outranks the wilderness base");
        final var authored = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, 18, 40, 50, 9, 9, 9, 2, false), tuning);
        check(authored.level() == 20
                        && authored.source() == MobProgressionPolicy.Source.AUTHORED_LOCATION,
                "authored location outranks template and geographic modifiers");
        final var bloodMoon = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, null, null, 48, 0, 0, 0, 5, false), tuning);
        check(bloodMoon.level() == 53, "blood moon/event bonus can enter the 51-70 danger band");
    }

    private static void authoredTemplateAbilityAndAffixInvariantsHold() {
        final MobAbilityDefinition slam = new MobAbilityDefinition("ground_slam",
                MobAbilityDefinition.Kind.GROUND_SLAM, 160L, 30L,
                5.0D, 8.0D, 0, Map.of("knockback", 0.8D));
        check(slam.telegraphTicks() == 30L && MobAbilityDefinition.dangerous(slam.kind()),
                "dangerous registry ability carries a readable telegraph");
        expectFailure(() -> new MobAbilityDefinition("instant_slam",
                MobAbilityDefinition.Kind.GROUND_SLAM, 100L, 0L,
                4.0D, 10.0D, 0, Map.of()),
                "instant high-impact ability is rejected");

        final MobTemplate template = new MobTemplate("frostbound_guard", 1,
                "Fagybilincs őr", "STRAY", "", 18, 28, MobRank.ELITE,
                MobArchetype.CONTROLLER, new MobTemplate.StatProfile(1.2D, 0.9D, 1.0D, 0.35D),
                List.of("ground_slam"), Set.of("frost"), Set.of("fire"),
                "frozen_ruins", Set.of("biome:snowy_plains"), "natural_or_authored",
                "frostbound_guard", List.of(EliteAffix.FROSTBOUND, EliteAffix.SHIELDED,
                EliteAffix.VOLATILE, EliteAffix.ARCANE));
        check(template.levelAt(0.5D) == 23 && template.affixPool().size() == 4,
                "authored level range and reusable affix pool are deterministic");
        expectFailure(() -> new MobTemplate("bad", 1, "Bad", "ZOMBIE", "", 1, 2,
                MobRank.NORMAL, MobArchetype.BRUISER, MobTemplate.StatProfile.baseline(),
                List.of(), Set.of("fire"), Set.of("fire"), "bad", Set.of(), "natural", "bad",
                List.of()), "resistance/weakness mismatch is rejected");
        check(EliteAffix.validate(List.of(EliteAffix.VOLATILE, EliteAffix.SHIELDED)).size() == 2,
                "elite accepts at most two safe spawn-fixed affixes");
        expectFailure(() -> EliteAffix.validate(List.of(EliteAffix.VOLATILE,
                EliteAffix.SHIELDED, EliteAffix.FROSTBOUND)), "third affix is rejected");
        expectFailure(() -> EliteAffix.validate(List.of(EliteAffix.SUMMONER,
                EliteAffix.ARCANE)), "unsafe recursive/spam-prone combination is rejected");
        check(MobRank.parse("world-boss") == MobRank.WORLD_BOSS
                        && MobArchetype.parse("skirmisher") == MobArchetype.SKIRMISHER,
                "canonical rank and archetype vocabulary parses config ids");
    }

    private static void encounterSnapshotUsesDiminishingStableScaling() {
        final EncounterScalingPolicy.Tuning tuning = EncounterScalingPolicy.Tuning.defaults();
        final UUID encounter = UUID.fromString("00000000-0000-0000-0000-000000006001");
        final var one = EncounterScalingPolicy.snapshot(encounter, 1, players(1),
                100.0D, 100.0D, 1L, tuning);
        final var two = EncounterScalingPolicy.snapshot(encounter, 1, players(2),
                100.0D, 100.0D, 1L, tuning);
        final var five = EncounterScalingPolicy.snapshot(encounter, 1, players(5),
                100.0D, 100.0D, 1L, tuning);
        final var crowd = EncounterScalingPolicy.snapshot(encounter, 1, players(40),
                100.0D, 100.0D, 1L, tuning);
        check(close(one.healthMultiplier(), 1.0D), "one player keeps baseline health");
        check(two.healthMultiplier() > 1.5D && two.healthMultiplier() < 1.8D,
                "two-player health is diminishing, not linear");
        check(five.healthMultiplier() < 5.0D && five.healthMultiplier() > two.healthMultiplier(),
                "five-player health remains below linear scaling");
        check(crowd.healthMultiplier() <= tuning.maximumHealthMultiplier(),
                "large encounter scaling is bounded");
        check(five.damageMultiplier() < 1.15D,
                "party size only nudges boss damage and avoids one-shot scaling");
        check(five.participants().size() == 5,
                "disconnect, death or late join cannot mutate the immutable start snapshot");
    }

    private static void contributionIsBoundedMeaningfulAndIdempotent() {
        final UUID encounter = UUID.fromString("00000000-0000-0000-0000-000000006101");
        final UUID player = UUID.fromString("00000000-0000-0000-0000-000000006102");
        final UUID ally = UUID.fromString("00000000-0000-0000-0000-000000006103");
        final ContributionLedger ledger = new ContributionLedger(encounter, 1_000L, Set.of(player));
        check(!ledger.recordDamage(player, 50.0D, 999L),
                "pre-combat padding is rejected");
        check(ledger.recordDamage(player, 120.0D, 1_100L), "boss damage contributes");
        check(ledger.recordTanking(player, 40.0D, 1_150L), "tanking contributes at lower weight");
        check(!ledger.recordSupport(player, player, 500.0D, 1_200L),
                "self-heal/support farming is rejected");
        check(ledger.recordSupport(ally, player, 20.0D, 1_250L),
                "real ally support can contribute");
        check(ledger.qualified(100.0D, 1_000L).size() == 1,
                "meaningful threshold excludes AFK and token support");
        check(ledger.claimSettlement(player) && !ledger.claimSettlement(player),
                "personal reward settlement is exactly once");
        for (int index = 0; index < ContributionLedger.MAX_PARTICIPANTS + 5; index++) {
            ledger.register(new UUID(7L, index));
        }
        check(ledger.size() == ContributionLedger.MAX_PARTICIPANTS,
                "contribution ledger is hard-bounded");
        ledger.close();
        check(!ledger.recordDamage(player, 1.0D, 2_000L),
                "encounter-end cleanup closes further padding");
    }

    private static void combatPowerRemainsAContextEstimate() {
        final double plain = CombatPowerEstimator.estimate(new CombatPowerEstimator.Input(
                30, 40.0D, 9.0D, 12.0D, 0.0D, 0.2D, 0.0D, 0, 0));
        final double synergistic = CombatPowerEstimator.estimate(new CombatPowerEstimator.Input(
                30, 38.0D, 8.0D, 10.0D, 1.0D, 0.2D, 1.0D, 2, 2));
        check(synergistic > plain,
                "signature, set and rune context prevents rarity-only power estimates");
        check(Double.isFinite(CombatPowerEstimator.estimate(new CombatPowerEstimator.Input(
                1, Double.NaN, Double.POSITIVE_INFINITY, -5.0D,
                0.0D, 0.0D, 0.0D, 0, 0))), "telemetry estimate is finite and bounded");
    }

    private static Set<UUID> players(final int count) {
        final LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (int index = 1; index <= count; index++) result.add(new UUID(0L, index));
        return result;
    }

    private static boolean close(final double left, final double right) {
        return Math.abs(left - right) < 0.000_001D;
    }

    private static void expectFailure(final Runnable action, final String message) {
        assertions++;
        try {
            action.run();
        } catch (final IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
