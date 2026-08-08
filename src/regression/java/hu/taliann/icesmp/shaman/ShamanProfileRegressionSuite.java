package hu.taliann.icesmp.shaman;

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
 * Dependency-free Profile v2 gateway regressions for the Sámán rollout: the allowlist admits
 * shaman, the two-loadout limit holds for the three-spec class and doctrines stay slot-local.
 */
public final class ShamanProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000441");
    private static int assertions;

    private ShamanProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        shamanSecondSlotUnlocksAndSwitches();
        threeSpecClassStillHoldsOnlyTwoLoadouts();
        shamanDoctrineMasteryAndCapstoneStaySlotLocal();
        System.out.println("Shaman profile regression suite passed. assertions=" + assertions);
    }

    private static void shamanSecondSlotUnlocksAndSwitches() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("shaman").classLevel(1).build());
        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "shaman-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for shaman");
        check(h.store.profile.secondSpecUnlocked(),
                "shaman second spec unlocks through the allowlist");

        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "elemental", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "shaman learns Elemi");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "tidal", LoadoutSlot.SECOND, openGates()))
                .toCompletableFuture().join().committed(), "shaman learns Hullámhívó second");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "shaman loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("tidal"),
                "switch activates the Hullámhívó loadout");
    }

    private static void threeSpecClassStillHoldsOnlyTwoLoadouts() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("shaman").classLevel(30).classExperience(100_000)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, loadout("elemental", LoadoutStatus.ACTIVE))
                .loadout(LoadoutSlot.SECOND, loadout("enhancement", LoadoutStatus.INACTIVE))
                .activeSlot(LoadoutSlot.FIRST)
                .build());
        final var third = h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                "tidal", LoadoutSlot.SECOND, openGates())).toCompletableFuture().join();
        check(third.status() == ProfileMutationResult.Status.REJECTED,
                "a third spec cannot silently displace an occupied loadout");
        check(h.store.profile.loadout(LoadoutSlot.SECOND).specializationId().equals("enhancement"),
                "the occupied loadout is untouched — respec is the only path to the third spec");
    }

    private static void shamanDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout elemental = loadout("elemental", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "eleven_szikra")
                .withDoctrineChoice("level_40", "vihar_hirnoke")
                .withMastery(new MasteryProgress(3, 310))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout tidal = loadout("tidal", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "melyviz")
                .withMastery(new MasteryProgress(1, 130))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(3).primaryClassId("shaman").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, elemental)
                .loadout(LoadoutSlot.SECOND, tidal)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "eleven_szikra", "level_40", "vihar_hirnoke")),
                "Elemi doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "melyviz")),
                "Hullámhívó doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 3
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 1,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "mely_gyokerek"),
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
