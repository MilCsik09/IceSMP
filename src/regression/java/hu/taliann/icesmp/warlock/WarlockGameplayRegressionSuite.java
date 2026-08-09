package hu.taliann.icesmp.warlock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
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
        theCombatStateHoldsNoDemonAuthority();
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

    /**
     * The Demonológus pact has exactly one truth source, and it is not here. Proven structurally
     * rather than by grep: no field and no member of the transient combat state may hold, count or
     * name a demon, so there is nothing that could drift away from the durable roster.
     */
    private static void theCombatStateHoldsNoDemonAuthority() {
        for (final Field field : WarlockCombatState.class.getDeclaredFields()) {
            final String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            check(!name.contains("demon") && !name.contains("roster") && !name.contains("legion"),
                    "no transient field may name a demon: " + field.getName());
            final Class<?> type = field.getType();
            final boolean container = type.isArray() || Collection.class.isAssignableFrom(type)
                    || Map.class.isAssignableFrom(type);
            check(!container || name.startsWith("curse") || name.startsWith("attunement"),
                    "the only containers left are the curse grimoire slots: " + field.getName());
        }
        for (final Method method : WarlockCombatState.class.getDeclaredMethods()) {
            final String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            check(!name.contains("demon") && !name.contains("roster"),
                    "no transient member may serve a demon roster: " + method.getName());
        }
        for (final Class<?> nested : WarlockCombatState.class.getDeclaredClasses()) {
            check(!nested.getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("demon"),
                    "no nested demon holder either: " + nested.getSimpleName());
        }

        // Whatever the state still carries must survive a spec switch cleanly on its own terms.
        final WarlockCombatState state = new WarlockCombatState();
        state.addDebt(40, 100);
        state.clearSpecializationState();
        check(state.debt() == 0, "the surviving state still clears itself on a spec switch");
    }

    private static void cleanupLifecycle() {
        final WarlockCombatState state = new WarlockCombatState();
        final long t0 = 200_000L;
        state.addDebt(60, 100);
        state.inscribeCurse("romlas", t0, 12_000L);
        state.tieThread(VICTIM, t0, 8_000L);
        state.addEmbers(3, 4);
        state.enterOverheat(t0, 5_000L);
        state.clearSpecializationState();
        check(state.debt() == 0, "spec switch writes off the Lélekadósság with its pacts");
        check(state.activeCurses(t0) == 0, "spec switch clears the Átokgrimoár");
        check(state.threadTarget(t0) == null, "spec switch cuts the Lélekfonal");
        check(state.embers() == 0 && !state.isOverheated(t0),
                "spec switch clears the embers and the lockout");
    }

    private static void debtAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"warlock\""),
                "the gameplay-v2 allowlist still admits this completed slice");

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

        // The pact runs through the ONE companion gateway, durable-first, and never counts itself.
        check(service.contains("gateway.bindDemonV2(player, kind, rosterCapacity(")
                        && service.contains("gateway.releaseDemonRosterV2(player)"),
                "binding and release both go through the existing PetManager companion gateway");
        check(service.contains("gateway.demonRoster(player)")
                        && service.contains("boundDemons(player).size()"),
                "the roster size the gameplay reads is the durable projection");
        check(!service.contains("rosterSize()") && !service.contains("callDemon(")
                        && !service.contains("dismissRoster()"),
                "no transient roster call survives in the service");

        final String pets = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/PetManager.java"));
        check(pets.contains("return companionRoster(player, DEMON_ROSTER);")
                        && pets.contains("return ClassSpecCatalog.companionProjection("
                        + "currentLoadout(player).orElse(null), namespace);"),
                "the demon projection is the one shared companion projection rule");
        final int bindIndex = pets.indexOf("PetMutationResult> bindDemonV2");
        final int bindCommit = pets.indexOf("mutateCompanion", bindIndex);
        final int bindSpawn = pets.indexOf("spawnAndAdopt", bindIndex);
        check(bindIndex > 0 && bindCommit > bindIndex && bindSpawn > bindCommit,
                "the demon is embodied only after the durable companion mutation is issued");
        // The pact ceiling must hold on EVERY path into the roster, not just the spell path.
        final int ritualIndex = pets.indexOf("PetMutationResult> ritualSummonV2");
        final int ritualGuard = pets.indexOf("ClassSpecCatalog.admitsCompanion(", ritualIndex);
        final int ritualCommit = pets.indexOf("mutateCompanion", ritualIndex);
        check(ritualIndex > 0 && ritualGuard > ritualIndex && ritualGuard < ritualCommit,
                "the /pet ritual answers to the same durable roster capacity as the pact spell");
        check(pets.indexOf("List.of(),Map.of(),rosterCapacity,operationId", ritualIndex) > ritualIndex,
                "and it carries that capacity into the commit, so the rule is re-checked there");

        final int releaseIndex = pets.indexOf("Integer> releaseDemonRosterV2");
        final int releaseCommit = pets.indexOf("mutateCompanion", releaseIndex);
        final int releaseDespawn = pets.indexOf("removeActive", releaseIndex);
        check(releaseIndex > 0 && releaseCommit > releaseIndex && releaseDespawn > releaseCommit,
                "the despawn only ever follows the durable removals");

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
