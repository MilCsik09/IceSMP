package hu.taliann.icesmp.warrior;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.Map;

/** Dependency-free Profile v2 isolation checks for the Warrior two-loadout gameplay. */
public final class WarriorProfileRegressionSuite {

    private static int assertions;

    private WarriorProfileRegressionSuite() {
    }

    public static void main(final String[] args) {
        secondSlotGateIsEnforced();
        twoWarriorSpecsRemainIsolated();
        doctrineIsSlotLocalAndImmutablePerTier();
        masteryAndCapstoneStaySlotLocal();
        activeSlotSwitchPreservesCommonClassProgression();
        sealedLoadoutCannotBecomeActive();
        System.out.println("Warrior profile regression suite passed. assertions=" + assertions);
    }

    private static void secondSlotGateIsEnforced() {
        final ClassLoadout berserker = loadout("berserker", LoadoutStatus.ACTIVE);
        expectFailure(() -> ClassSpecSection.builder()
                        .revision(0).primaryClassId("warrior").classLevel(28).classExperience(2000)
                        .loadout(LoadoutSlot.FIRST, berserker)
                        .loadout(LoadoutSlot.SECOND, loadout("guardian", LoadoutStatus.INACTIVE))
                        .activeSlot(LoadoutSlot.FIRST)
                        .secondSpecUnlocked(false).build(),
                "locked second slot rejects hidden learned spec");
    }

    private static void twoWarriorSpecsRemainIsolated() {
        final ClassLoadout berserker = loadout("berserker", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "bloodlust")
                .withMastery(new MasteryProgress(3, 340));
        final ClassLoadout guardian = loadout("guardian", LoadoutStatus.INACTIVE)
                .withDoctrineChoice("level_30", "bastion")
                .withMastery(new MasteryProgress(1, 120));
        final ClassSpecSection profile = profile(berserker, guardian, LoadoutSlot.FIRST);
        check(profile.loadout(LoadoutSlot.FIRST).specializationId().equals("berserker"),
                "first slot keeps Berserker identity");
        check(profile.loadout(LoadoutSlot.SECOND).specializationId().equals("guardian"),
                "second slot keeps Guardian identity");
        check(profile.loadout(LoadoutSlot.FIRST).mastery().rank() == 3,
                "Berserker mastery remains in first slot");
        check(profile.loadout(LoadoutSlot.SECOND).mastery().rank() == 1,
                "Guardian mastery remains in second slot");
        check(profile.loadout(LoadoutSlot.FIRST).doctrineChoices().get("level_30").equals("bloodlust"),
                "Berserker doctrine remains local");
        check(profile.loadout(LoadoutSlot.SECOND).doctrineChoices().get("level_30").equals("bastion"),
                "Guardian doctrine remains local");
    }

    private static void doctrineIsSlotLocalAndImmutablePerTier() {
        final ClassLoadout loadout = loadout("berserker", LoadoutStatus.ACTIVE)
                .withDoctrineChoice("level_30", "bloodlust")
                .withDoctrineChoice("level_40", "executioner");
        check(loadout.doctrineChoices().equals(Map.of(
                        "level_30", "bloodlust", "level_40", "executioner")),
                "multiple doctrine tiers coexist in one slot");
        check(loadout.withDoctrineChoice("level_30", "bloodlust").equals(loadout),
                "idempotent same doctrine choice is stable");
        expectFailure(() -> loadout.withDoctrineChoice("level_30", "titan"),
                "committed doctrine tier cannot silently overwrite");
    }

    private static void masteryAndCapstoneStaySlotLocal() {
        final ClassLoadout berserker = loadout("berserker", LoadoutStatus.ACTIVE)
                .withMastery(new MasteryProgress(10, 1000))
                .withCapstoneStatus(CapstoneStatus.COMPLETED);
        final ClassLoadout guardian = loadout("guardian", LoadoutStatus.INACTIVE)
                .withMastery(new MasteryProgress(2, 220))
                .withCapstoneStatus(CapstoneStatus.AVAILABLE);
        final ClassSpecSection profile = profile(berserker, guardian, LoadoutSlot.FIRST);
        check(profile.loadout(LoadoutSlot.FIRST).capstoneStatus() == CapstoneStatus.COMPLETED,
                "Berserker capstone completion stays in Berserker slot");
        check(profile.loadout(LoadoutSlot.SECOND).capstoneStatus() == CapstoneStatus.AVAILABLE,
                "Guardian trial availability stays independent");
        check(profile.loadout(LoadoutSlot.SECOND).mastery().experience() == 220,
                "switching other slot cannot copy mastery XP");
    }

    private static void activeSlotSwitchPreservesCommonClassProgression() {
        final ClassLoadout berserkerInactive = loadout("berserker", LoadoutStatus.INACTIVE)
                .withMastery(new MasteryProgress(4, 480));
        final ClassLoadout guardianActive = loadout("guardian", LoadoutStatus.ACTIVE)
                .withMastery(new MasteryProgress(2, 210));
        final ClassSpecSection switched = profile(berserkerInactive, guardianActive, LoadoutSlot.SECOND);
        check(switched.primaryClassId().equals("warrior"), "switch preserves one Warrior class");
        check(switched.classLevel() == 50, "switch preserves common class level");
        check(switched.classExperience() == 9999, "switch preserves common class XP");
        check(switched.activeSlot() == LoadoutSlot.SECOND, "exactly target slot becomes active");
        check(switched.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.INACTIVE,
                "old slot becomes inactive rather than deleted");
    }

    private static void sealedLoadoutCannotBecomeActive() {
        expectFailure(() -> profile(
                        loadout("berserker", LoadoutStatus.INACTIVE),
                        new ClassLoadout("guardian", LoadoutStatus.SEALED,
                                new SealReason(SealCause.ADMINISTRATIVE, "", "test"),
                                Map.of(), MasteryProgress.empty(), null, java.util.Set.of(), "",
                                CapstoneStatus.LOCKED, Map.of(), Map.of(), ""),
                        LoadoutSlot.SECOND),
                "activeSlot cannot point at SEALED loadout");
    }

    private static ClassSpecSection profile(final ClassLoadout first,
                                            final ClassLoadout second,
                                            final LoadoutSlot active) {
        return ClassSpecSection.builder()
                .revision(5)
                .primaryClassId("warrior")
                .classLevel(50)
                .classExperience(9999)
                .secondSpecUnlocked(true)
                .loadout(LoadoutSlot.FIRST, first)
                .loadout(LoadoutSlot.SECOND, second)
                .activeSlot(active)
                .build();
    }

    private static ClassLoadout loadout(final String spec, final LoadoutStatus status) {
        return new ClassLoadout(spec, status, null, Map.of(), MasteryProgress.empty(), null,
                java.util.Set.of(), "", CapstoneStatus.LOCKED, Map.of(), Map.of(), "");
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
