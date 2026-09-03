package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileWhisperStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Pure contract regressions for the meter-free faction/crime/Whisper rework. */
public final class FactionReworkRegressionSuite {

    private static int assertions;

    private FactionReworkRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        darkHealingTradeoffIsFixedAndContextual();
        whisperStagesAreFiniteAndReversibleBeforeExposure();
        evidenceIsExactExpiringAndSingleUse();
        runtimeSourcesContainNoRetiredMeterLoops();
        System.out.println("Faction rework regression suite passed. assertions=" + assertions);
    }

    private static void darkHealingTradeoffIsFixedAndContextual() {
        final FactionPassivePolicy policy = new FactionPassivePolicy();
        final FactionMembership dark = FactionMembership.citizen(FactionType.DARK);
        checkDouble(0.70D, policy.healingMultiplier(dark, false),
                "DARK everyday healing cost changed");
        checkDouble(1.0D, policy.healingMultiplier(dark, true),
                "high-stakes DARK healing was not exempt");
        checkDouble(1.0D, policy.healingMultiplier(
                FactionMembership.citizen(FactionType.RED), false),
                "DARK healing cost leaked to RED");
        checkDouble(1.0D, policy.healingMultiplier(FactionMembership.guest(), false),
                "DARK healing cost leaked to guests");
    }

    private static void whisperStagesAreFiniteAndReversibleBeforeExposure() {
        check(PlayerProfileWhisperStore.Stage.values().length == 4,
                "Whisper stage count changed");
        check(PlayerProfileWhisperStore.Stage.CLEAN.advance()
                        == PlayerProfileWhisperStore.Stage.OBSERVED,
                "first accusation stage changed");
        check(PlayerProfileWhisperStore.Stage.OBSERVED.advance()
                        == PlayerProfileWhisperStore.Stage.SUSPECTED,
                "second accusation stage changed");
        check(PlayerProfileWhisperStore.Stage.SUSPECTED.advance()
                        == PlayerProfileWhisperStore.Stage.EXPOSED,
                "third accusation no longer exposes");
        check(PlayerProfileWhisperStore.Stage.EXPOSED.advance()
                        == PlayerProfileWhisperStore.Stage.EXPOSED,
                "exposure overflowed");
        check(PlayerProfileWhisperStore.Stage.SUSPECTED.cover()
                        == PlayerProfileWhisperStore.Stage.OBSERVED,
                "cover does not remove exactly one stage");
        check(PlayerProfileWhisperStore.Stage.CLEAN.cover()
                        == PlayerProfileWhisperStore.Stage.CLEAN,
                "cover underflowed");
    }

    private static void evidenceIsExactExpiringAndSingleUse() {
        final AtomicLong clock = new AtomicLong(1_000L);
        final WhisperEvidenceLedger ledger = new WhisperEvidenceLedger(clock::get);
        final UUID witness = UUID.randomUUID();
        final UUID suspect = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        ledger.grant(witness, suspect, 500L);
        check(ledger.has(witness, suspect), "fresh evidence missing");
        check(!ledger.has(witness, other), "evidence leaked to another suspect");
        check(!ledger.has(other, suspect), "evidence leaked to another witness");
        check(ledger.consume(witness, suspect), "fresh evidence could not be consumed");
        check(!ledger.consume(witness, suspect), "evidence was reusable");
        ledger.grant(witness, suspect, 500L);
        clock.set(1_500L);
        check(!ledger.has(witness, suspect), "expired evidence remained valid");
        ledger.grant(witness, suspect, 500L);
        ledger.grant(other, suspect, 500L);
        ledger.clearPlayer(suspect);
        check(!ledger.has(witness, suspect) && !ledger.has(other, suspect),
                "suspect cleanup retained evidence");
    }

    private static void runtimeSourcesContainNoRetiredMeterLoops() throws Exception {
        final String core = read("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String king = read("src/main/java/hu/taliann/icesmp/commands/faction/FactionKingSubcommand.java");
        final String status = read("src/main/java/hu/taliann/icesmp/commands/faction/FactionStatusSubcommand.java");
        final String sins = read("src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileSinStore.java");
        final String whispers = read("src/main/java/hu/taliann/icesmp/managers/WhisperManager.java");
        check(!core.contains("scheduleTaxCollection") && !core.contains("taxTask"),
                "active tax scheduler remains");
        check(!core.contains("factionFoodListener::tick") && !core.contains("whisperManager::tick"),
                "retired food/Whisper tick remains");
        check(!king.contains("\"tax\""), "king tax command remains");
        check(status.contains("/faction status [eskü]") && status.contains("sealDarkPact"),
                "status/oath command contract missing");
        check(sins.contains("return count > 0") && sins.contains("boolean wanted")
                        && sins.contains("boolean exiled") && sins.contains("boolean darkPact"),
                "crime axes are not independent or sinner compatibility changed");
        check(!whispers.contains("suspicion") && !whispers.contains("decay")
                        && whispers.contains("recordAccusation"),
                "Whisper meter/decay remains or staged accusation is missing");
    }

    private static String read(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void checkDouble(final double expected, final double actual,
                                    final String message) {
        check(Math.abs(expected - actual) < 0.000_001D, message);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
