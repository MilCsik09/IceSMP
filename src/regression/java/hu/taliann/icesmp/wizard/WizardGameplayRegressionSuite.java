package hu.taliann.icesmp.wizard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Dependency-free behavior regression for the concrete Varázsló runtime state. */
public final class WizardGameplayRegressionSuite {

    private static int assertions;

    private WizardGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        weaveIsAnExplicitPairTable();
        reactionArmsAndIsSpentOnce();
        attunementsFeedConvergenceAndCrown();
        theCombatStateHoldsNoCourtAuthority();
        attunementDecayIsPollingFrequencyInvariant();
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

    /**
     * The Holtak Udvara has exactly one truth source, and it is not here. Proven structurally rather
     * than by grep: nothing in the transient combat state may hold, count or name a courtier, so
     * there is nothing that could drift away from the durable necromancer.court roster.
     */
    private static void theCombatStateHoldsNoCourtAuthority() {
        for (final Field field : WizardCombatState.class.getDeclaredFields()) {
            final String name = field.getName().toLowerCase(Locale.ROOT);
            check(!name.contains("court") && !name.contains("udvar") && !name.contains("roster"),
                    "no transient field may name the court: " + field.getName());
            final Class<?> type = field.getType();
            final boolean container = type.isArray() || Collection.class.isAssignableFrom(type)
                    || Map.class.isAssignableFrom(type);
            check(!container || name.startsWith("attunement"),
                    "the only container left is the attunement array: " + field.getName());
        }
        for (final Method method : WizardCombatState.class.getDeclaredMethods()) {
            final String name = method.getName().toLowerCase(Locale.ROOT);
            check(!name.contains("court") && !name.contains("raise") && !name.contains("harvest")
                            && !name.equals("holds"),
                    "no transient member may serve the court: " + method.getName());
        }
    }

    /**
     * Lazy decay must be a function of elapsed time, not of how often it is read. The old model
     * truncated the sub-point remainder on every poll and then moved its clock forward, so frequent
     * reads decayed strictly slower than rare ones — the rate silently depended on the caller.
     */
    private static void attunementDecayIsPollingFrequencyInvariant() {
        final long t0 = 500_000L;
        final long delay = 6_000L;
        final double perSecond = 6.0D;

        // One read after a full second versus ten reads inside it: the same attunement must remain.
        final WizardCombatState rare = new WizardCombatState();
        final WizardCombatState frequent = new WizardCombatState();
        rare.addAttunement(0, 100, t0, delay, perSecond);
        frequent.addAttunement(0, 100, t0, delay, perSecond);
        final long end = t0 + delay + 1_000L;
        for (long now = t0 + delay + 100L; now <= end; now += 100L) {
            frequent.attunement(0, now, delay, perSecond);
        }
        check(rare.attunement(0, end, delay, perSecond)
                        == frequent.attunement(0, end, delay, perSecond),
                "ten reads inside a second decay exactly as much as one read after it");
        check(rare.attunement(0, end, delay, perSecond) == 94,
                "one second at 6/s takes exactly six points");

        // The pathological case: polls far shorter than one whole point of decay.
        final WizardCombatState pounded = new WizardCombatState();
        pounded.addAttunement(0, 100, t0, delay, perSecond);
        for (long now = t0 + delay + 10L; now <= end; now += 10L) {
            pounded.attunement(0, now, delay, perSecond);
        }
        check(pounded.attunement(0, end, delay, perSecond) == 94,
                "sub-point polling never rounds the decay away");

        // A fresh gain restarts the grace window and the accounting with it.
        final WizardCombatState regained = new WizardCombatState();
        regained.addAttunement(0, 100, t0, delay, perSecond);
        regained.attunement(0, end, delay, perSecond);
        check(regained.attunement(0, end, delay, perSecond) == 94, "the decay so far stands");
        regained.addAttunement(0, 6, end, delay, perSecond);
        check(regained.attunement(0, end + delay, delay, perSecond) == 100,
                "a new gain re-arms the grace window instead of paying an old decay debt");

        // Long idling still bottoms out at zero rather than going negative.
        final WizardCombatState idle = new WizardCombatState();
        idle.addAttunement(0, 100, t0, delay, perSecond);
        check(idle.attunement(0, t0 + delay + 60_000L, delay, perSecond) == 0,
                "a long idle drains the attunement to zero, never below");
    }

    private static void cleanupLifecycle() {
        final WizardCombatState state = new WizardCombatState();
        final long t0 = 100_000L;
        state.weave(WizardCombatState.School.TUZ, t0, 5_000L);
        state.weave(WizardCombatState.School.FAGY, t0, 5_000L);
        state.addAttunement(0, 80, t0, 6_000L, 6.0D);
        state.clearSpecializationState();
        check(state.armedReaction(t0) == null, "spec switch clears the armed reaction");
        check(state.lastSchool() == null, "spec switch clears the weave memory");
        check(state.attunement(0, t0, 6_000L, 6.0D) == 0, "spec switch clears the attunements");
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

        // One admission rule, one gateway, and the raise only ever follows the durable commit.
        check(service.contains("ClassSpecCatalog.admitsCompanion(activeLoadout(player.getUniqueId()),")
                        && service.contains("if (admitsRaise(player)) return true;"),
                "the pre-cast gate is the shared admission rule read from the durable loadout");
        check(service.contains("gateway.raiseCourtV2(player, kind, courtEntityType(kind), courtCapacity(player))")
                        && service.contains("gateway.releaseCourtV2(player)"),
                "raise and harvest both go through the existing PetManager companion gateway");
        check(service.contains("gateway.courtRoster(player)")
                        && service.contains("court(player).size()"),
                "the court size the gameplay reads is the durable projection");
        for (final String gone : new String[]{"courtSize()", "harvestCourt()", "state.raise(",
                "state.holds("}) {
            check(!service.contains(gone), "no transient court call survives in the service: " + gone);
        }

        final String pets = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/PetManager.java"));
        final int raiseIndex = pets.indexOf("PetMutationResult> raiseCourtV2");
        final int raiseCommit = pets.indexOf("mutateCompanion", raiseIndex);
        final int raiseSpawn = pets.indexOf("spawnAndAdopt", raiseIndex);
        check(raiseIndex > 0 && raiseCommit > raiseIndex && raiseSpawn > raiseCommit,
                "the courtier is embodied only after the durable companion mutation is issued");

        final String gateway = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/DefaultClassSpecProfileGateway.java"));
        check(gateway.contains("if(r.capacity()>0&&roster.size()>=r.capacity())"
                        + "return Plan.reject(\"companion roster is full\");"),
                "the committed mutation re-evaluates the very capacity the caller validated");

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
