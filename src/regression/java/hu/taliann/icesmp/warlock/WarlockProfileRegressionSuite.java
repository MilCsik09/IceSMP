package hu.taliann.icesmp.warlock;

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
 * Dependency-free Profile v2 gateway regressions for the Boszorkánymester rollout: the allowlist admits
 * warlock, the DARK Demonológus still answers to the existing gate system, doctrines stay slot-local,
 * and the Demonológus pact has exactly one authority — the durable demonologist.roster companion
 * roster, driven through the real gateway and read through the one shared companion projection.
 */
public final class WarlockProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000004c1");
    private static final UUID IMP = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    private static final UUID IMP_TWIN = UUID.fromString("00000000-0000-0000-0000-00000000d002");
    private static final UUID INFERNAL = UUID.fromString("00000000-0000-0000-0000-00000000d003");
    private static final String PACT = "demonologist.roster";
    private static int assertions;

    private WarlockProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        warlockSecondSlotUnlocksAndSwitches();
        darkDemonologistObeysTheExistingGates();
        warlockDoctrineMasteryAndCapstoneStaySlotLocal();
        demonPactHasExactlyOneDurableAuthority();
        aFailedDurableBindingIsNeverVisible();
        System.out.println("Warlock profile regression suite passed. assertions=" + assertions);
    }

    private static void warlockSecondSlotUnlocksAndSwitches() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("warlock").classLevel(1).build());
        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "warlock-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for warlock");
        check(h.store.profile.secondSpecUnlocked(),
                "warlock second spec unlocks through the allowlist");

        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "affliction", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "warlock learns Átok");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "destruction", LoadoutSlot.SECOND, openGates()))
                .toCompletableFuture().join().committed(), "warlock learns Pusztítás second");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "warlock loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("destruction"),
                "switch activates the Pusztítás loadout");
    }

    private static void darkDemonologistObeysTheExistingGates() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("warlock").classLevel(30).classExperience(100_000)
                .secondSpecUnlocked(true).build());
        final var blocked = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "demonologist", LoadoutSlot.FIRST, closedGates())).toCompletableFuture().join();
        check(blocked.status() == ProfileMutationResult.Status.REJECTED,
                "the DARK Demonológus is refused while its gates are unsatisfied");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.EMPTY,
                "a refused DARK learn leaves the loadout untouched");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "demonologist", LoadoutSlot.FIRST, satisfiedGates()))
                .toCompletableFuture().join().committed(),
                "satisfied gates admit the Demonológus through the existing DARK system");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).specializationId().equals("demonologist"),
                "the admitted DARK spec occupies its loadout");
    }

    private static void warlockDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout affliction = loadout("affliction", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "tarto_atok")
                .withDoctrineChoice("level_40", "eros_fonal")
                .withMastery(new MasteryProgress(5, 530))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout destruction = loadout("destruction", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "elenk_parazs")
                .withMastery(new MasteryProgress(1, 110))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(5).primaryClassId("warlock").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, affliction)
                .loadout(LoadoutSlot.SECOND, destruction)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "tarto_atok", "level_40", "eros_fonal")),
                "Átok doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "elenk_parazs")),
                "Pusztítás doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 5
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 1,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "olcso_paktum"),
                "committed doctrine tier cannot silently overwrite");
    }

    /**
     * The whole Demonológus pact contract, driven through the real gateway: durable commit precedes
     * every authoritative read, the projection is a pure function of Profile v2 (so a relog rebuilds
     * it), a spec switch and a DARK seal only hide it, releases are durable-first, and no replay or
     * reused identity can ever duplicate a companion.
     */
    private static void demonPactHasExactlyOneDurableAuthority() {
        final Harness h = pactHarness();
        check(pact(h).isEmpty(), "a fresh pact projects no demons at all");

        check(h.gateway.mutateCompanion(PLAYER, bind(IMP, "imp", "warlock-bind:imp"))
                .toCompletableFuture().join().committed(), "the pact binding commits durably");
        check(pact(h).size() == 1,
                "the demon becomes visible only through the committed durable roster");
        check(pact(h).get(0).kind().equals("imp"),
                "the kind rides along as an attribute of the instance");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().size() == 1,
                "the durable roster is the thing that actually grew");

        check(h.gateway.mutateCompanion(PLAYER, bind(IMP_TWIN, "imp", "warlock-bind:imp-twin"))
                        .toCompletableFuture().join().committed(),
                "a second instance of the same kind is a second companion, not a refused duplicate");
        check(pact(h).size() == 2, "the pact holds two distinct imps");
        check(pact(h).stream().map(CompanionProfile::companionId).distinct().count() == 2,
                "the roster is keyed by logical companion id, never by kind");

        final var replayed = h.gateway.mutateCompanion(PLAYER, bind(IMP, "imp", "warlock-bind:imp"))
                .toCompletableFuture().join();
        check(!replayed.committed() && pact(h).size() == 2,
                "replaying the same binding operation adds nothing");
        final var reused = h.gateway.mutateCompanion(PLAYER, bind(IMP, "imp", "warlock-bind:again"))
                .toCompletableFuture().join();
        check(reused.status() == ProfileMutationResult.Status.REJECTED && pact(h).size() == 2,
                "a reused companion identity is refused outright — no duplicate on a race");

        final Harness relogged = harness(h.store.profile);
        check(pact(relogged).size() == 2 && pact(relogged).get(0).kind().equals("imp"),
                "a completely fresh runtime rebuilds the identical pact from Profile v2 alone");

        check(h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join().committed(), "the warlock switches to Pusztítás");
        check(pact(h).isEmpty(), "an inactive Demonológus loadout projects no demons");
        check(ClassSpecCatalog.companionProjection(
                        h.store.profile.loadout(LoadoutSlot.FIRST), PACT).isEmpty(),
                "read head-on, an INACTIVE loadout still projects nothing");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().size() == 2,
                "the spec switch hides the pact without touching it");
        check(h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.FIRST))
                .toCompletableFuture().join().committed(), "the warlock switches back");
        check(pact(h).size() == 2, "the pact returns exactly as it was");

        check(h.gateway.reconcile(PLAYER, new ClassSpecProfileGateway.ReconcileRequest(
                        Map.of(LoadoutSlot.FIRST, closedGates())))
                .toCompletableFuture().join().committed(), "closed DARK gates seal the Demonológus");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.SEALED,
                "the gate closure seals exactly that loadout");
        check(pact(h).isEmpty(), "a sealed pact projects nothing");
        check(ClassSpecCatalog.companionProjection(
                        h.store.profile.loadout(LoadoutSlot.FIRST), PACT).isEmpty(),
                "read head-on, a SEALED loadout still projects nothing");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().size() == 2,
                "sealing never deletes the durable pact");
        check(h.gateway.mutateCompanion(PLAYER, bind(INFERNAL, "infernal", "warlock-bind:sealed"))
                        .toCompletableFuture().join().status() == ProfileMutationResult.Status.REJECTED,
                "no demon may be bound into a sealed pact");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().size() == 2,
                "the refused binding left the durable pact untouched");

        check(h.gateway.reconcile(PLAYER, new ClassSpecProfileGateway.ReconcileRequest(
                        Map.of(LoadoutSlot.FIRST, satisfiedGates())))
                .toCompletableFuture().join().committed(), "satisfied gates lift the seal");
        check(h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.FIRST))
                .toCompletableFuture().join().committed(), "the unsealed pact is activatable again");
        check(pact(h).size() == 2, "unsealing restores the whole pact, demon for demon");

        for (final CompanionProfile bound : List.copyOf(pact(h))) {
            check(h.gateway.mutateCompanion(PLAYER, release(bound.companionId(),
                            "warlock-release:" + bound.companionId()))
                    .toCompletableFuture().join().committed(), "each demon leaves the durable roster");
        }
        check(pact(h).isEmpty()
                        && h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().isEmpty(),
                "the release is durable, not merely a runtime view being cleared");
        check(h.gateway.mutateCompanion(PLAYER, release(IMP, "warlock-release:absent"))
                        .toCompletableFuture().join().status()
                        == ProfileMutationResult.Status.NO_CHANGE,
                "releasing an absent demon reports no change, so no reward is ever paid twice");
    }

    /** A durable write that never lands must leave the pact invisible and the session fenced. */
    private static void aFailedDurableBindingIsNeverVisible() {
        final Harness h = pactHarness();
        h.store.rejectSaves = true;
        final var failed = h.gateway.mutateCompanion(PLAYER, bind(IMP, "imp", "warlock-bind:doomed"))
                .toCompletableFuture().join();
        check(!failed.committed(), "a failed durable save is not a committed binding");
        check(pact(h).isEmpty(),
                "nothing is authoritative before the durable commit — the demon never appears");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).companionRoster().isEmpty(),
                "the durable roster is untouched by the failed write");
        check(!h.store.blockReason.isBlank(),
                "the failed durable write fences the session instead of silently continuing");
    }

    private static Harness pactHarness() {
        return harness(ClassSpecSection.builder()
                .revision(3).primaryClassId("warlock").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, loadout("demonologist", LoadoutStatus.ACTIVE))
                .loadout(LoadoutSlot.SECOND, loadout("destruction", LoadoutStatus.INACTIVE))
                .activeSlot(LoadoutSlot.FIRST)
                .build());
    }

    /** The pact exactly as the runtime sees it: the one shared companion projection. */
    private static List<CompanionProfile> pact(final Harness h) {
        final ClassSpecSection profile = h.store.profile;
        return profile == null || profile.activeSlot() == null ? List.of()
                : ClassSpecCatalog.companionProjection(profile.loadout(profile.activeSlot()), PACT);
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest bind(
            final UUID companionId, final String kind, final String operationId) {
        return new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD, companionId,
                new CompanionProfile(companionId, PACT, "VEX", "Imp", 1, 0L, "", "ACTIVE",
                        List.of(), 0L, Map.of("ritual_summoned", "true",
                        CompanionProfile.KIND_KEY, kind)),
                "", 0, 0L, 0L, List.of(), Map.of(), operationId);
    }

    private static ClassSpecProfileGateway.CompanionMutationRequest release(
            final UUID companionId, final String operationId) {
        return new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,
                ClassSpecProfileGateway.CompanionMutationRequest.Kind.REMOVE, companionId, null,
                "", 0, 0L, 0L, List.of(), Map.of(), operationId);
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
        volatile boolean rejectSaves;

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
            if (rejectSaves) {
                return CompletableFuture.completedFuture(SaveResult.failed(profile, "store offline"));
            }
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
