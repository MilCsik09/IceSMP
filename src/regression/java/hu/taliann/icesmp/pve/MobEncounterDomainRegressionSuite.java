package hu.taliann.icesmp.pve;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dependency-free behavioral progression, authored-content and encounter regressions. */
public final class MobEncounterDomainRegressionSuite {
    private static int assertions;

    private MobEncounterDomainRegressionSuite() { }

    public static void main(final String[] args) {
        progressionCoversOneToSeventyWithSeparateCurves();
        hybridPrecedenceAndBonusesAreBounded();
        authoredTemplateAbilityAndAffixInvariantsHold();
        behaviorProfilesCreateDistinctSpacingDecisions();
        contextualSelectionIsStableAndWeighted();
        daylightProtectionSourcesCompose();
        creatureReactionIdentityIsStableAndBounded();
        elitePassiveRankDoesNotImplyAggressionOrRewards();
        deterministicPopulationDistributionIsRepresentative();
        encounterSnapshotUsesDiminishingStableScaling();
        contributionIsBoundedMeaningfulAndIdempotent();
        combatPowerRemainsAContextEstimate();
        equippedCombatPowerTracksLiveCanonicalGear();
        encounterRewardFaultMatrixIsFailClosed();
        System.out.println("Mob/Encounter behavioral domain regression suite passed. assertions=" + assertions);
    }

    private static void creatureReactionIdentityIsStableAndBounded() {
        final UUID animal = UUID.fromString("00000000-0000-0000-0000-00000000a11f");
        final var policy = passivePolicy(Map.of(
                CreatureSpeciesPolicy.Temperament.TIMID, 80,
                CreatureSpeciesPolicy.Temperament.DEFENSIVE, 20));
        final CreatureSpeciesPolicy.Temperament first = policy.temperamentPolicy()
                .select(animal, policy.allowedTemperaments());
        check(first == policy.temperamentPolicy().select(animal, policy.allowedTemperaments()),
                "creature temperament is stable for the entity UUID");
        final CreatureSpeciesPolicy.Reaction reaction = policy.temperamentPolicy().reaction(animal, first);
        check(reaction == policy.temperamentPolicy().reaction(animal, first),
                "one entity does not reroll its FLEE/FIGHT identity on every hit");
        check(policy.disposition() == CreatureSpeciesPolicy.Disposition.PASSIVE,
                "a stable combat reaction does not change passive disposition");
        expectFailure(() -> new CreatureSpeciesPolicy.SocialPolicy(
                        CreatureSpeciesPolicy.SocialRelation.SAME_SPECIES,
                        32.0D, 80, Set.of(), 1L, 500),
                "unbounded herd propagation is rejected");
    }

    private static void elitePassiveRankDoesNotImplyAggressionOrRewards() {
        final CreatureSpeciesPolicy policy = passivePolicy(Map.of(
                CreatureSpeciesPolicy.Temperament.DEFENSIVE, 100));
        check(policy.techniquesFor(MobRank.NORMAL).equals(List.of("headbutt")),
                "normal passive gets one authored physical technique");
        check(policy.techniquesFor(MobRank.ELITE).containsAll(
                        List.of("headbutt", "short_charge", "defensive_stomp")),
                "elite passive gets bounded rank complexity after provocation");
        check(policy.disposition() == CreatureSpeciesPolicy.Disposition.PASSIVE,
                "elite rank cannot promote passive disposition to hostile");
        check(!policy.authoredRewardEligible(),
                "elite passive does not inherit hostile canonical rewards");
    }

    private static void deterministicPopulationDistributionIsRepresentative() {
        final CreatureSpeciesPolicy policy = passivePolicy(Map.of(
                CreatureSpeciesPolicy.Temperament.TIMID, 80,
                CreatureSpeciesPolicy.Temperament.DEFENSIVE, 20));
        int fights = 0;
        for (int index = 0; index < 10_000; index++) {
            final UUID entity = new UUID(0x1ce5L, index);
            final CreatureSpeciesPolicy.Temperament temperament = policy.temperamentPolicy()
                    .select(entity, policy.allowedTemperaments());
            if (policy.temperamentPolicy().reaction(entity, temperament)
                    == CreatureSpeciesPolicy.Reaction.FIGHT) fights++;
        }
        check(fights >= 1_750 && fights <= 2_250,
                "seeded 20 percent defensive population stays within deterministic tolerance: " + fights);
    }

    private static CreatureSpeciesPolicy passivePolicy(
            final Map<CreatureSpeciesPolicy.Temperament, Integer> weights) {
        final Map<CreatureSpeciesPolicy.Temperament, Double> fight = new java.util.EnumMap<>(
                CreatureSpeciesPolicy.Temperament.class);
        weights.keySet().forEach(temperament -> fight.put(temperament,
                temperament == CreatureSpeciesPolicy.Temperament.DEFENSIVE ? 100.0D : 0.0D));
        return new CreatureSpeciesPolicy("cow", CreatureSpeciesPolicy.Category.PASSIVE,
                CreatureSpeciesPolicy.Disposition.PASSIVE, true, true,
                MobArchetype.BRUISER, weights.keySet(),
                new CreatureSpeciesPolicy.TemperamentPolicy(weights, fight,
                        false, 240L, "panic_dash"),
                CreatureSpeciesPolicy.ProvocationPolicy.DIRECT_PLAYER,
                List.of("headbutt"), Map.of(
                        MobRank.VETERAN, List.of("short_charge"),
                        MobRank.ELITE, List.of("defensive_stomp")),
                new CreatureSpeciesPolicy.SocialPolicy(
                        CreatureSpeciesPolicy.SocialRelation.SAME_SPECIES,
                        6.0D, 2, Set.of(CreatureSpeciesPolicy.Temperament.DEFENSIVE),
                        240L, 12),
                CreatureSpeciesPolicy.RewardProfile.VANILLA_ONLY,
                CreatureSpeciesPolicy.BabyPolicy.IDENTITY_ONLY,
                CreatureSpeciesPolicy.TamePolicy.NOT_TAMEABLE);
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
        final var levelOne = MobProgressionPolicy.scale(20.0D, 4.0D, 1,
                1.0D, 1.0D, tuning);
        check(close(levelOne.healthMultiplier(), 1.0D) && close(levelOne.damageMultiplier(), 1.0D),
                "level one uses baseline multipliers");
        final var levelFifty = MobProgressionPolicy.scale(20.0D, 4.0D, 50,
                1.0D, 1.0D, tuning);
        check(close(levelFifty.healthMultiplier(), Math.min(8.0D, 1.0D + 49.0D * 0.08D))
                        && close(levelFifty.damageMultiplier(), Math.min(3.0D, 1.0D + 49.0D * 0.025D)),
                "level fifty follows the authored HP/damage formulas");
        final var cap = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, null, null, 50, 20, 20, 20, 20, false), tuning);
        check(cap.level() == 70 && cap.capped(), "generic survival progression hard-caps at 70");
        final var authoredBoss = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                90, null, null, 1, 0, 0, 0, 0, true), tuning);
        check(authoredBoss.level() == 90
                        && authoredBoss.source() == MobProgressionPolicy.Source.ENCOUNTER_OVERRIDE,
                "authored boss display level may exceed the survival cap");
        final var bossCap = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                999, null, null, 1, 0, 0, 0, 0, true), tuning);
        check(bossCap.level() == 200 && bossCap.capped(), "authored boss level hard-caps at 200");
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
        final var encounter = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                42, 18, 40, 50, 9, 9, 9, 2, false), tuning);
        check(encounter.level() == 42 && encounter.appliedBonus() == 0
                        && encounter.source() == MobProgressionPolicy.Source.ENCOUNTER_OVERRIDE,
                "encounter override is authoritative and ignores lower geographic/context layers");
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
        final MobAbilityDefinition counterable = new MobAbilityDefinition("counterable_rune",
                MobAbilityDefinition.Kind.DELAYED_RUNE, 180L, 40L, 30L,
                4.0D, 7.0D, 0, MobAbilityDefinition.TargetRule.NEAREST_PLAYER, true,
                Set.of(MobRank.ELITE), Set.of(MobArchetype.CONTROLLER), Map.of());
        check(counterable.recoveryTicks() == 30L && counterable.interruptible()
                        && counterable.eligible(MobRank.ELITE, MobArchetype.CONTROLLER)
                        && !counterable.eligible(MobRank.VETERAN, MobArchetype.CONTROLLER),
                "technique eligibility, recovery and interrupt counterplay are explicit data");
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

    private static void behaviorProfilesCreateDistinctSpacingDecisions() {
        final MobBehaviorProfile bruiser = MobBehaviorProfile.defaults(MobArchetype.BRUISER);
        final MobBehaviorProfile ranged = MobBehaviorProfile.defaults(MobArchetype.RANGED);
        final MobBehaviorProfile skirmisher = MobBehaviorProfile.defaults(MobArchetype.SKIRMISHER);
        check(bruiser.preferredRange() < skirmisher.preferredRange()
                        && skirmisher.preferredRange() < ranged.preferredRange(),
                "archetype projection creates distinct close, cycling and ranged spacing");
        check(ranged.techniqueWeight(MobAbilityDefinition.Kind.RETREAT, 2.0D, 0.8D)
                        > ranged.techniqueWeight(MobAbilityDefinition.Kind.PROJECTILE_BURST,
                        2.0D, 0.8D),
                "ranged profile prefers retreat when crowded");
        check(bruiser.techniqueWeight(MobAbilityDefinition.Kind.LUNGE, 12.0D, 0.8D)
                        > bruiser.techniqueWeight(MobAbilityDefinition.Kind.RETREAT,
                        12.0D, 0.8D),
                "bruiser profile prefers closing over retreat at distance");
        expectFailure(() -> new MobBehaviorProfile(8.0D, 9.0D, 20.0D,
                0.0D, 0.0D, 0.0D, 1.0D, 1.0D),
                "minimum comfort range cannot exceed preferred range");
    }

    private static void contextualSelectionIsStableAndWeighted() {
        final List<ContextualWeightedSelector.Candidate<String>> candidates = List.of(
                new ContextualWeightedSelector.Candidate<>("common", "common", 3.0D),
                new ContextualWeightedSelector.Candidate<>("rare", "rare", 1.0D));
        final UUID stable = new UUID(0x1ce5L, 42L);
        final String first = ContextualWeightedSelector.select(candidates, stable, 17L);
        check(first.equals(ContextualWeightedSelector.select(candidates.reversed(), stable, 17L)),
                "weighted variant choice is stable and authoring-order independent");
        int common = 0;
        for (int index = 0; index < 20_000; index++) {
            if (ContextualWeightedSelector.select(candidates,
                    new UUID(0x1ce5L, index), 17L).equals("common")) common++;
        }
        check(common >= 14_600 && common <= 15_400,
                "seeded 3:1 natural weight remains within deterministic tolerance: " + common);
        final MobNaturalContext swamp = new MobNaturalContext(1.0D, Set.of("dimension:normal"),
                Set.of("territory:protected_city"), Map.of("biome:swamp", 4.0D), 3, true);
        check(swamp.eligible(Set.of("dimension:normal", "biome:swamp"))
                        && close(swamp.effectiveWeight(Set.of("dimension:normal", "biome:swamp")), 4.0D)
                        && !swamp.eligible(Set.of("dimension:normal", "territory:protected_city")),
                "context affinity weights eligible wilderness but protected-city exclusion fails closed");
    }

    private static void daylightProtectionSourcesCompose() {
        check(!DaylightProtectionPolicy.shouldBurn(true, true, false, false),
                "authored daylight source suppresses baseline burn");
        check(!DaylightProtectionPolicy.shouldBurn(true, false, true, true)
                        && !DaylightProtectionPolicy.shouldBurn(true, false, false, true),
                "removing territory protection cannot cancel an active event source");
        check(DaylightProtectionPolicy.shouldBurn(true, false, false, false),
                "removing the final source restores a burning baseline");
        check(!DaylightProtectionPolicy.shouldBurn(false, false, false, false),
                "a naturally non-burning carrier never gains burn from reconciliation");
    }

    private static void encounterSnapshotUsesDiminishingStableScaling() {
        final EncounterScalingPolicy.Tuning tuning = EncounterScalingPolicy.Tuning.defaults();
        final UUID encounter = UUID.fromString("00000000-0000-0000-0000-000000006001");
        for (final int count : List.of(1, 2, 5, 10, 60, 128)) {
            final var snapshot = EncounterScalingPolicy.snapshot(encounter, 1, players(count),
                    100.0D, 100.0D, 1L, tuning);
            final double expectedHealth = Math.min(12.0D,
                    1.0D + 0.65D * Math.pow(count - 1.0D, 0.8D));
            final double expectedDamage = Math.min(1.18D,
                    1.0D + 0.04D * (Math.log(count) / Math.log(2.0D)));
            check(close(snapshot.healthMultiplier(), expectedHealth),
                    "encounter HP formula matches at " + count + " players");
            check(close(snapshot.damageMultiplier(), expectedDamage),
                    "encounter damage formula matches at " + count + " players");
            check(snapshot.mechanicRateMultiplier() >= 1.0D
                            && snapshot.mechanicRateMultiplier() <= 1.5D,
                    "mechanic rate remains within 1.0-1.5x at " + count + " players");
            check(snapshot.participants().size() == count,
                    "start snapshot is immutable-sized at " + count + " players");
        }
        final var five = EncounterScalingPolicy.snapshot(encounter, 1, players(5),
                100.0D, 100.0D, 1L, tuning);
        check(five.healthMultiplier() < 5.0D,
                "five-player health remains below linear scaling");
        check(five.damageMultiplier() < 1.15D,
                "party size only nudges boss damage and avoids one-shot scaling");
        expectFailure(() -> EncounterScalingPolicy.snapshot(encounter, 1, players(129),
                100.0D, 100.0D, 1L, tuning), "participant snapshot rejects more than 128 players");
    }

    private static void contributionIsBoundedMeaningfulAndIdempotent() {
        final UUID encounter = UUID.fromString("00000000-0000-0000-0000-000000006101");
        final UUID player = UUID.fromString("00000000-0000-0000-0000-000000006102");
        final UUID ally = UUID.fromString("00000000-0000-0000-0000-000000006103");
        final UUID bystander = UUID.fromString("00000000-0000-0000-0000-000000006104");
        final ContributionLedger ledger = new ContributionLedger(encounter, 1_000L, Set.of(player));
        check(!ledger.recordDamage(player, 50.0D, 999L),
                "pre-combat padding is rejected");
        check(!ledger.recordSupport(ally, bystander, 20.0D, 1_050L),
                "support on a nonparticipant/inactive target is rejected");
        check(ledger.recordDamage(player, 120.0D, 1_100L), "boss damage contributes");
        check(ledger.recordTanking(player, 40.0D, 1_150L), "tanking contributes at lower weight");
        check(!ledger.recordSupport(player, player, 500.0D, 1_200L),
                "self-heal/support farming is rejected");
        check(ledger.recordSupport(ally, player, 20.0D, 1_250L),
                "real support to an encounter-active ally can contribute");
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

    private static void equippedCombatPowerTracksLiveCanonicalGear() {
        final double naked = EquippedCombatPowerModel.estimate(1, List.of());
        final var starter = gear(hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND,
                8, hu.taliann.icesmp.itemization.ItemRarity.UNCOMMON, 3.0D, 0.0D,
                Map.of("attack_damage", 1.0D), 0, 0, "");
        final var midArmor = gear(hu.taliann.icesmp.itemization.ItemTemplate.Slot.CHEST,
                28, hu.taliann.icesmp.itemization.ItemRarity.RARE, 0.0D, 8.0D,
                Map.of("max_health", 12.0D, "armor", 4.0D), 1, 0, "frost_guard");
        final var signature = gear(hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND,
                45, hu.taliann.icesmp.itemization.ItemRarity.LEGENDARY, 12.0D, 0.0D,
                Map.of("attack_damage", 8.0D, "ability_power", 10.0D), 1, 2, "frost_guard");
        final double starterPower = EquippedCombatPowerModel.estimate(8, List.of(starter));
        final double midPower = EquippedCombatPowerModel.estimate(28, List.of(starter, midArmor));
        final double ascendedPower = EquippedCombatPowerModel.estimate(50,
                List.of(signature, midArmor));
        check(naked < starterPower && starterPower < midPower && midPower < ascendedPower,
                "naked, starter, mid-game and ascended canonical equipment form a live power curve");
        check(ascendedPower <= 10_000.0D,
                "equipped CombatPower remains bounded and internal");
        check(EquippedCombatPowerModel.estimate(28, List.of(midArmor)) < ascendedPower,
                "mixed or ignored malformed slots cannot inherit the stronger item estimate");
    }

    private static EquippedCombatPowerModel.GearSignal gear(
            final hu.taliann.icesmp.itemization.ItemTemplate.Slot slot, final int level,
            final hu.taliann.icesmp.itemization.ItemRarity rarity,
            final double damage, final double armor, final Map<String, Double> stats,
            final int runes, final int signatureTier, final String setId) {
        return new EquippedCombatPowerModel.GearSignal(slot, level, rarity, damage, armor,
                Map.of(), stats, runes, signatureTier, setId);
    }

    private static void encounterRewardFaultMatrixIsFailClosed() {
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, 0)
                        == EncounterRewardRecoveryPolicy.Decision.DELIVER,
                "prepared exact-before reward retries delivery");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, 1)
                        == EncounterRewardRecoveryPolicy.Decision.COMMIT_WITNESS,
                "prepared exact-after witness commits without duplicate delivery");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.PREPARED, 2)
                        == EncounterRewardRecoveryPolicy.Decision.MANUAL_REVIEW,
                "duplicate prepared reward witnesses fail closed");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.COMMITTED, 1)
                        == EncounterRewardRecoveryPolicy.Decision.CLEANUP_ONLY,
                "committed reward retry only cleans its marker");
        check(EncounterRewardRecoveryPolicy.decide(
                        EncounterRewardRecoveryPolicy.ReceiptState.COMMITTED, 2)
                        == EncounterRewardRecoveryPolicy.Decision.MANUAL_REVIEW,
                "duplicate committed reward witnesses also fail closed");
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
