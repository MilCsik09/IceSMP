package hu.taliann.icesmp.classspec.profile;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.NumericGuards;
import hu.taliann.icesmp.classspec.domain.ProfileDiagnostics;
import hu.taliann.icesmp.classspec.domain.ProfileOperation;
import hu.taliann.icesmp.classspec.domain.ProfileOperationStatus;
import hu.taliann.icesmp.classspec.domain.ProfileOperationType;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executable ClassSpecSection domain regressions; persistence is structured YAML. */
public final class ClassSpecSectionV2RegressionSuite {
    private static int assertions;

    private ClassSpecSectionV2RegressionSuite() {
    }

    public static void main(final String[] args) {
        emptySectionDefaults();
        domainInvariants();
        numericBounds();
        collectionBoundsAndImmutability();
        deterministicLogicalCollections();
        noBukkitOrOwnerBindingInSection();
        System.out.println("ClassSpecSection v2 domain regression tests passed. assertions=" + assertions);
    }

    private static void emptySectionDefaults() {
        final ClassSpecSection section = ClassSpecSection.empty(0);
        check(section.sectionId() == ProfileSectionId.CLASS_SPEC, "section id");
        check(section.schemaVersion() == ClassSpecSection.SCHEMA_VERSION, "schema");
        check(section.revision() == 0, "revision zero");
        check(section.status() == ProfileStatus.READY, "ready");
        check(section.activeSlot() == null, "inactive");
        check(section.loadouts().size() == 2, "two slots");
        check(section.primaryClassId().isEmpty(), "classless");
        expect(IllegalArgumentException.class, () -> ClassSpecSection.empty(-1));
    }

    private static void domainInvariants() {
        final ClassLoadout necromancer = loadout(
                "necromancer", LoadoutStatus.ACTIVE, null, Map.of(), Map.of());
        final ClassSpecSection active = ClassSpecSection.builder()
                .revision(4)
                .primaryClassId("wizard")
                .classLevel(20)
                .classExperience(900)
                .activeSlot(LoadoutSlot.FIRST)
                .loadout(LoadoutSlot.FIRST, necromancer)
                .build();
        check(active.isGameplayUsable(), "active section usable");
        check(active.loadout(LoadoutSlot.FIRST).specializationId().equals("necromancer"),
                "spec stored");

        expect(IllegalArgumentException.class, () -> ClassSpecSection.builder()
                .primaryClassId("wizard")
                .classLevel(10)
                .activeSlot(LoadoutSlot.FIRST)
                .loadout(LoadoutSlot.FIRST,
                        loadout("guardian", LoadoutStatus.ACTIVE, null, Map.of(), Map.of()))
                .build());
        expect(IllegalArgumentException.class, () -> ClassSpecSection.builder()
                .primaryClassId("wizard")
                .classLevel(10)
                .activeSlot(LoadoutSlot.FIRST)
                .loadout(LoadoutSlot.FIRST, loadout(
                        "necromancer",
                        LoadoutStatus.SEALED,
                        new SealReason(SealCause.FACTION_MISSING, "dark", ""),
                        Map.of(),
                        Map.of()))
                .build());
        expect(IllegalArgumentException.class, () -> new ClassLoadout(
                "", LoadoutStatus.EMPTY, null, Map.of(), MasteryProgress.empty(), null,
                Set.of(), "", CapstoneStatus.LOCKED, Map.of(), Map.of("hidden", "x"), ""));

        final CompanionProfile beast = new CompanionProfile(
                UUID.randomUUID(), "beast_master.stable", "WOLF", "Fang", 1, 0,
                "", "ACTIVE", List.of(), 0, Map.of());
        expect(IllegalArgumentException.class, () -> loadout(
                "necromancer", LoadoutStatus.INACTIVE, null,
                Map.of(beast.companionId(), beast), Map.of()));

        final ClassSpecSection review = ClassSpecSection.builder()
                .status(ProfileStatus.REVIEW)
                .diagnostics(new ProfileDiagnostics("", "", "", "manual review"))
                .build();
        expect(IllegalStateException.class, review::toBuilder);
        expect(IllegalStateException.class, review::withoutClass);

        final ClassSpecSection quarantined = ClassSpecSection.builder()
                .status(ProfileStatus.QUARANTINED)
                .diagnostics(new ProfileDiagnostics("ev-1", "bad digest", "", "quarantined"))
                .build();
        expect(IllegalStateException.class, quarantined::toBuilder);
        check(!quarantined.isGameplayUsable(), "quarantine blocked");

        final SealReason both = new SealReason(Map.of(
                SealCause.FACTION_MISSING, "dark",
                SealCause.SINNER_MARK_MISSING, "sinner"), "gates");
        check(both.causes().size() == 2, "complete seal set");
        check(both.gateRestorableOnly(), "restorable gates");
    }

    private static void numericBounds() {
        check(NumericGuards.addInt(2, 3, "x") == 5, "safe int");
        check(NumericGuards.addLong(2, 3, "x") == 5, "safe long");
        check(NumericGuards.nextRevision(9) == 10, "next revision");
        expect(IllegalArgumentException.class,
                () -> NumericGuards.addInt(Integer.MAX_VALUE, 1, "x"));
        expect(IllegalArgumentException.class,
                () -> NumericGuards.addLong(Long.MAX_VALUE, 1, "x"));
        expect(IllegalStateException.class, () -> NumericGuards.nextRevision(Long.MAX_VALUE));
        expect(IllegalArgumentException.class, () -> new MasteryProgress(-1, 0));
        expect(IllegalArgumentException.class, () -> new CompanionProfile(
                UUID.randomUUID(), "beast_master.stable", "WOLF", "", 0, 0,
                "", "", List.of(), 0, Map.of()));
    }

    private static void collectionBoundsAndImmutability() {
        final ClassSpecSection section = ClassSpecSection.builder()
                .operation(operation("op-1"))
                .build();
        expect(UnsupportedOperationException.class,
                () -> section.operations().put("other", operation("other")));
        expect(UnsupportedOperationException.class,
                () -> section.loadouts().add(ClassLoadout.empty()));

        final ClassSpecSection.Builder oversized = ClassSpecSection.builder();
        for (int index = 0; index <= ClassSpecSection.MAX_OPERATION_RECEIPTS; index++) {
            oversized.operation(operation("op-" + index));
        }
        expect(IllegalArgumentException.class, oversized::build);
    }

    private static void deterministicLogicalCollections() {
        final CompanionProfile firstCompanion = new CompanionProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000211"),
                "beast_master.stable", "WOLF", "A", 1, 0,
                "", "ACTIVE", List.of(), 0, Map.of("z", "2", "a", "1"));
        final CompanionProfile secondCompanion = new CompanionProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000212"),
                "beast_master.stable", "FOX", "B", 1, 0,
                "", "ACTIVE", List.of(), 0, Map.of());
        final Map<UUID, CompanionProfile> first = new LinkedHashMap<>();
        first.put(firstCompanion.companionId(), firstCompanion);
        first.put(secondCompanion.companionId(), secondCompanion);
        final Map<UUID, CompanionProfile> second = new LinkedHashMap<>();
        second.put(secondCompanion.companionId(), secondCompanion);
        second.put(firstCompanion.companionId(), firstCompanion);

        final ClassSpecSection left = ClassSpecSection.builder()
                .primaryClassId("archer")
                .classLevel(1)
                .loadout(LoadoutSlot.FIRST,
                        loadout("beast_master", LoadoutStatus.INACTIVE, null, first, Map.of()))
                .build();
        final ClassSpecSection right = ClassSpecSection.builder()
                .primaryClassId("archer")
                .classLevel(1)
                .loadout(LoadoutSlot.FIRST,
                        loadout("beast_master", LoadoutStatus.INACTIVE, null, second, Map.of()))
                .build();
        check(left.equals(right), "logical map insertion order independent");
        check(left.hashCode() == right.hashCode(), "stable logical hash");
        expect(IllegalArgumentException.class, () -> new CompanionProfile(
                UUID.randomUUID(), "beast_master.stable", "WOLF", "A", 1, 0,
                "", "ACTIVE", List.of(), 0, Map.of(" A ", "1", "a", "2")));
    }

    private static void noBukkitOrOwnerBindingInSection() {
        final List<String> forbidden = new ArrayList<>();
        for (final Field field : ClassSpecSection.class.getDeclaredFields()) {
            final String type = field.getType().getName();
            if (type.startsWith("org.bukkit.") || type.equals(UUID.class.getName())) {
                forbidden.add(field.getName() + ":" + type);
            }
        }
        check(forbidden.isEmpty(), "section contains forbidden runtime/owner fields: " + forbidden);
    }

    private static ProfileOperation operation(final String id) {
        return new ProfileOperation(
                id, ProfileOperationType.COMPANION_MUTATION,
                ProfileOperationStatus.COMMITTED, "ADD", "0", "none", 0, "ok");
    }

    private static ClassLoadout loadout(
            final String spec,
            final LoadoutStatus status,
            final SealReason seal,
            final Map<UUID, CompanionProfile> roster,
            final Map<String, String> mechanics) {
        return new ClassLoadout(
                spec, status, seal, Map.of(), MasteryProgress.empty(), null,
                Set.of(), "", CapstoneStatus.LOCKED, roster, mechanics, "");
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void expect(
            final Class<? extends Throwable> type,
            final Throwing operation) {
        assertions++;
        try {
            operation.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (final Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError(
                        "Expected " + type.getSimpleName() + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
