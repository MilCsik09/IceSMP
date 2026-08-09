package hu.taliann.icesmp.assassin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Orgyilkos runtime state. */
public final class AssassinGameplayRegressionSuite {

    private static final UUID PREY = UUID.fromString("00000000-0000-0000-0000-0000000006b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000006b2");

    private static int assertions;

    private AssassinGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        opportunityOpensFromEveryOpeningAndIsSpentWhole();
        toxinKitHoldsExactlyThreeSlots();
        stealthIsStrictlyFinite();
        trailCarriesOneEcho();
        infectionRegistryIsHardCapped();
        cleanupLifecycle();
        plagueCapAndAllowlistSourceContracts();
        System.out.println("Assassin gameplay regression suite passed. assertions=" + assertions);
    }

    private static void opportunityOpensFromEveryOpeningAndIsSpentWhole() {
        final AssassinCombatState state = new AssassinCombatState();
        final long t0 = 10_000L;
        check(!state.isOpportunityOpen(t0), "no finisher window without an opening");
        check(state.consumeOpportunity(t0) == null, "an absent window consumes nothing");

        for (final AssassinCombatState.Opening opening : AssassinCombatState.Opening.values()) {
            final AssassinCombatState fresh = new AssassinCombatState();
            fresh.armOpportunity(opening, t0, 5_000L);
            check(fresh.isOpportunityOpen(t0 + 4_999L),
                    "the " + opening + " opening arms the window");
            check(fresh.opening(t0 + 1_000L) == opening,
                    "the window remembers which opening paid for it");
            check(fresh.consumeOpportunity(t0 + 1_000L) == opening,
                    "the finisher spends exactly that opening");
            check(!fresh.isOpportunityOpen(t0 + 1_100L),
                    "the window is spent whole — a second finisher finds nothing");
        }

        state.armOpportunity(AssassinCombatState.Opening.POZICIO, t0, 5_000L);
        check(state.opening(t0 + 5_001L) == null, "an unused window expires on its own");
        final AssassinCombatState nullArm = new AssassinCombatState();
        nullArm.armOpportunity(null, t0, 5_000L);
        check(!nullArm.isOpportunityOpen(t0), "a null opening never arms anything");
    }

    private static void toxinKitHoldsExactlyThreeSlots() {
        final AssassinCombatState state = new AssassinCombatState();
        check(AssassinCombatState.TOXIN_SLOTS == 3, "the Toxinkészlet is exactly three slots");
        check(state.applyToxin("benito", 3), "the first toxin takes a slot");
        check(state.dose("benito") == 1, "a fresh toxin starts at dose one");
        check(state.applyToxin("benito", 3) && state.dose("benito") == 2,
                "the same toxin deepens its dose instead of taking a second slot");
        check(state.filledToxinSlots() == 1, "deepening never consumes another slot");
        state.applyToxin("benito", 3);
        state.applyToxin("benito", 3);
        check(state.dose("benito") == 3, "the dose is bounded at its maximum");

        check(state.applyToxin("marokod", 3) && state.applyToxin("sorvaszto", 3),
                "the other two toxins fill the remaining slots");
        check(state.filledToxinSlots() == 3, "all three slots are taken");
        check(!state.applyToxin("negyedik", 3),
                "a fourth toxin is refused — the kit must be catalysed first");
        check(state.totalDose() == 5, "the total dose sums every slot");

        check(state.heldToxins().size() == 3, "the held trio is readable for the catalyst");
        check(state.catalyse() == 5, "catalysing burns the whole kit and reports its dose");
        check(state.filledToxinSlots() == 0 && state.totalDose() == 0,
                "the kit is empty after catalysing");
        check(state.catalyse() == 0, "an empty kit catalyses nothing");
        check(!state.applyToxin("  ", 3), "a blank toxin id is never stored");
    }

    private static void stealthIsStrictlyFinite() {
        final AssassinCombatState state = new AssassinCombatState();
        final long t0 = 50_000L;
        check(!state.isStealthed(t0, 60, 4_000L, 8.0D), "the phantom does not start hidden");
        state.enterStealth(t0, 8_000L);
        check(state.isStealthed(t0 + 7_999L, 60, 4_000L, 8.0D),
                "stealth holds inside its window");
        check(!state.isStealthed(t0 + 8_001L, 60, 4_000L, 8.0D),
                "stealth is time-boxed — it always ends on its own");

        state.enterStealth(t0 + 9_000L, 8_000L);
        state.addDetection(70, t0 + 9_000L, 4_000L, 8.0D);
        check(!state.isStealthed(t0 + 9_100L, 60, 4_000L, 8.0D),
                "detection above the threshold breaks stealth immediately");
        state.enterStealth(t0 + 20_000L, 8_000L);
        state.breakStealth();
        check(!state.isStealthed(t0 + 20_100L, 60, 4_000L, 8.0D),
                "a strike breaks stealth outright");

        final AssassinCombatState quiet = new AssassinCombatState();
        check(quiet.addDetection(30, t0, 4_000L, 8.0D) == 30, "loud acts raise Észleltség");
        check(quiet.addDetection(1000, t0, 4_000L, 8.0D) == 100, "Észleltség is bounded at 100");
        check(quiet.detection(t0 + 3_999L, 4_000L, 8.0D) == 100,
                "Észleltség holds inside the grace window");
        check(quiet.detection(t0 + 9_000L, 4_000L, 8.0D) == 60,
                "idle Észleltség decays lazily");
        check(quiet.ventDetection(1000) == 60, "a vent can never take more than is there");
    }

    private static void trailCarriesOneEcho() {
        final AssassinCombatState state = new AssassinCombatState();
        final long t0 = 100_000L;
        check(!state.consumeEcho(t0), "no Visszhang without an Árnyéknyom");
        state.armTrail(t0, 5_000L);
        check(state.isTrailLive(t0 + 4_999L), "the trail stays live inside its window");
        check(state.consumeEcho(t0 + 1_000L), "the trail carries a Visszhang");
        check(!state.consumeEcho(t0 + 1_100L), "the trail carries exactly one Visszhang");
        state.armTrail(t0 + 2_000L, 5_000L);
        check(state.consumeEcho(t0 + 2_100L), "a new trail carries a fresh Visszhang");
        check(!state.consumeEcho(t0 + 20_000L), "an expired trail echoes nothing");
    }

    private static void infectionRegistryIsHardCapped() {
        final AssassinCombatState state = new AssassinCombatState();
        final long t0 = 150_000L;
        check(state.infect(PREY, 2, t0, 15_000L), "the first carrier is registered");
        check(state.isInfected(PREY, t0 + 100L), "the carrier is tracked");
        check(!state.infect(PREY, 2, t0, 15_000L), "a carrier is never registered twice");
        check(state.infect(OTHER, 2, t0, 15_000L), "a second carrier fits under the cap");
        check(state.infectionCount(t0) == 2, "both carriers count");
        check(!state.infect(UUID.randomUUID(), 2, t0, 15_000L),
                "the entity cap is hard — nothing beyond it is ever registered");

        state.cure(PREY);
        check(!state.isInfected(PREY, t0) && state.infectionCount(t0) == 1,
                "a dead or cured carrier leaves the registry at once");
        check(state.infectionCount(t0 + 30_000L) == 0,
                "infections expire on their own — no stale ids linger");
        check(state.infect(UUID.randomUUID(), 2, t0 + 30_000L, 15_000L),
                "expired entries free their slot under the cap");
        check(state.infectedIds(t0 + 30_000L).size() == 1, "the registry reports its live ids");

        check(state.mutateStrain(3) == 1, "the strain mutates one stage at a time");
        state.mutateStrain(3);
        check(state.mutateStrain(3) == 3, "the strain reaches its bounded final stage");
        check(state.mutateStrain(3) == 3, "the strain can never exceed its maximum");
    }

    private static void cleanupLifecycle() {
        final AssassinCombatState state = new AssassinCombatState();
        final long t0 = 200_000L;
        state.armOpportunity(AssassinCombatState.Opening.INTERRUPT, t0, 5_000L);
        state.applyToxin("benito", 3);
        state.armTrail(t0, 5_000L);
        state.addDetection(50, t0, 4_000L, 8.0D);
        state.enterStealth(t0, 8_000L);
        state.mutateStrain(3);
        state.infect(PREY, 2, t0, 15_000L);
        state.clearSpecializationState();
        check(!state.isOpportunityOpen(t0), "spec switch clears the Lehetőség");
        check(state.filledToxinSlots() == 0, "spec switch clears the Toxinkészlet");
        check(!state.isTrailLive(t0), "spec switch clears the Árnyéknyom");
        check(state.detection(t0, 4_000L, 8.0D) == 0, "spec switch clears the Észleltség");
        check(!state.isStealthed(t0, 60, 4_000L, 8.0D), "spec switch ends any stealth");
        check(state.strainStage() == 0, "spec switch clears the strain");
        check(state.infectionCount(t0) == 0, "spec switch drops the whole infection registry");
    }

    private static void plagueCapAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"warrior\", \"evoker\", \"archer\", \"shaman\", "
                        + "\"monk\", \"paladin\", \"demon_hunter\",")
                        && policy.contains("\"druid\", \"priest\", \"death_knight\", \"assassin\")"),
                "gameplay-v2 allowlist is exactly the completed slices");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/assassin/AssassinGameplayService.java"));
        check(service.contains("moon.isActive()"),
                "plague infection stands down while a blood moon runs — event precedence");
        check(service.contains("MinionManager.isMinionTagged(target)")
                        && service.contains("WORLD_BOSS_KEY"),
                "plugin-owned/scripted and world-boss entities are immune");
        check(service.contains("guard.isBlocked(PLAGUE_GUARD_KEY"),
                "the existing spawn guard enforces the dungeon/claim/territory policy");
        check(service.contains("entityCap(owner.getUniqueId())"),
                "every infection passes the hard entity cap");
        check(service.contains("target.getScheduler().run(plugin,"),
                "the carrier effect hops to the carrier's own region thread");
        check(!service.contains("runAtFixedRate") && !service.contains("getNearbyEntities"),
                "no repeating tasks or proximity scans in the assassin runtime");

        final String state = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/assassin/AssassinCombatState.java"));
        check(state.contains("Carriers never hand the strain on"),
                "mob-to-mob spread is impossible by construction, not merely throttled");

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        check(gameplayConfig.contains("entity-cap: 6") && gameplayConfig.contains("immune-types:")
                        && gameplayConfig.contains("allow-infection: true"),
                "the plague caps and the immunity list are admin-tunable live config");

        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"));
        for (final String trial : new String[]{"assassin_poisoner_trial",
                "assassin_phantom_trial", "assassin_plaguebringer_trial"}) {
            check(manager.contains(trial), "the capstone trial contract " + trial + " is registered");
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
