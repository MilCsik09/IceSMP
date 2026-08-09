package hu.taliann.icesmp.assassin;

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
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Dependency-free Profile v2 gateway regressions for the Orgyilkos rollout: the allowlist admits
 * assassin, the DARK Pestishozó still answers to the existing gate system, and doctrines stay
 * slot-local.
 */
public final class AssassinProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000004b1");
    private static int assertions;

    private AssassinProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        assassinSecondSlotUnlocksAndSwitches();
        darkPlaguebringerObeysTheExistingGates();
        assassinDoctrineMasteryAndCapstoneStaySlotLocal();
        System.out.println("Assassin profile regression suite passed. assertions=" + assertions);
    }

    private static void assassinSecondSlotUnlocksAndSwitches() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("assassin").classLevel(1).build());
        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "assassin-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for assassin");
        check(h.store.profile.secondSpecUnlocked(),
                "assassin second spec unlocks through the allowlist");

        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "poisoner", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "assassin learns Méregkeverő");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "phantom", LoadoutSlot.SECOND, openGates()))
                .toCompletableFuture().join().committed(), "assassin learns Fantom second");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "assassin loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("phantom"),
                "switch activates the Fantom loadout");
    }

    private static void darkPlaguebringerObeysTheExistingGates() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("assassin").classLevel(30).classExperience(100_000)
                .secondSpecUnlocked(true).build());
        final var blocked = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "plaguebringer", LoadoutSlot.FIRST, closedGates())).toCompletableFuture().join();
        check(blocked.status() == ProfileMutationResult.Status.REJECTED,
                "the DARK Pestishozó is refused while its gates are unsatisfied");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.EMPTY,
                "a refused DARK learn leaves the loadout untouched");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "plaguebringer", LoadoutSlot.FIRST, satisfiedGates()))
                .toCompletableFuture().join().committed(),
                "satisfied gates admit the Pestishozó through the existing DARK system");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).specializationId().equals("plaguebringer"),
                "the admitted DARK spec occupies its loadout");
    }

    private static void assassinDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout poisoner = loadout("poisoner", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "gyors_kever")
                .withDoctrineChoice("level_40", "arnyekbol")
                .withMastery(new MasteryProgress(5, 530))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout phantom = loadout("phantom", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "halk_lepes")
                .withMastery(new MasteryProgress(1, 110))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(5).primaryClassId("assassin").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, poisoner)
                .loadout(LoadoutSlot.SECOND, phantom)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "gyors_kever", "level_40", "arnyekbol")),
                "Méregkeverő doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "halk_lepes")),
                "Fantom doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 5
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 1,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "hosszu_pillanat"),
                "committed doctrine tier cannot silently overwrite");
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
