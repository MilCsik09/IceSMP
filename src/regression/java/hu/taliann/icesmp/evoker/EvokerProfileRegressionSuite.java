package hu.taliann.icesmp.evoker;

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
 * Dependency-free Profile v2 gateway regressions for the Sárkányidéző two-loadout rollout:
 * the explicit gameplay-v2 allowlist admits evoker and keeps every unreworked class fail-closed.
 */
public final class EvokerProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000411");
    private static int assertions;

    private EvokerProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        evokerSecondSlotUnlocksThroughClassExperience();
        evokerLearnsAndSwitchesBothSpecs();
        unreworkedClassStaysFailClosed();
        evokerDoctrineMasteryAndCapstoneStaySlotLocal();
        System.out.println("Evoker profile regression suite passed. assertions=" + assertions);
    }

    private static void evokerSecondSlotUnlocksThroughClassExperience() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("evoker").classLevel(1).build());
        final var beforeGate = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "devastation", LoadoutSlot.SECOND, openGates())).toCompletableFuture().join();
        check(beforeGate.status() == ProfileMutationResult.Status.REJECTED,
                "locked second slot rejects direct SECOND learning");

        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "evoker-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for evoker");
        check(h.store.profile.classLevel() >= 28, "XP reaches the second-spec level");
        check(h.store.profile.secondSpecUnlocked(),
                "evoker second spec unlocks via the allowlist, exactly like warrior");
    }

    private static void evokerLearnsAndSwitchesBothSpecs() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("evoker").classLevel(28).classExperience(100_000)
                .secondSpecUnlocked(true).build());
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "devastation", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "evoker learns Perzselés");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "preservation", LoadoutSlot.SECOND, openGates()))
                .toCompletableFuture().join().committed(), "evoker learns Megőrzés second");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("devastation"),
                "first learned spec becomes active");

        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "evoker loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("preservation"),
                "switch activates the second loadout");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.INACTIVE,
                "previous loadout becomes inactive, not deleted");
    }

    /** Class/spec pairs (non-DARK) rotated through as the rework progresses. */
    private static final java.util.List<String[]> UNREWORKED_CANDIDATES = java.util.List.of(
            new String[]{"paladin", "holy", "retribution"},
            new String[]{"demon_hunter", "havoc", "vengeance"},
            new String[]{"priest", "discipline", "shadow"},
            new String[]{"death_knight", "blood", "frost"},
            new String[]{"assassin", "poisoner", "phantom"},
            new String[]{"druid", "feral", "lunar"},
            new String[]{"warlock", "affliction", "destruction"});

    private static void unreworkedClassStaysFailClosed() {
        final String[] candidate = UNREWORKED_CANDIDATES.stream()
                .filter(entry -> !hu.taliann.icesmp.classspec.application.GameplayV2ClassPolicy
                        .isEnabled(entry[0]))
                .findFirst().orElse(null);
        if (candidate == null) {
            System.out.println("All catalog classes are gameplay-v2 enabled; "
                    + "fail-closed case retired.");
            return;
        }
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId(candidate[0]).classLevel(28).classExperience(100_000)
                .loadout(LoadoutSlot.FIRST, loadout(candidate[1], LoadoutStatus.ACTIVE))
                .activeSlot(LoadoutSlot.FIRST).build());

        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,
                        1_000, 100, 0, 28, "unreworked-xp")).toCompletableFuture().join();
        check(xp.committed(), "unreworked class still earns XP normally");
        check(!h.store.profile.secondSpecUnlocked(),
                "even past level 28, XP never unlocks the second slot outside the allowlist");

        final var select = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                candidate[2], LoadoutSlot.SECOND, openGates())).toCompletableFuture().join();
        check(select.status() == ProfileMutationResult.Status.REJECTED,
                "SECOND learning is rejected outside the allowlist");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.status() == ProfileMutationResult.Status.REJECTED,
                "loadout switching is rejected outside the allowlist");
    }

    private static void evokerDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout devastation = loadout("devastation", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "gyujtopont")
                .withDoctrineChoice("level_40", "iker_aram")
                .withMastery(new MasteryProgress(3, 320))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout preservation = loadout("preservation", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "melyebb_visszhang")
                .withMastery(new MasteryProgress(1, 110))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(6).primaryClassId("evoker").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, devastation)
                .loadout(LoadoutSlot.SECOND, preservation)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "gyujtopont", "level_40", "iker_aram")),
                "Perzselés doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "melyebb_visszhang")),
                "Megőrzés doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 3
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 1,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "hosszu_lelegzet"),
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
