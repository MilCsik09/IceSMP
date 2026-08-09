package hu.taliann.icesmp.evoker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Sárkányidéző runtime state. */
public final class EvokerGameplayRegressionSuite {

    private static int assertions;

    private EvokerGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        empowerChargeRanksAndFizzle();
        empowerChargeReplacementAndInterrupt();
        essenceAlternationAndBurst();
        essenceBurstRetentionDoctrine();
        echoWindowSingleConsumption();
        imprintIsHealOnlySingleUseAndBounded();
        markedAllyAndCleanupLifecycle();
        gameplayV2AllowlistSourceContracts();
        System.out.println("Evoker gameplay regression suite passed. assertions=" + assertions);
    }

    private static void empowerChargeRanksAndFizzle() {
        final EvokerCombatState state = new EvokerCombatState();
        final long t0 = 10_000L;
        check(state.releaseRank("fire_breath", t0, 1_200L, 2_400L, 6_000L) == 0,
                "no charge means no release rank");

        state.startCharge("fire_breath", t0);
        check(state.releaseRank("fire_breath", t0, 1_200L, 2_400L, 6_000L) == 1,
                "instant release is rank one");
        check(state.releaseRank("fire_breath", t0 + 1_199L, 1_200L, 2_400L, 6_000L) == 1,
                "held below the first threshold stays rank one");
        check(state.releaseRank("fire_breath", t0 + 1_200L, 1_200L, 2_400L, 6_000L) == 2,
                "first hold threshold reaches rank two");
        check(state.releaseRank("fire_breath", t0 + 2_400L, 1_200L, 2_400L, 6_000L) == 3,
                "second hold threshold reaches rank three");
        check(state.releaseRank("eternity_surge", t0 + 100L, 1_200L, 2_400L, 6_000L) == 0,
                "a charge is bound to one concrete spell");

        check(state.releaseRank("fire_breath", t0 + 6_001L, 1_200L, 2_400L, 6_000L) == 0,
                "overheld charge fizzles instead of releasing");
        check(state.chargingSpellId().isEmpty(), "fizzled charge is fully cleared");
        state.startCharge("fire_breath", t0 + 7_000L);
        check(state.releaseRank("fire_breath", t0 + 7_100L, 1_200L, 2_400L, 6_000L) == 1,
                "fizzle allows a clean restart");
    }

    private static void empowerChargeReplacementAndInterrupt() {
        final EvokerCombatState state = new EvokerCombatState();
        final long t0 = 50_000L;
        state.startCharge("fire_breath", t0);
        state.startCharge("eternity_surge", t0 + 500L);
        check(state.releaseRank("fire_breath", t0 + 600L, 1_200L, 2_400L, 6_000L) == 0,
                "a new charge replaces the previous spell's charge");
        check(state.releaseRank("eternity_surge", t0 + 600L, 1_200L, 2_400L, 6_000L) == 1,
                "the replacing charge is live");

        state.clearCharge();
        check(state.releaseRank("eternity_surge", t0 + 700L, 1_200L, 2_400L, 6_000L) == 0,
                "an interrupting hit clears the held charge");

        state.startCharge("dream_breath", t0 + 1_000L);
        state.clearSpecializationState();
        check(state.chargingSpellId().isEmpty(),
                "a held charge never survives a spec switch");
    }

    private static void essenceAlternationAndBurst() {
        final EvokerCombatState state = new EvokerCombatState();
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4) == 1,
                "first essence cast starts the alternation");
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4) == 2,
                "alternating color builds resonance");
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4) == 1,
                "repeating a color restarts the alternation");
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4);
        state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4);
        check(!state.isBurstArmed(4), "three of four alternations is not yet a burst");
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4);
        check(state.isBurstArmed(4), "the configured threshold arms the burst");
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4) == 4,
                "resonance is bounded at the threshold");

        state.consumeBurst(0);
        check(state.resonance() == 0, "spent burst vents the resonance");
        check(state.lastEssenceColor().isEmpty(),
                "full vent restarts the alternation from scratch");
        check(!state.isBurstArmed(4), "spent burst cannot double-fire");
    }

    private static void essenceBurstRetentionDoctrine() {
        final EvokerCombatState state = new EvokerCombatState();
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 3);
        state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 3);
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 3);
        check(state.isBurstArmed(3), "lower doctrine threshold arms earlier");
        state.consumeBurst(2);
        check(state.resonance() == 2, "retention doctrine keeps partial resonance");
        check(state.lastEssenceColor().isPresent(),
                "retained resonance keeps the alternation context");
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 3) == 3,
                "retained resonance re-arms with a single alternation");
    }

    private static void echoWindowSingleConsumption() {
        final EvokerCombatState state = new EvokerCombatState();
        final long t0 = 100_000L;
        check(!state.consumeEcho(t0), "unarmed echo cannot be consumed");
        state.armEcho(t0, 6_000L);
        check(state.isEchoArmed(t0 + 5_999L), "echo stays armed inside its window");
        check(state.consumeEcho(t0 + 1_000L), "the next prepared heal consumes the echo");
        check(!state.consumeEcho(t0 + 1_001L), "echo is strictly single-use");
        state.armEcho(t0 + 10_000L, 6_000L);
        check(!state.consumeEcho(t0 + 16_001L), "expired echo never fires");
    }

    private static void imprintIsHealOnlySingleUseAndBounded() {
        final EvokerCombatState state = new EvokerCombatState();
        final long t0 = 200_000L;
        check(state.consumeImprintRestore(t0, 10.0D, 6.0D) == 0.0D,
                "no imprint means no restore");

        state.recordImprint(16.0D, t0, 8_000L);
        check(state.isImprintAlive(t0 + 7_999L), "imprint lives inside its window");
        check(state.imprintRemainingMillis(t0 + 3_000L) == 5_000L,
                "imprint remaining time is exact");
        check(state.consumeImprintRestore(t0 + 1_000L, 8.0D, 6.0D) == 14.0D,
                "restore is bounded by the configured gain cap");
        check(state.consumeImprintRestore(t0 + 1_100L, 8.0D, 6.0D) == 0.0D,
                "imprint is strictly single-use");

        state.recordImprint(16.0D, t0 + 2_000L, 8_000L);
        check(state.consumeImprintRestore(t0 + 3_000L, 18.0D, 6.0D) == 0.0D,
                "imprint never lowers health — heal-only by contract");

        state.recordImprint(16.0D, t0 + 20_000L, 8_000L);
        check(state.consumeImprintRestore(t0 + 28_001L, 4.0D, 6.0D) == 0.0D,
                "expired imprint cannot restore");
    }

    private static void markedAllyAndCleanupLifecycle() {
        final EvokerCombatState state = new EvokerCombatState();
        final UUID ally = UUID.randomUUID();
        state.setMarkedAlly(ally, "Gyógyítandó");
        check(state.markedAllyId().orElseThrow().equals(ally), "one marked ally is tracked");
        check(state.markedAllyLabel().equals("Gyógyítandó"), "mark keeps a readable label");
        state.setMarkedAlly(UUID.randomUUID(), "Másik");
        check(state.markedAllyLabel().equals("Másik"), "a new mark replaces the old one");

        final long t0 = 300_000L;
        state.startCharge("spiritbloom", t0);
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4);
        state.armEcho(t0, 6_000L);
        state.recordImprint(12.0D, t0, 8_000L);
        state.clearSpecializationState();
        check(state.chargingSpellId().isEmpty(), "spec switch clears the charge");
        check(state.resonance() == 0, "spec switch clears resonance");
        check(!state.isEchoArmed(t0), "spec switch clears the echo");
        check(!state.isImprintAlive(t0), "spec switch clears the imprint");
        check(state.markedAllyId().isEmpty(), "spec switch clears the marked ally");

        state.armEcho(t0, 6_000L);
        state.clearAll();
        check(!state.isEchoArmed(t0), "death/logout cleanup clears everything");
    }

    private static void gameplayV2AllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"warrior\", \"evoker\", \"archer\", \"shaman\", "
                        + "\"monk\", \"paladin\", \"demon_hunter\",")
                        && policy.contains("\"druid\")"),
                "gameplay-v2 allowlist is exactly the completed slices");

        final String gateway = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/DefaultClassSpecProfileGateway.java"));
        check(gateway.contains(
                        "GameplayV2ClassPolicy.isEnabled(p.primaryClassId())&&level>=r.secondSpecUnlockLevel()"),
                "second-slot XP unlock is allowlist-gated");
        check(gateway.contains("second-slot gameplay is enabled only for reworked classes"),
                "SECOND-slot learning stays fail-closed for unreworked classes");
        check(gateway.contains("loadout switching is enabled only for reworked classes"),
                "gateway loadout switching stays fail-closed for unreworked classes");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/evoker/EvokerGameplayService.java"));
        check(!service.contains("teleport") && !service.contains("getInventory().setItem")
                        && !service.contains("setContents") && !service.contains("giveExp"),
                "Időlenyomat rolls back health only — no position/inventory/xp rollback path");
        check(service.contains("consumeImprintRestore"),
                "imprint restore goes through the bounded heal-only state contract");

        final String catalyst = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java"));
        check(catalyst.contains("evoker.beforeCast(player, selected)"),
                "Felerősítés charge gate is wired into the shared cast pipeline");
        check(catalyst.contains("castPowerBonusPercent"),
                "empower/burst bonus rides the shared capped power pipeline");
        check(catalyst.contains("GameplayV2ClassPolicy.isEnabled(job.getId())) return false;"),
                "gameplay-v2 classes cannot bypass the personal Lélekkapocs with a melee catalyst");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
