package hu.taliann.icesmp.deathknight;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior regression for the concrete Halállovag runtime state. */
public final class DeathKnightGameplayRegressionSuite {

    private static int assertions;

    private DeathKnightGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        runeWheelSpendsAndRechargesLazily();
        deathRuneOnlyComesFromTransmutation();
        bloodMemoryIsBoundedAndSpentWhole();
        frostMarksConsumePartiallyOrFully();
        plagueBurstsAndMutatesTheGhoul();
        cleanupLifecycle();
        runeAndAllowlistSourceContracts();
        System.out.println("Death Knight gameplay regression suite passed. assertions=" + assertions);
    }

    private static void runeWheelSpendsAndRechargesLazily() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 10_000L;
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 0,
                "an unprimed wheel is empty");
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 2,
                "priming fills the self-recharging runes");
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L) == 0,
                "priming never hands out a Halál rune");
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 2,
                "priming twice cannot refill the wheel");

        check(state.spendRune(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L),
                "a declared spender takes its rune");
        check(state.spendRune(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L),
                "the second Vér rune is still there");
        check(!state.spendRune(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L),
                "an empty rune cannot be spent");
        check(state.runes(DeathKnightCombatState.Rune.FAGY, 2, t0, 6_000L) == 2,
                "spending Vér never touches Fagy");

        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0 + 5_999L, 6_000L) == 0,
                "a rune needs its full recharge step");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0 + 6_000L, 6_000L) == 1,
                "one step restores exactly one rune");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0 + 60_000L, 6_000L) == 2,
                "the recharge stops at the capacity");
    }

    private static void deathRuneOnlyComesFromTransmutation() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 50_000L;
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0 + 600_000L, 6_000L) == 0,
                "the Halál rune never recharges on its own — waiting yields nothing");
        check(state.transmuteToDeath(2, 1, t0, 6_000L),
                "a deliberate transmutation forges the Halál rune");
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L) == 1,
                "the Halál rune is on the wheel");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 1,
                "the transmutation was paid for with a natural rune");
        check(!state.transmuteToDeath(2, 1, t0, 6_000L),
                "the Halál capacity bounds the transmutation");
        check(state.spendRune(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L),
                "the forged Halál rune funds its spender");
        check(!state.spendRune(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L),
                "a spent Halál rune is gone until it is forged again");

        final DeathKnightCombatState drained = new DeathKnightCombatState();
        drained.prime(1, t0);
        drained.spendRune(DeathKnightCombatState.Rune.VER, 1, t0, 6_000L);
        drained.spendRune(DeathKnightCombatState.Rune.FAGY, 1, t0, 6_000L);
        check(!drained.transmuteToDeath(1, 1, t0, 6_000L),
                "an empty wheel has nothing to transmute");
    }

    private static void bloodMemoryIsBoundedAndSpentWhole() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 100_000L;
        check(DeathKnightCombatState.memoryCapacity() == 8,
                "the Vér Emlékezete is a fixed-size ring, never a growing damage log");
        check(state.recentDamage(t0, 8_000L) == 0.0D, "an untouched knight remembers nothing");
        state.rememberDamage(4.0D, t0);
        state.rememberDamage(6.0D, t0 + 100L);
        check(state.recentDamage(t0 + 200L, 8_000L) == 10.0D, "recent hits sum up");
        state.rememberDamage(-5.0D, t0 + 200L);
        check(state.recentDamage(t0 + 300L, 8_000L) == 10.0D,
                "a non-positive hit is never remembered");

        for (int i = 0; i < 20; i++) state.rememberDamage(1.0D, t0 + 1_000L + i);
        check(state.recentDamage(t0 + 2_000L, 8_000L) == 8.0D,
                "the ring holds at most its capacity — the oldest entries are overwritten");

        check(state.recentDamage(t0 + 30_000L, 8_000L) == 0.0D,
                "hits outside the window no longer count");
        state.rememberDamage(9.0D, t0 + 30_000L);
        check(state.consumeMemory(t0 + 30_100L, 8_000L) == 9.0D, "the conversion cashes the memory");
        check(state.recentDamage(t0 + 30_200L, 8_000L) == 0.0D,
                "the memory is spent whole — it cannot be double-cashed");
    }

    private static void frostMarksConsumePartiallyOrFully() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        check(state.addFrostMarks(1, 5) == 1, "a frost cast leaves a Fagyjel");
        state.addFrostMarks(2, 5);
        check(state.addFrostMarks(9, 5) == 5, "Fagyjelek are bounded at the maximum");
        check(state.consumeFrostMarks(2) == 2, "a strike consumes part of the stack");
        check(state.frostMarks() == 3, "the partial consume leaves the rest standing");
        check(state.consumeFrostMarks(99) == 3, "a partial consume can never take more than there is");
        check(state.frostMarks() == 0, "the stack is empty, never negative");

        state.addFrostMarks(4, 5);
        check(state.consumeAllFrostMarks() == 4, "Zúzás takes the whole stack at once");
        check(state.frostMarks() == 0, "the full consume empties the stack");
        check(state.consumeAllFrostMarks() == 0, "an empty stack yields nothing");
    }

    private static void plagueBurstsAndMutatesTheGhoul() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        check(state.addPlague(1, 6) == 1, "an unholy cast plants a Dögvész mark");
        check(state.addPlague(99, 6) == 6, "Dögvész is bounded at the maximum");
        check(state.burstPlague() == 6, "the burst spends every mark");
        check(state.plague() == 0, "the burst empties the marks");
        check(state.burstPlague() == 0, "an empty burst yields nothing");

        check(state.mutation() == 0, "the ghoul starts unmutated");
        check(state.advanceMutation(3) == 1, "feeding advances the mutation one stage");
        state.advanceMutation(3);
        check(state.advanceMutation(3) == 3, "the mutation reaches its bounded final stage");
        check(state.advanceMutation(3) == 3, "the mutation can never exceed its maximum");
    }

    private static void cleanupLifecycle() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 200_000L;
        state.prime(2, t0);
        state.transmuteToDeath(2, 1, t0, 6_000L);
        state.rememberDamage(7.0D, t0);
        state.addFrostMarks(3, 5);
        state.addPlague(4, 6);
        state.advanceMutation(3);
        state.clearSpecializationState();
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L) == 0,
                "spec switch clears the Halál rune");
        check(state.recentDamage(t0, 8_000L) == 0.0D, "spec switch clears the blood memory");
        check(state.frostMarks() == 0, "spec switch clears the Fagyjelek");
        check(state.plague() == 0 && state.mutation() == 0,
                "spec switch clears the Dögvész and the ghoul mutation");
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 2,
                "the wheel can be primed again after a spec switch");
    }

    private static void runeAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"warrior\", \"evoker\", \"archer\", \"shaman\", "
                        + "\"monk\", \"paladin\", \"demon_hunter\",")
                        && policy.contains("\"druid\", \"priest\", \"death_knight\")"),
                "gameplay-v2 allowlist is exactly the completed slices");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/deathknight/DeathKnightGameplayService.java"));
        check(!service.contains("runAtFixedRate") && !service.contains("getNearbyEntities"),
                "no repeating tasks or proximity scans in the death knight runtime");
        check(!service.contains("SoulforgeManager") && !service.contains("SoulforgeCommand")
                        && !service.contains("import hu.taliann.icesmp.managers.Soulforge"),
                "the ghoul never touches the Nekromanta Soulforge — the systems stay separate");
        check(!service.contains("spawnEntity") && !service.contains("SummonMinionsSpell"),
                "the ghoul is summoned by the EXISTING minion spells; the runtime only mutates it");

        final String catalog = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/domain/ClassSpecCatalog.java"));
        check(catalog.contains("\"unholy\", \"unholy.ghoul\""),
                "Szentségtelen keeps the unholy.ghoul companion namespace");

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        check(gameplayConfig.contains("classes: []"),
                "the melee-catalyst compatibility list is empty — every class casts through its Lélekkapocs");
        check(gameplayConfig.contains("transmute-spells:"),
                "the Halál rune source is declared, admin-tunable live config");

        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"));
        for (final String trial : new String[]{"death_knight_blood_trial",
                "death_knight_frost_trial", "death_knight_unholy_trial"}) {
            check(manager.contains(trial), "the capstone trial contract " + trial + " is registered");
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
