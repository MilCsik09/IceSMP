package hu.taliann.icesmp.wizard;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.application.ClassSpecSectionMutationStore;
import hu.taliann.icesmp.classspec.application.DefaultClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GateSnapshot;
import hu.taliann.icesmp.classspec.application.GateState;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.application.ProfileSessionRegistry;
import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Dependency-free Profile v2 gateway regressions for the Varázsló rollout: the allowlist admits
 * wizard, the DARK Nekromanta still answers to the existing gate system, doctrines stay slot-local,
 * and the Holtak Udvara has exactly one authority — the durable necromancer.court companion roster,
 * with one admission rule shared by the pre-cast check and the committed mutation.
 */
public final class WizardProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000004d1");
    private static final String COURT = "necromancer.court";
    private static int assertions;

    private WizardProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        wizardSecondSlotUnlocksAndSwitches();
        darkNecromancerObeysTheExistingGates();
        wizardDoctrineMasteryAndCapstoneStaySlotLocal();
        theCourtHasExactlyOneDurableAuthority();
        everySlotIsReachableAndTheRuleIsShared();
        System.out.println("Wizard profile regression suite passed. assertions=" + assertions);
    }

    private static void wizardSecondSlotUnlocksAndSwitches() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("wizard").classLevel(1).build());
        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "wizard-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for wizard");
        check(h.store.profile.secondSpecUnlocked(),
                "wizard second spec unlocks through the allowlist");

        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "elementalist", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "wizard learns Elementalista");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "necromancer", LoadoutSlot.SECOND, satisfiedGates()))
                .toCompletableFuture().join().committed(), "wizard learns Nekromanta second through the DARK gates");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "wizard loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("necromancer"),
                "switch activates the Nekromanta loadout");
    }

    private static void darkNecromancerObeysTheExistingGates() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("wizard").classLevel(30).classExperience(100_000)
                .secondSpecUnlocked(true).build());
        final var blocked = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "necromancer", LoadoutSlot.FIRST, closedGates())).toCompletableFuture().join();
        check(blocked.status() == ProfileMutationResult.Status.REJECTED,
                "the DARK Nekromanta is refused while its gates are unsatisfied");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.EMPTY,
                "a refused DARK learn leaves the loadout untouched");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "necromancer", LoadoutSlot.FIRST, satisfiedGates()))
                .toCompletableFuture().join().committed(),
                "satisfied gates admit the Nekromanta through the existing DARK system");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).specializationId().equals("necromancer"),
                "the admitted DARK spec occupies its loadout");
    }

    private static void wizardDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout elementalist = loadout("elementalist", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "gyors_rahangolodas")
                .withDoctrineChoice("level_40", "mely_szoves")
                .withMastery(new MasteryProgress(5, 530))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout necromancer = loadout("necromancer", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "nagyobb_udvar")
                .withMastery(new MasteryProgress(1, 110))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(5).primaryClassId("wizard").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, elementalist)
                .loadout(LoadoutSlot.SECOND, necromancer)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "gyors_rahangolodas", "level_40", "mely_szoves")),
                "Elementalista doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "nagyobb_udvar")),
                "Nekromanta doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 5
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 1,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "hosszu_visszacsatolas"),
                "committed doctrine tier cannot silently overwrite");
    }

    /**
     * The Holtak Udvara contract driven through the real gateway: durable commit precedes every
     * authoritative read, the projection is a pure function of Profile v2, a spec switch and a DARK
     * seal only hide it, the harvest is durable-first, and no replay can duplicate a courtier.
     */
    private static void theCourtHasExactlyOneDurableAuthority() {
        final Harness h = courtHarness();
        check(court(h).isEmpty(), "a fresh court projects no courtiers");

        check(h.gateway.mutateCompanion(PLAYER, raise(id(1), "zombi", 4, "necromancer-raise:1"))
                .toCompletableFuture().join().committed(), "the raise commits durably");
        check(court(h).size() == 1,
                "the courtier becomes visible only through the committed durable roster");
        check(court(h).get(0).kind().equals("zombi"), "the kind rides along as an attribute");

        final var replayed = h.gateway.mutateCompanion(PLAYER,
                raise(id(1), "zombi", 4, "necromancer-raise:1")).toCompletableFuture().join();
        check(!replayed.committed() && court(h).size() == 1,
                "replaying the same raise operation adds nothing");

        final Harness relogged = harness(h.store.profile);
        check(court(relogged).size() == 1,
                "a fresh runtime rebuilds the identical court from Profile v2 alone");

        check(h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join().committed(), "the wizard switches to Elementalista");
        check(court(h).isEmpty() && ClassSpecCatalog.companionProjection(
                        h.store.profile.loadout(LoadoutSlot.FIRST), COURT).isEmpty(),
                "an inactive Nekromanta loadout projects no courtiers, read either way");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().size() == 1,
                "the spec switch hides the court without touching it");
        check(h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.FIRST))
                .toCompletableFuture().join().committed(), "the wizard switches back");
        check(court(h).size() == 1, "the court returns exactly as it was");

        check(h.gateway.reconcile(PLAYER, new ClassSpecProfileGateway.ReconcileRequest(
                        Map.of(LoadoutSlot.FIRST, closedGates())))
                .toCompletableFuture().join().committed(), "closed DARK gates seal the Nekromanta");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.SEALED,
                "the gate closure seals exactly that loadout");
        check(ClassSpecCatalog.companionProjection(
                        h.store.profile.loadout(LoadoutSlot.FIRST), COURT).isEmpty(),
                "a SEALED loadout projects nothing");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().size() == 1,
                "sealing never deletes the durable court");
        check(h.gateway.mutateCompanion(PLAYER, raise(id(2), "csontvaz", 4, "necromancer-raise:2"))
                        .toCompletableFuture().join().status() == ProfileMutationResult.Status.REJECTED,
                "nothing may be raised into a sealed court");
        check(h.gateway.reconcile(PLAYER, new ClassSpecProfileGateway.ReconcileRequest(
                        Map.of(LoadoutSlot.FIRST, satisfiedGates())))
                .toCompletableFuture().join().committed(), "satisfied gates lift the seal");
        check(h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.FIRST))
                .toCompletableFuture().join().committed(), "the unsealed court is activatable again");
        check(court(h).size() == 1, "unsealing restores the court whole");

        for (final CompanionProfile courtier : List.copyOf(court(h))) {
            check(h.gateway.mutateCompanion(PLAYER, release(courtier.companionId()))
                    .toCompletableFuture().join().committed(), "the harvest is a durable removal");
        }
        check(court(h).isEmpty()
                        && h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().isEmpty(),
                "the harvest empties the durable court, not merely a runtime view");
    }

    /**
     * The two findings that were one modelling bug: every slot must be reachable although only three
     * kinds exist, and the rule that admits a raise before the cast must be the very rule the commit
     * enforces — otherwise a passing pre-check turns into a refused mutation and a phantom cast.
     */
    private static void everySlotIsReachableAndTheRuleIsShared() {
        final Harness h = courtHarness();
        final String[] kinds = {"zombi", "csontvaz", "lidercz", "zombi"};
        for (int slot = 0; slot < 4; slot++) {
            final ClassLoadout before = h.store.profile.loadout(LoadoutSlot.FIRST);
            check(ClassSpecCatalog.admitsCompanion(before, COURT, 4),
                    "the shared rule admits slot " + (slot + 1) + " of 4");
            check(h.gateway.mutateCompanion(PLAYER,
                            raise(id(slot + 10), kinds[slot], 4, "necromancer-raise:slot" + slot))
                            .toCompletableFuture().join().committed(),
                    "what the rule admits, the commit accepts (slot " + (slot + 1) + ")");
            check(court(h).size() == slot + 1, "the court grew to " + (slot + 1));
        }
        check(court(h).stream().filter(c -> c.kind().equals("zombi")).count() == 2,
                "a repeated kind is a second courtier — three kinds still fill four slots");
        check(court(h).stream().map(CompanionProfile::companionId).distinct().count() == 4,
                "the court is keyed by logical companion id, never by kind");

        final ClassLoadout full = h.store.profile.loadout(LoadoutSlot.FIRST);
        check(!ClassSpecCatalog.admitsCompanion(full, COURT, 4),
                "the shared rule refuses the fifth raise before the cast");
        check(h.gateway.mutateCompanion(PLAYER, raise(id(99), "zombi", 4, "necromancer-raise:99"))
                        .toCompletableFuture().join().status()
                        == ProfileMutationResult.Status.REJECTED,
                "and the commit refuses it too — the rule is one rule");

        // A stale pre-check cannot smuggle a courtier past the ceiling: capacity is re-read at commit.
        check(h.gateway.mutateCompanion(PLAYER, raise(id(98), "zombi", 8, "necromancer-raise:98"))
                        .toCompletableFuture().join().committed(),
                "a genuinely wider capacity does admit one more");
        check(court(h).size() == 5, "the wider ceiling is what admitted it");
        check(h.gateway.mutateCompanion(PLAYER, raise(id(97), "zombi", 4, "necromancer-raise:97"))
                        .toCompletableFuture().join().status()
                        == ProfileMutationResult.Status.REJECTED,
                "the commit judges against the capacity it was given, not against a cached count");
    }

    private static Harness courtHarness() {
        return harness(ClassSpecSection.builder()
                .revision(3).primaryClassId("wizard").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, loadout("necromancer", LoadoutStatus.ACTIVE))
                .loadout(LoadoutSlot.SECOND, loadout("elementalist", LoadoutStatus.INACTIVE))
                .activeSlot(LoadoutSlot.FIRST)
                .build());
    }

    private static UUID id(final int index) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-0000000c%04d", index));
    }

    /** The court exactly as the runtime sees it: the one shared companion projection. */
    private static List<CompanionProfile> court(final Harness h) {
        final ClassSpecSection profile = h.store.profile;
        return profile == null || profile.activeSlot() == null ? List.of()
                : ClassSpecCatalog.companionProjection(profile.loadout(profile.activeSlot()), COURT);
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest raise(
            final UUID companionId, final String kind, final int capacity, final String operationId) {
        return new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, companionId,
                new CompanionProfile(companionId, COURT, "ZOMBIE", "Udvaronc", 1, 0L, "", "ACTIVE",
                        List.of(), 0L, Map.of("ritual_summoned", "true",
                        CompanionProfile.KIND_KEY, kind)),
                "", 0, 0L, 0L, List.of(), Map.of(), capacity, operationId);
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest release(final UUID companionId) {
        return new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.REMOVE, companionId, null,
                "", 0, 0L, 0L, List.of(), Map.of(), COURT + "-release:" + companionId);
    }

    private static Harness harness(final ClassSpecSection profile) {
        final FakeStore store = new FakeStore(profile);
        final ProfileSessionRegistry sessions = new ProfileSessionRegistry();
        final UUID token = sessions.begin(PLAYER);
        sessions.markReady(PLAYER, token);
        return new Harness(store, new DefaultClassSpecProfileGateway(
                store, ClassSpecRuntimePort.noop(), sessions));
    }

    private static ClassLoadout loadout(final String spec, final LoadoutStatus status) {
        return new ClassLoadout(spec, status, null, Map.of(), MasteryProgress.empty(), null,
                Set.of(), "", CapstoneStatus.LOCKED, Map.of(), Map.of(), "");
    }

    private static GateSnapshot openGates() {
        return new GateSnapshot(GateState.ofRequirements(
                false, true, false, true, false, true), Map.of());
    }

    private static final Map<GateState.Gate, String> DARK_GATE_IDS = Map.of(
            GateState.Gate.FACTION, "dark_faction",
            GateState.Gate.SINNER, "sinner_mark",
            GateState.Gate.QUEST, "dark_initiation");

    private static GateSnapshot satisfiedGates() {
        return new GateSnapshot(GateState.satisfied(), DARK_GATE_IDS);
    }

    private static GateSnapshot closedGates() {
        return new GateSnapshot(GateState.ofRequirements(
                true, false, true, false, true, false), DARK_GATE_IDS);
    }

    private record Harness(FakeStore store, DefaultClassSpecProfileGateway gateway) {
    }

    private static final class FakeStore implements ClassSpecSectionMutationStore {
        volatile ClassSpecSection profile;
        volatile String blockReason = "";

        FakeStore(final ClassSpecSection profile) {
            this.profile = profile;
        }

        @Override
        public Optional<ClassSpecSection> cached(final UUID id) {
            return Optional.ofNullable(profile);
        }

        @Override
        public Optional<String> sessionBlockReason(final UUID id) {
            return blockReason.isBlank() ? Optional.empty() : Optional.of(blockReason);
        }

        @Override
        public CompletionStage<SaveResult> save(final UUID id, final long expected,
                                                final ClassSpecSection candidate) {
            if (profile == null || profile.revision() != expected) {
                return CompletableFuture.completedFuture(
                        SaveResult.conflict(profile, profile == null ? -1 : profile.revision()));
            }
            profile = candidate;
            return CompletableFuture.completedFuture(SaveResult.committed(candidate));
        }

        @Override
        public CompletionStage<ClassSpecSection> recover(final UUID id, final String evidence,
                                                         final String audit) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public void blockSession(final UUID id, final String reason) {
            blockReason = reason;
        }
    }

    private static void expectFailure(final Runnable action, final String message) {
        assertions++;
        try {
            action.run();
            throw new AssertionError(message + " (no exception)");
        } catch (final IllegalArgumentException | IllegalStateException expected) {
            // expected domain rejection
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
