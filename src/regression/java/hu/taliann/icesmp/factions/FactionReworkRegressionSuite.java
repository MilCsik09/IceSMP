package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileWhisperStore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pure contract regressions for the meter-free faction/crime/Whisper rework. */
public final class FactionReworkRegressionSuite {

    private static int assertions;

    private FactionReworkRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        darkHealingTradeoffIsFixedAndContextual();
        whisperStagesAreFiniteAndReversibleBeforeExposure();

        runtimeSourcesContainNoRetiredMeterLoops();
        sightlineBoundariesAndQuorum();
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

    private static void runtimeSourcesContainNoRetiredMeterLoops() throws Exception {
        final String core = read("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String king = read("src/main/java/hu/taliann/icesmp/commands/faction/FactionKingSubcommand.java");
        final String status = read("src/main/java/hu/taliann/icesmp/commands/faction/FactionStatusSubcommand.java");
        final String sins = read("src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileSinStore.java");
        final String whispers = read("src/main/java/hu/taliann/icesmp/managers/WhisperManager.java");
        for (final String path : java.util.List.of(
                "managers/ClassHealthService.java", "warrior/WarriorGameplayService.java",
                "listeners/SignatureItemListener.java")) {
            check(read("src/main/java/hu/taliann/icesmp/" + path).contains("SpellHealingUtil.heal("),
                    "receiving healing bypass remains in " + path);
        }
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

    private static void sightlineBoundariesAndQuorum() {
        final var boundary = WhisperSightline.cells(15.5, 64.5, 0.5, 17.5, 64.5, 0.5);
        check(boundary.equals(java.util.List.of(new WhisperSightline.Cell(15, 64, 0),
                new WhisperSightline.Cell(16, 64, 0), new WhisperSightline.Cell(17, 64, 0))),
                "chunk-border ray skipped the foreign wall cell");
        final var negative = WhisperSightline.cells(0.5, 64.5, 0.5, -1.5, 64.5, 0.5);
        check(negative.contains(new WhisperSightline.Cell(-1, 64, 0))
                && negative.contains(new WhisperSightline.Cell(-2, 64, 0)), "negative coordinates must floor, not truncate");
        check(WhisperSightline.cells(0, 0, 0, -0.0, 0, 0).size() == 1, "stationary signed-zero ray terminates");
        check(WhisperSightline.cells(0, 0, 0, 36, 36, 36).size() < 200, "diagonal traversal is bounded");
        boolean rejected = false;
        try { WhisperSightline.cells(0, 0, 0, 65, 0, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "unbounded witness radius rejected");
        check(hu.taliann.icesmp.managers.KingManager.electionQuorum(2, 2) == 2, "small faction quorum");
        check(hu.taliann.icesmp.managers.KingManager.electionQuorum(2, 12) == 4, "medium faction quorum");
        check(hu.taliann.icesmp.managers.KingManager.electionQuorum(2, 60) == 20, "large faction quorum prevents two-account election");
        check(hu.taliann.icesmp.managers.KingManager.electionQuorum(5, 2) == 5, "operator minimum preserved");
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
