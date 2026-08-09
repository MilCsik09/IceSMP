package hu.taliann.icesmp.wizard;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior regression for the concrete Varázsló runtime state. */
public final class WizardGameplayRegressionSuite {

    private static int assertions;

    private WizardGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        weaveIsAnExplicitPairTable();
        reactionArmsAndIsSpentOnce();
        attunementsFeedConvergenceAndCrown();
        courtIsBoundedAndHarvestedWhole();
        cleanupLifecycle();
        soulforgeAndAllowlistSourceContracts();
        System.out.println("Wizard gameplay regression suite passed. assertions=" + assertions);
    }

    private static void weaveIsAnExplicitPairTable() {
        // The whole table, asserted pair by pair: five reactions, everything else inert.
        check(WizardCombatState.reactionFor(WizardCombatState.School.TUZ,
                        WizardCombatState.School.FAGY) == WizardCombatState.Reaction.GOZROBBANAS,
                "Tűz → Fagy weaves Gőzrobbanás");
        check(WizardCombatState.reactionFor(WizardCombatState.School.FAGY,
                        WizardCombatState.School.VIHAR) == WizardCombatState.Reaction.JEGVIHAR,
                "Fagy → Vihar weaves Jégvihar");
        check(WizardCombatState.reactionFor(WizardCombatState.School.VIHAR,
                        WizardCombatState.School.TUZ) == WizardCombatState.Reaction.KOHO,
                "Vihar → Tűz weaves Kohó");
        check(WizardCombatState.reactionFor(WizardCombatState.School.ARNY,
                        WizardCombatState.School.ARKAN) == WizardCombatState.Reaction.ARNYVISSZHANG,
                "Árny → Arkán weaves Árnyvisszhang");
        check(WizardCombatState.reactionFor(WizardCombatState.School.ARKAN,
                        WizardCombatState.School.ARNY) == WizardCombatState.Reaction.ARKAN_EROSITES,
                "Arkán → Árny weaves Arkán Erősítés");

        // Order matters and unlisted pairs stay inert — no rule is ever derived.
        check(WizardCombatState.reactionFor(WizardCombatState.School.FAGY,
                WizardCombatState.School.TUZ) == null, "the reverse pair is not a reaction");
        check(WizardCombatState.reactionFor(WizardCombatState.School.TUZ,
                WizardCombatState.School.TUZ) == null, "a repeated school weaves nothing");
        check(WizardCombatState.reactionFor(WizardCombatState.School.TUZ,
                WizardCombatState.School.VIHAR) == null, "an unlisted pair weaves nothing");
        check(WizardCombatState.reactionFor(null, WizardCombatState.School.TUZ) == null,
                "a missing first school weaves nothing");
        int reactions = 0;
        for (final WizardCombatState.School a : WizardCombatState.School.values()) {
            for (final WizardCombatState.School b : WizardCombatState.School.values()) {
                if (WizardCombatState.reactionFor(a, b) != null) reactions++;
            }
        }
        check(reactions == 5, "the table holds exactly five concrete pairs — nothing generative");
    }

    private static void reactionArmsAndIsSpentOnce() {
        final WizardCombatState state = new WizardCombatState();
        final long t0 = 10_000L;
        check(state.weave(WizardCombatState.School.TUZ, t0, 5_000L) == null,
                "the first cast alone weaves nothing");
        check(state.lastSchool() == WizardCombatState.School.TUZ, "the weave remembers the school");
        check(state.weave(WizardCombatState.School.FAGY, t0, 5_000L)
                        == WizardCombatState.Reaction.GOZROBBANAS,
                "the second cast completes the pair");
        check(state.lastSchool() == null,
                "the pair is consumed by its reaction — the same weave cannot fire twice");
        check(state.armedReaction(t0 + 4_999L) == WizardCombatState.Reaction.GOZROBBANAS,
                "the reaction stays armed for its window");
        check(state.consumeReaction(t0 + 1_000L) == WizardCombatState.Reaction.GOZROBBANAS,
                "the empowered cast spends the reaction");
        check(state.armedReaction(t0 + 1_100L) == null, "a spent reaction is gone");
        check(state.consumeReaction(t0 + 1_200L) == null, "there is nothing left to spend");

        state.weave(WizardCombatState.School.FAGY, t0 + 2_000L, 5_000L);
        state.weave(WizardCombatState.School.VIHAR, t0 + 2_100L, 5_000L);
        check(state.armedReaction(t0 + 20_000L) == null, "an unused reaction expires");
    }

    private static void attunementsFeedConvergenceAndCrown() {
        final WizardCombatState state = new WizardCombatState();
        final long t0 = 50_000L;
        check(WizardCombatState.ATTUNEMENTS == 3, "there is one three-slot attunement array");
        check(state.addAttunement(0, 70, t0, 6_000L, 6.0D) == 70, "a fire cast attunes fire");
        check(state.addAttunement(0, 1000, t0, 6_000L, 6.0D) == 100, "attunement is bounded at 100");
        check(state.addAttunement(9, 50, t0, 6_000L, 6.0D) == 0, "an out-of-range index is inert");
        check(!state.isConvergent(70, t0, 6_000L, 6.0D), "one attuned element is not Konvergencia");

        state.addAttunement(1, 70, t0, 6_000L, 6.0D);
        check(state.isConvergent(70, t0, 6_000L, 6.0D), "two at the bar is Konvergencia");
        check(!state.isCrowned(70, t0, 6_000L, 6.0D), "two is not yet the Elemi Korona");
        state.addAttunement(2, 70, t0, 6_000L, 6.0D);
        check(state.isCrowned(70, t0, 6_000L, 6.0D), "all three at the bar is the Elemi Korona");
        check(state.attunedCount(70, t0, 6_000L, 6.0D) == 3, "the count reports all three");

        check(state.attunement(0, t0 + 5_999L, 6_000L, 6.0D) == 100,
                "attunement holds inside the grace window");
        check(!state.isCrowned(70, t0 + 12_000L, 6_000L, 6.0D),
                "idle attunements decay and the crown slips");
    }

    private static void courtIsBoundedAndHarvestedWhole() {
        final WizardCombatState state = new WizardCombatState();
        check(WizardCombatState.COURT_SLOTS == 4, "the Holtak Udvara has a fixed slot count");
        check(state.raise("zombi", 2), "the first kind is raised");
        check(state.holds("zombi"), "the court holds the kind");
        check(!state.raise("zombi", 2), "the same kind never takes a second slot");
        check(state.raise("csontvaz", 2), "a second kind fits the capacity");
        check(!state.raise("lidercz", 2),
                "the capacity is hard — nothing beyond it is ever raised");
        check(state.courtSize() == 2, "the court reports its size");
        check(state.raise("lidercz", 3), "a wider capacity admits the third kind");
        check(!state.raise("  ", 4), "a blank kind id is never raised");

        check(state.harvestCourt() == 3, "harvesting releases the whole court at once");
        check(state.courtSize() == 0, "the court is empty afterwards");
        check(state.harvestCourt() == 0, "an empty court harvests nothing");
    }

    private static void cleanupLifecycle() {
        final WizardCombatState state = new WizardCombatState();
        final long t0 = 100_000L;
        state.weave(WizardCombatState.School.TUZ, t0, 5_000L);
        state.weave(WizardCombatState.School.FAGY, t0, 5_000L);
        state.addAttunement(0, 80, t0, 6_000L, 6.0D);
        state.raise("zombi", 2);
        state.clearSpecializationState();
        check(state.armedReaction(t0) == null, "spec switch clears the armed reaction");
        check(state.lastSchool() == null, "spec switch clears the weave memory");
        check(state.attunement(0, t0, 6_000L, 6.0D) == 0, "spec switch clears the attunements");
        check(state.courtSize() == 0, "spec switch empties the Holtak Udvara");
    }

    private static void soulforgeAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"warrior\", \"evoker\", \"archer\", \"shaman\", "
                        + "\"monk\", \"paladin\", \"demon_hunter\",")
                        && policy.contains("\"death_knight\", \"assassin\", \"warlock\", \"wizard\")"),
                "the gameplay-v2 allowlist now admits all thirteen classes");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/wizard/WizardGameplayService.java"));
        check(service.contains("forge.extraSlots(player)"),
                "the court size reads the EXISTING Soulforge authority");
        for (final String duplicated : new String[]{"upgradeV2(", "nextCost(", "shardBalance",
                "ProfileOperation", "operationId"}) {
            check(!service.contains(duplicated),
                    "the shard economy is never reimplemented here (" + duplicated + ")");
        }
        check(!service.contains("runAtFixedRate") && !service.contains("getNearbyEntities"),
                "no repeating tasks or proximity scans in the wizard runtime");

        final String state = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/wizard/WizardCombatState.java"));
        check(state.contains("reactionFor") && !state.contains("Rule") && !state.contains("Pattern"),
                "the weave is a plain enumerated table, not a rule or pattern engine");

        final String catalog = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/domain/ClassSpecCatalog.java"));
        check(catalog.contains("\"necromancer\", \"necromancer.court\""),
                "Nekromanta keeps the necromancer.court companion namespace");

        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"));
        for (final String trial : new String[]{"wizard_elementalist_trial", "wizard_necromancer_trial"}) {
            check(manager.contains(trial), "the capstone trial contract " + trial + " is registered");
        }

        final String gameplayConfig = Files.readString(Path.of(
                "src/main/resources/config/class-gameplay.yml"));
        check(gameplayConfig.contains("attunement-threshold: 70"),
                "the 70+ attunement bar is admin-tunable live config");
        check(gameplayConfig.contains("classes: []"),
                "every class casts through its personal Lélekkapocs");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
