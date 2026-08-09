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
 * Dependency-free Profile v2 gateway regressions for the Boszorkánymester rollout: the allowlist admits
 * warlock, the DARK Demonológus still answers to the existing gate system, and doctrines stay
 * slot-local.
 */
public final class WarlockProfileRegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000004c1");
    private static int assertions;

    private WarlockProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        warlockSecondSlotUnlocksAndSwitches();
        darkDemonologistObeysTheExistingGates();
        warlockDoctrineMasteryAndCapstoneStaySlotLocal();
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
