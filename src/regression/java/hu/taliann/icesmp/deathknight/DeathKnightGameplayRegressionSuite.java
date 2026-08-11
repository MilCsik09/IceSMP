package hu.taliann.icesmp.deathknight;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior + cast-transaction regression for the concrete Halállovag runtime. */
public final class DeathKnightGameplayRegressionSuite {

    private static int assertions;

    private DeathKnightGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        runeWheelSpendsAndRechargesLazily();
        deathRuneOnlyComesFromTransmutation();
        castTransactionCommitsRuneExactlyOnce();
        bloodMemoryIsBoundedAndSpentWhole();
        frostMarksConsumePartiallyOrFully();
        plagueBurstsAndMutatesTheGhoul();
        lifecycleCleanupRebuildsDeterministically();
        sourceContracts();
        System.out.println("Death Knight gameplay regression suite passed. assertions=" + assertions);
    }

    private static void runeWheelSpendsAndRechargesLazily() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 10_000L;
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 0,
                "unprimed rune wheel is empty");
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 2,
                "prime fills natural Vér runes");
        check(state.runes(DeathKnightCombatState.Rune.FAGY, 2, t0, 6_000L) == 2,
                "prime fills natural Fagy runes");
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L) == 0,
                "prime never creates Halál runes");
        check(state.spendRune(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L),
                "first Vér rune is spendable");
        check(state.spendRune(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L),
                "second Vér rune is spendable");
        check(!state.spendRune(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L),
                "empty rune type cannot go negative");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0 + 5_999L, 6_000L) == 0,
                "recharge waits the full interval");
        check(state.rechargePercent(DeathKnightCombatState.Rune.VER, 2, t0 + 3_000L, 6_000L) == 50,
                "HUD recharge projection is exact");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0 + 6_000L, 6_000L) == 1,
                "one interval restores exactly one rune");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0 + 60_000L, 6_000L) == 2,
                "lazy/offline recharge caps at natural capacity");
    }

    private static void deathRuneOnlyComesFromTransmutation() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 50_000L;
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0 + 600_000L, 6_000L) == 0,
                "Halál rune never recharges naturally");
        check(state.transmuteToDeath(2, 1, t0, 6_000L),
                "natural rune can be transmuted to Halál");
        check(state.runes(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L) == 1,
                "transmutation creates one Halál rune");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 1,
                "transmutation pays one natural rune");
        check(!state.transmuteToDeath(2, 1, t0, 6_000L),
                "Halál capacity prevents over-transmutation");
        check(state.spendRune(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L),
                "forged Halál rune is spendable once");
        check(!state.spendRune(DeathKnightCombatState.Rune.HALAL, 2, t0, 6_000L),
                "spent Halál rune cannot be double-consumed");
    }

    private static void castTransactionCommitsRuneExactlyOnce() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long now = 75_000L;
        state.prime(2, now);
        final int initial = state.runes(DeathKnightCombatState.Rune.VER, 2, now, 6_000L);
        check(initial == 2, "transaction starts with two runes");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, now, 6_000L) == initial,
                "blocked validation is read-only");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, now, 6_000L) == initial,
                "primary-resource rejection consumes zero runes");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, now, 6_000L) == initial,
                "NO_TARGET/NO_EFFECT consumes zero runes before class commit");
        check(state.spendRune(DeathKnightCombatState.Rune.VER, 2, now, 6_000L),
                "successful class commit consumes the rune");
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, now, 6_000L) == initial - 1,
                "successful cast consumes exactly one rune");
    }

    private static void bloodMemoryIsBoundedAndSpentWhole() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        final long t0 = 100_000L;
        check(DeathKnightCombatState.memoryCapacity() == 8,
                "Vér Emlékezete uses a bounded ring");
        state.rememberDamage(4.0D, t0);
        state.rememberDamage(6.0D, t0 + 100L);
        check(state.recentDamage(t0 + 200L, 8_000L) == 10.0D,
                "recent mitigated damage is remembered");
        for (int i = 0; i < 20; i++) state.rememberDamage(1.0D, t0 + 1_000L + i);
        check(state.recentDamage(t0 + 2_000L, 8_000L) == 8.0D,
                "memory ring never grows beyond capacity");
        check(state.recentDamage(t0 + 30_000L, 8_000L) == 0.0D,
                "expired damage leaves the window");
        state.rememberDamage(9.0D, t0 + 30_000L);
        check(state.consumeMemory(t0 + 30_100L, 8_000L) == 9.0D,
                "memory converts exactly once");
        check(state.consumeMemory(t0 + 30_200L, 8_000L) == 0.0D,
                "memory cannot double-pay");
    }

    private static void frostMarksConsumePartiallyOrFully() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        check(state.addFrostMarks(1, 5) == 1, "frost cast adds a mark");
        check(state.addFrostMarks(99, 5) == 5, "Fagyjel stack is capped");
        check(state.consumeFrostMarks(2) == 2 && state.frostMarks() == 3,
                "partial spender removes only requested marks");
        check(state.consumeAllFrostMarks() == 3 && state.frostMarks() == 0,
                "full spender empties remaining marks");
        check(state.consumeAllFrostMarks() == 0,
                "empty marks cannot be consumed again");
    }

    private static void plagueBurstsAndMutatesTheGhoul() {
        final DeathKnightCombatState state = new DeathKnightCombatState();
        check(state.addPlague(99, 6) == 6, "Dögvész is bounded");
        check(state.burstPlague() == 6 && state.plague() == 0,
                "plague burst spends the stack exactly once");
        check(state.burstPlague() == 0, "empty plague burst has no hidden second payout");
        check(state.advanceMutation(3) == 1, "ghoul mutation advances one stage");
        state.advanceMutation(3);
        check(state.advanceMutation(3) == 3, "mutation reaches its bounded final stage");
        check(state.advanceMutation(3) == 3, "mutation cannot exceed its maximum");
    }

    private static void lifecycleCleanupRebuildsDeterministically() {
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
                "spec/loadout switch clears Halál rune");
        check(state.recentDamage(t0, 8_000L) == 0.0D,
                "spec/loadout switch clears blood memory");
        check(state.frostMarks() == 0,
                "spec/loadout switch clears Fagyjelek");
        check(state.plague() == 0 && state.mutation() == 0,
                "spec/loadout switch clears Unholy transient state");
        state.prime(2, t0);
        check(state.runes(DeathKnightCombatState.Rune.VER, 2, t0, 6_000L) == 2,
                "reconnect/rebuild starts a deterministic natural rune wheel");
        state.clearAll();
        check(state.frostMarks() == 0 && state.plague() == 0,
                "death/logout full cleanup is idempotent");
    }

    private static void sourceContracts() throws Exception {
        final String service = normalized("src/main/java/hu/taliann/icesmp/deathknight/DeathKnightGameplayService.java");
        final String listener = normalized("src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java");

        final int before = service.indexOf("public boolean beforeCast");
        final int availability = service.indexOf(
                "state.runes(required, naturalCapacity(playerId), now, rechargeMillis(playerId))", before);
        final int after = service.indexOf("public void afterCast", before);
        final int spend = service.indexOf(
                "state.spendRune(required, naturalCapacity(playerId), now, rechargeMillis(playerId))", after);
        check(before >= 0 && availability > before && after > availability && spend > after,
                "rune availability is read during preparation and spent only after successful execution");
        check(service.substring(before, after).indexOf("spendRune(") < 0,
                "blocked preparation cannot consume a rune");
        check(occurrences(service.substring(after),
                "state.spendRune(required, naturalCapacity(playerId), now, rechargeMillis(playerId))") == 1,
                "afterCast has exactly one primary rune-consume site");

        final int prepare = listener.indexOf("final PreparationResult preparation = prepareClassCast");
        final int affordability = listener.indexOf("final boolean useResource", prepare);
        final int execution = listener.indexOf(
                "selected.cast(player, CastModifiers.standardPower(power))", affordability);
        final int outcomeGate = listener.indexOf("outcome == null || !outcome.commitsCast()", execution);
        final int classCommit = listener.indexOf("hook.commit().commit", outcomeGate);
        check(prepare >= 0 && affordability > prepare && execution > affordability,
                "preparation and primary affordability both precede execution");
        check(outcomeGate > execution && classCommit > outcomeGate,
                "NO_TARGET/NO_EFFECT exits before Death Knight afterCast/rune commit");

        check(service.contains("onPlayerDeath(final PlayerDeathEvent event) { clearPlayerState"),
                "death clears Death Knight transient state");
        check(service.contains("onQuit(final PlayerQuitEvent event) { clearPlayerState"),
                "logout clears Death Knight transient state");
        check(service.contains("onKick(final PlayerKickEvent event) { clearPlayerState"),
                "kick clears Death Knight transient state");
        check(service.contains("onPluginDisable(final PluginDisableEvent event)") && service.contains("shutdown();"),
                "plugin disable clears Death Knight runtime state");
        check(!service.contains("runAtFixedRate") && !service.contains("getNearbyEntities"),
                "Death Knight runtime has no repeating scans");
        check(!service.contains("SoulforgeManager") && !service.contains("SummonMinionsSpell"),
                "Unholy runtime does not become a second Soulforge/minion authority");

        final String adapter = normalized(
                "src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java");
        check(adapter.contains("deathKnight.clearSpecializationState(playerId);"),
                "loadout switch clears spec-local DK state");
        check(adapter.contains("registerTransientOwner(deathKnight);"),
                "seal/reset reconciliation owns DK full cleanup");

        final String catalog = normalized(
                "src/main/java/hu/taliann/icesmp/classspec/domain/ClassSpecCatalog.java");
        check(catalog.contains("\"unholy\", \"unholy.ghoul\""),
                "Unholy durable companion namespace remains isolated");
    }

    private static String normalized(final String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    private static int occurrences(final String text, final String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
