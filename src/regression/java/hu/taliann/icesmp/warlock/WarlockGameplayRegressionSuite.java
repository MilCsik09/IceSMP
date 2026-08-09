package hu.taliann.icesmp.warlock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Boszorkánymester runtime state. */
public final class WarlockGameplayRegressionSuite {

    private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-0000000007c1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000007c2");

    private static int assertions;

    private WarlockGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        debtIsBookedAndOnlyWorkedOff();
        curseGrimoireHoldsThree();
        soulThreadNamesOneVictim();
        embersBurstAndBuyOverheat();
        rosterIsABoundedTrio();
        cleanupLifecycle();
        debtAndAllowlistSourceContracts();
        System.out.println("Warlock gameplay regression suite passed. assertions=" + assertions);
    }

    private static void debtIsBookedAndOnlyWorkedOff() {
        final WarlockCombatState state = new WarlockCombatState();
        check(state.debt() == 0, "a fresh warlock owes nothing");
        check(state.addDebt(20, 100) == 20, "a pact books its debt");
        check(state.addDebt(1000, 100) == 100, "the debt is bounded by the ceiling");
        check(state.isDebtCeilingReached(100),
                "at the ceiling no further pact may be sealed");
        check(state.repayDebt(30) == 30, "repayment work reduces the debt");
        check(state.debt() == 70, "the repayment is exact");
        check(!state.isDebtCeilingReached(100), "below the ceiling the pacts open again");
        check(state.repayDebt(1000) == 70, "a repayment can never overshoot into credit");
        check(state.debt() == 0, "the debt bottoms out at zero, never negative");
        check(state.repayDebt(10) == 0, "there is nothing to repay at zero");
    }

    private static void curseGrimoireHoldsThree() {
        final WarlockCombatState state = new WarlockCombatState();
        final long t0 = 50_000L;
        check(WarlockCombatState.CURSE_SLOTS == 3, "the Átokgrimoár holds exactly three curses");
        check(state.inscribeCurse("gyotrelem", t0, 12_000L), "the first curse is inscribed");
        check(state.hasCurse("gyotrelem", t0 + 100L), "the curse is live");
        check(state.activeCurses(t0 + 100L) == 1, "one curse counts");
        check(state.inscribeCurse("gyotrelem", t0 + 1_000L, 12_000L)
                        && state.activeCurses(t0 + 1_100L) == 1,
                "re-inscribing refreshes the same page instead of taking another");
        check(state.inscribeCurse("romlas", t0, 12_000L)
                        && state.inscribeCurse("aszaly", t0, 12_000L),
                "the other two curses fill the grimoire");
        check(state.activeCurses(t0 + 100L) == 3, "all three pages are written");
        check(!state.inscribeCurse("negyedik", t0 + 100L, 12_000L),
                "a fourth distinct curse finds no room");
        check(!state.inscribeCurse("  ", t0, 12_000L), "a blank curse id is never inscribed");

        check(state.activeCurses(t0 + 13_000L) == 0, "curses expire on their own");
        check(state.inscribeCurse("negyedik", t0 + 13_000L, 12_000L),
                "an expired page is reusable");
    }

    private static void soulThreadNamesOneVictim() {
        final WarlockCombatState state = new WarlockCombatState();
        final long t0 = 100_000L;
        check(state.threadTarget(t0) == null, "no Lélekfonal before the first tie");
        state.tieThread(VICTIM, t0, 8_000L);
        check(VICTIM.equals(state.threadTarget(t0 + 1_000L)), "the thread names its victim");
        state.tieThread(OTHER, t0 + 2_000L, 8_000L);
        check(OTHER.equals(state.threadTarget(t0 + 2_100L)),
                "átkötés moves the thread — it always names exactly one victim");
        state.tieThread(null, t0 + 3_000L, 8_000L);
        check(OTHER.equals(state.threadTarget(t0 + 3_100L)),
                "a null tie never silently cuts the thread");
        state.cutThread();
        check(state.threadTarget(t0 + 3_200L) == null, "cutting drops the thread");
        state.tieThread(VICTIM, t0 + 4_000L, 8_000L);
        check(state.threadTarget(t0 + 20_000L) == null, "an untended thread expires");
    }

    private static void embersBurstAndBuyOverheat() {
        final WarlockCombatState state = new WarlockCombatState();
        final long t0 = 150_000L;
        check(state.addEmbers(1, 4) == 1, "a fire cast banks an ember");
        check(state.addEmbers(99, 4) == 4, "the embers are bounded at the maximum");
        check(state.spendEmbers() == 4, "the burst takes every ember");
        check(state.embers() == 0, "the burst empties the bank");
        check(state.spendEmbers() == 0, "an empty bank pays nothing");

        check(!state.isOverheated(t0), "no lockout without a maxed-out burst");
        state.enterOverheat(t0, 5_000L);
        check(state.isOverheated(t0 + 4_999L), "Túlhevülés holds for its window");
        check(!state.isOverheated(t0 + 5_001L),
                "Túlhevülés is a bounded, deterministic lockout — it always lifts");
    }

    private static void rosterIsABoundedTrio() {
        final WarlockCombatState state = new WarlockCombatState();
        check(WarlockCombatState.ROSTER_SLOTS == 3, "the roster is a fixed trio of demon kinds");
        check(state.callDemon("imp"), "the first demon kind joins the pact");
        check(state.hasDemon("imp"), "the kind is on the roster");
        check(!state.callDemon("imp"), "the same kind never takes a second slot");
        check(state.callDemon("szolga") && state.callDemon("infernal"),
                "the other two kinds fill the roster");
        check(state.rosterSize() == 3, "the roster is full");
        check(!state.callDemon("negyedik"), "a fourth kind finds no slot");
        check(!state.callDemon("  "), "a blank kind id is never registered");
        check(state.dismissRoster() == 3, "dismissing empties the pact in one act");
        check(state.rosterSize() == 0, "the roster is empty afterwards");
        check(state.dismissRoster() == 0, "an empty roster dismisses nothing");
    }

    private static void cleanupLifecycle() {
        final WarlockCombatState state = new WarlockCombatState();
        final long t0 = 200_000L;
        state.addDebt(60, 100);
        state.inscribeCurse("romlas", t0, 12_000L);
        state.tieThread(VICTIM, t0, 8_000L);
        state.addEmbers(3, 4);
        state.enterOverheat(t0, 5_000L);
        state.callDemon("imp");
        state.clearSpecializationState();
        check(state.debt() == 0, "spec switch writes off the Lélekadósság with its pacts");
        check(state.activeCurses(t0) == 0, "spec switch clears the Átokgrimoár");
        check(state.threadTarget(t0) == null, "spec switch cuts the Lélekfonal");
        check(state.embers() == 0 && !state.isOverheated(t0),
                "spec switch clears the embers and the lockout");
        check(state.rosterSize() == 0, "spec switch empties the demon roster");
    }

    private static void debtAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"warrior\", \"evoker\", \"archer\", \"shaman\", "
                        + "\"monk\", \"paladin\", \"demon_hunter\",")
                        && policy.contains("\"death_knight\", \"assassin\", \"warlock\")"),
                "gameplay-v2 allowlist is exactly the completed slices");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/warlock/WarlockGameplayService.java"));
        // Real coupling only: the javadoc deliberately names these systems to say it avoids them.
        for (final String economic : new String[]{"CurrencyManager", "BankManager", "EconomyManager",
                "MarketManager", "AuctionManager", "getBalance(", "deposit(", "withdraw(",
                "import hu.taliann.icesmp.managers.Currency", "import hu.taliann.icesmp.managers.Bank"}) {
            check(!service.contains(economic),
                    "the Lélekadósság never touches an economic system (" + economic + ")");
        }
        check(!service.contains("runAtFixedRate") && !service.contains("getNearbyEntities"),
                "no repeating tasks or proximity scans in the warlock runtime");

        final String state = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/warlock/WarlockCombatState.java"));
        check(state.contains("The debt never decays") || state.contains("never decays on its own"),
                "the debt is worked off deliberately, never decayed away");

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        check(gameplayConfig.contains("debt-ceiling: 100") && gameplayConfig.contains("repay-spells:"),
                "the debt ceiling and the repayment path are admin-tunable live config");

        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"));
        for (final String trial : new String[]{"warlock_affliction_trial",
                "warlock_destruction_trial", "warlock_demonologist_trial"}) {
            check(manager.contains(trial), "the capstone trial contract " + trial + " is registered");
        }

        final String catalog = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/domain/ClassSpecCatalog.java"));
        check(catalog.contains("\"demonologist\", \"demonologist.roster\""),
                "Demonológus keeps the demonologist.roster companion namespace");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
