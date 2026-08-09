package hu.taliann.icesmp.druid;

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
 * Dependency-free Profile v2 gateway regressions for the Druida rollout: the allowlist admits
 * druid, the two-loadout limit still holds for the first four-spec class, and the third spec
 * cannot squeeze into an occupied loadout.
 */
public final class DruidProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000481");
    private static int assertions;

    private DruidProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        druidSecondSlotUnlocksAndSwitches();
        fourSpecClassStillHoldsOnlyTwoLoadouts();
        druidDoctrineMasteryAndCapstoneStaySlotLocal();
        System.out.println("Druid profile regression suite passed. assertions=" + assertions);
    }

    private static void druidSecondSlotUnlocksAndSwitches() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("druid").classLevel(1).build());
        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "druid-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for druid");
        check(h.store.profile.secondSpecUnlocked(),
                "druid second spec unlocks through the allowlist");

        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "feral", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "druid learns Vadőr");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "restoration", LoadoutSlot.SECOND, openGates()))
                .toCompletableFuture().join().committed(), "druid learns Helyreállító second");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "druid loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("restoration"),
                "switch activates the Helyreállító loadout");
    }

    private static void fourSpecClassStillHoldsOnlyTwoLoadouts() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("druid").classLevel(30).classExperience(100_000)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, loadout("feral", LoadoutStatus.ACTIVE))
                .loadout(LoadoutSlot.SECOND, loadout("lunar", LoadoutStatus.INACTIVE))
                .activeSlot(LoadoutSlot.FIRST)
                .build());
        final var third = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "ironbark", LoadoutSlot.SECOND, openGates())).toCompletableFuture().join();
        check(third.status() == ProfileMutationResult.Status.REJECTED,
                "a four-spec class still carries only two loadouts");
        check(h.store.profile.loadout(LoadoutSlot.SECOND).specializationId().equals("lunar"),
                "the occupied loadout is untouched by a third-spec attempt");
        final var duplicate = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "feral", LoadoutSlot.SECOND, openGates())).toCompletableFuture().join();
        check(duplicate.status() == ProfileMutationResult.Status.REJECTED,
                "the same spec cannot occupy both loadouts");
    }

    private static void druidDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout feral = loadout("feral", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "ragadozo_osztone")
                .withDoctrineChoice("level_40", "szagnyom_mestere")
                .withMastery(new MasteryProgress(4, 420))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout restoration = loadout("restoration", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "bo_vetes")
                .withMastery(new MasteryProgress(2, 210))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(4).primaryClassId("druid").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, feral)
                .loadout(LoadoutSlot.SECOND, restoration)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "ragadozo_osztone",
                                "level_40", "szagnyom_mestere")),
                "Vadőr doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "bo_vetes")),
                "Helyreállító doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 4
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 2,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "eles_karom"),
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
