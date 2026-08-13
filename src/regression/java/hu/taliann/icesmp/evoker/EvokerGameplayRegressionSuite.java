package hu.taliann.icesmp.evoker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior + source-contract regression for the Evoker charge lifecycle. */
public final class EvokerGameplayRegressionSuite {

    private static int assertions;

    private EvokerGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        empowerRankBoundariesAndFizzle();
        empowerReplacementAndLifecycleCleanup();
        essenceAndPreparedHealState();
        typedPreparingTransactionContract();
        lifecycleWiringContract();
        System.out.println("Evoker gameplay regression suite passed. assertions=" + assertions);
    }

    private static void empowerRankBoundariesAndFizzle() {
        final EvokerCombatState state = new EvokerCombatState();
        final long t0 = 10_000L;
        check(state.releaseRank("fire_breath", t0, 1_200L, 2_400L, 6_000L) == 0,
                "no charge means no release");
        state.startCharge("fire_breath", t0);
        check(state.releaseRank("fire_breath", t0, 1_200L, 2_400L, 6_000L) == 1,
                "immediate release is Rank I");
        check(state.releaseRank("fire_breath", t0 + 1_199L, 1_200L, 2_400L, 6_000L) == 1,
                "Rank I upper boundary is exclusive");
        check(state.releaseRank("fire_breath", t0 + 1_200L, 1_200L, 2_400L, 6_000L) == 2,
                "Rank II starts exactly at its threshold");
        check(state.releaseRank("fire_breath", t0 + 2_399L, 1_200L, 2_400L, 6_000L) == 2,
                "Rank II remains active below Rank III");
        check(state.releaseRank("fire_breath", t0 + 2_400L, 1_200L, 2_400L, 6_000L) == 3,
                "Rank III starts exactly at its threshold");
        check(state.releaseRank("fire_breath", t0 + 6_000L, 1_200L, 2_400L, 6_000L) == 3,
                "fizzle boundary still permits the final Rank III release");
        check(state.releaseRank("fire_breath", t0 + 6_001L, 1_200L, 2_400L, 6_000L) == 0,
                "overheld charge fizzles");
        check(state.chargingSpellId().isEmpty(), "fizzle clears the charge session");
    }

    private static void empowerReplacementAndLifecycleCleanup() {
        final EvokerCombatState state = new EvokerCombatState();
        final long t0 = 50_000L;
        state.startCharge("fire_breath", t0);
        state.startCharge("eternity_surge", t0 + 500L);
        check(state.releaseRank("fire_breath", t0 + 700L, 1_200L, 2_400L, 6_000L) == 0,
                "a new empower session replaces the previous spell");
        check(state.releaseRank("eternity_surge", t0 + 700L, 1_200L, 2_400L, 6_000L) == 1,
                "replacement session is live");

        state.clearCharge();
        check(state.chargingSpellId().isEmpty(), "interrupt/cancel clears charge");
        state.startCharge("dream_breath", t0 + 1_000L);
        state.clearSpecializationState();
        check(state.chargingSpellId().isEmpty(), "spec/loadout transition clears charge");
        state.startCharge("spiritbloom", t0 + 2_000L);
        state.clearAll();
        check(state.chargingSpellId().isEmpty(), "death/logout/kick/disable cleanup clears charge");
    }

    private static void essenceAndPreparedHealState() {
        final EvokerCombatState state = new EvokerCombatState();
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4) == 1,
                "first essence cast starts resonance");
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4) == 2,
                "alternation builds resonance");
        check(state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4) == 1,
                "same color restarts the alternation");
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4);
        state.recordEssenceCast(EvokerCombatState.EssenceColor.KEK, 4);
        state.recordEssenceCast(EvokerCombatState.EssenceColor.VOROS, 4);
        check(state.isBurstArmed(4), "threshold arms the burst");
        state.consumeBurst(0);
        check(!state.isBurstArmed(4) && state.resonance() == 0,
                "burst consumption is single-use");

        final long t0 = 100_000L;
        state.armEcho(t0, 6_000L);
        check(state.consumeEcho(t0 + 1_000L), "armed echo is consumable");
        check(!state.consumeEcho(t0 + 1_001L), "echo cannot double-consume");
        state.recordImprint(16.0D, t0, 8_000L);
        check(state.consumeImprintRestore(t0 + 1_000L, 8.0D, 6.0D) == 14.0D,
                "imprint restore is bounded and heal-only");
        check(state.consumeImprintRestore(t0 + 1_001L, 8.0D, 6.0D) == 0.0D,
                "imprint is single-use");

        state.setMarkedAlly(UUID.randomUUID(), "ally");
        state.clearSpecializationState();
        check(state.resonance() == 0, "spec transition clears resonance");
        check(!state.isEchoArmed(t0), "spec transition clears echo");
        check(!state.isImprintAlive(t0), "spec transition clears imprint");
        check(state.markedAllyId().isEmpty(), "spec transition clears marked ally");
    }

    /**
     * PREPARING is a real transaction phase: the first empower input exits before
     * affordability/reservation/execution and therefore cannot consume cost, create
     * cooldown/combo/stat history or call afterCast. Only a committing typed outcome
     * reaches the class commit and bookkeeping below it.
     */
    private static void typedPreparingTransactionContract() throws Exception {
        final String listener = normalized("src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java");
        check(listener.contains("classHooks.put(JobType.EVOKER, new ClassCastHook("),
                "Evoker is routed through the class-hook registry");
        check(listener.contains("? PreparationResult.READY : PreparationResult.PREPARING"),
                "first empower input maps to PREPARING instead of a fake failed cast");
        final int prepare = listener.indexOf("final PreparationResult preparation = prepareClassCast");
        final int preparingReturn = listener.indexOf("if (preparation != PreparationResult.READY) return;", prepare);
        final int affordability = listener.indexOf("final boolean useResource", preparingReturn);
        final int reservation = listener.indexOf("final CostReservation reservation", affordability);
        final int execution = listener.indexOf("selected.cast(player, CastModifiers.standardPower(power))", reservation);
        final int outcomeGate = listener.indexOf("!outcome.commitsCast()", execution);
        final int classCommit = listener.indexOf("hook.commit().commit", outcomeGate);
        final int cooldown = listener.indexOf("putCooldown(player, selected", classCommit);
        final int stats = listener.indexOf("stats.recordSpellCast", cooldown);
        check(prepare >= 0 && preparingReturn > prepare && affordability > preparingReturn,
                "PREPARING returns before affordability/cost reservation");
        check(reservation > affordability && execution > reservation,
                "cost is a reservation immediately around execution");
        check(outcomeGate > execution && classCommit > outcomeGate,
                "class afterCast state is unreachable for a non-committing outcome");
        check(cooldown > classCommit && stats > cooldown,
                "cooldown/combo/stat bookkeeping happens only after successful class commit");
        check(listener.contains("reservation.rollback();") && listener.contains("outcome == null || !outcome.commitsCast()"),
                "failed/no-effect execution rolls the reservation back");
    }

    private static void lifecycleWiringContract() throws Exception {
        final String service = normalized("src/main/java/hu/taliann/icesmp/evoker/EvokerGameplayService.java");
        check(service.contains("onPlayerDeath(final PlayerDeathEvent event) { clearPlayerState"),
                "death cancels Evoker transient state");
        check(service.contains("onQuit(final PlayerQuitEvent event) { clearPlayerState"),
                "logout cancels Evoker transient state");
        check(service.contains("onKick(final PlayerKickEvent event) { clearPlayerState"),
                "kick cancels Evoker transient state");
        check(service.contains("onPluginDisable(final PluginDisableEvent event)" ) && service.contains("shutdown();"),
                "plugin disable cancels all Evoker transient state");
        check(service.contains("state.clearSpecializationState();"),
                "specialization reconcile has an explicit charge/state cleanup path");

        final String adapter = normalized("src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java");
        check(adapter.contains("evoker.clearSpecializationState(playerId);"),
                "loadout switch clears an in-progress Evoker charge");
        check(adapter.contains("registerTransientOwner(evoker);"),
                "full seal/reset reconciliation owns Evoker transient cleanup");
    }

    private static String normalized(final String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
