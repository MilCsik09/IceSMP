package hu.taliann.icesmp.archer;

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
 * Dependency-free Profile v2 gateway regressions for the Íjász rollout: the allowlist admits
 * archer, the Vadmester stable stays slot-local and a loadout switch never touches the roster.
 */
public final class ArcherProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000421");
    private static int assertions;

    private ArcherProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        archerSecondSlotUnlocksAndSwitches();
        stableRosterSurvivesLoadoutSwitch();
        archerDoctrineMasteryAndCapstoneStaySlotLocal();
        System.out.println("Archer profile regression suite passed. assertions=" + assertions);
    }

    private static void archerSecondSlotUnlocksAndSwitches() {
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("archer").classLevel(1).build());
        final var xp = h.gateway.mutateClassExperience(PLAYER,
                new ClassSpecProfileGateway.ClassExperienceRequest(
                        ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                        100_000, 100, 0, 28, "archer-xp-28")).toCompletableFuture().join();
        check(xp.committed(), "class XP mutation commits for archer");
        check(h.store.profile.secondSpecUnlocked(),
                "archer second spec unlocks through the allowlist");

        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "sharpshooter", LoadoutSlot.FIRST, openGates()))
                .toCompletableFuture().join().committed(), "archer learns Mesterlövész");
        check(h.gateway.select(PLAYER, new ClassSpecProfileGateway.SelectRequest(
                        "beast_master", LoadoutSlot.SECOND, openGates()))
                .toCompletableFuture().join().committed(), "archer learns Vadmester second");
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "archer loadout switching is enabled");
        check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("beast_master"),
                "switch activates the Vadmester loadout");
    }

    private static void stableRosterSurvivesLoadoutSwitch() {
        final UUID wolfId = UUID.fromString("00000000-0000-0000-0000-000000000431");
        final CompanionProfile wolf = new CompanionProfile(wolfId, "beast_master.stable",
                "WOLF", "Csikasz", 3, 240L, "", "ACTIVE", List.of(), 0L, Map.of());
        final Harness h = harness(ClassSpecSection.builder()
                .primaryClassId("archer").classLevel(30).classExperience(100_000)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, new ClassLoadout("beast_master",
                        LoadoutStatus.ACTIVE, null, Map.of(), MasteryProgress.empty(), null,
                        Set.of(), "", CapstoneStatus.LOCKED, Map.of(wolfId, wolf), Map.of(), ""))
                .loadout(LoadoutSlot.SECOND, loadout("sharpshooter", LoadoutStatus.INACTIVE))
                .activeSlot(LoadoutSlot.FIRST)
                .build());
        final var switched = h.gateway.switchLoadout(PLAYER,
                        new ClassSpecProfileGateway.SwitchRequest(LoadoutSlot.SECOND))
                .toCompletableFuture().join();
        check(switched.committed(), "switch away from Vadmester commits");
        final ClassLoadout stable = h.store.profile.loadout(LoadoutSlot.FIRST);
        check(stable.companionRoster().containsKey(wolfId),
                "the stabled companion survives the loadout switch untouched");
        check(stable.companionRoster().get(wolfId).level() == 3,
                "companion progression is preserved across the switch");
    }

    private static void archerDoctrineMasteryAndCapstoneStaySlotLocal() {
        final ClassLoadout sharpshooter = loadout("sharpshooter", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "nyugodt_kez")
                .withDoctrineChoice("level_40", "eles_szem")
                .withMastery(new MasteryProgress(2, 210))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassLoadout beastMaster = loadout("beast_master", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "gondozo")
                .withMastery(new MasteryProgress(4, 470))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassSpecSection profile = ClassSpecSection.builder()
                .revision(4).primaryClassId("archer").classLevel(50).classExperience(999_999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, sharpshooter)
                .loadout(LoadoutSlot.SECOND, beastMaster)
                .activeSlot(LoadoutSlot.FIRST)
                .build();
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices()
                        .equals(Map.of("level_30", "nyugodt_kez", "level_40", "eles_szem")),
                "Mesterlövész doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices()
                        .equals(Map.of("level_30", "gondozo")),
                "Vadmester doctrines stay in their own slot");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 2
                        && profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 4,
                "mastery progress is slot-local");
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.AVAILABLE
                        && profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.COMPLETED,
                "capstone/trial state is slot-local");
        expectFailure(() -> profile.loadout(LoadoutSlot.FIRST)
                        .withDoctrineChoice("level_30", "gyors_felhuzas"),
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
