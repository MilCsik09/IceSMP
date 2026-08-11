package hu.taliann.icesmp.playerprofile.domain;

import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionProfileState;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionSection;

import java.util.Map;
import java.util.Set;

/** Dependency-free profession authority regressions. */
public final class ProfessionProfileStateRegressionSuite {

    private static int assertions;

    private ProfessionProfileStateRegressionSuite() {
    }

    public static void main(final String[] args) {
        primarySlotsAreExclusive();
        experienceAndLevelMirrorAreAtomic();
        recipesAreCanonicalAndIdempotent();
        invalidInputsFailClosed();
        System.out.println("Profession profile state regression suite passed. assertions=" + assertions);
    }

    private static void primarySlotsAreExclusive() {
        final ProfessionType gathering = first(ProfessionCategory.GATHERING);
        final ProfessionType otherGathering = second(ProfessionCategory.GATHERING);
        final ProfessionType crafting = first(ProfessionCategory.CRAFTING);
        final ProfessionSection empty = ProfessionSection.empty(0L);

        final var selectedGathering = ProfessionProfileState.select(empty, gathering);
        check(selectedGathering.changed(), "first gathering selection changes");
        check(ProfessionProfileState.selected(selectedGathering.section(), ProfessionCategory.GATHERING)
                == gathering, "gathering selected");
        final var blocked = ProfessionProfileState.select(selectedGathering.section(), otherGathering);
        check(!blocked.changed(), "occupied gathering slot rejects select");
        check(ProfessionProfileState.selected(blocked.section(), ProfessionCategory.GATHERING)
                == gathering, "occupied slot unchanged");

        final var selectedCrafting = ProfessionProfileState.select(blocked.section(), crafting);
        check(selectedCrafting.changed(), "independent crafting slot changes");
        check(ProfessionProfileState.hasProfession(selectedCrafting.section(), gathering),
                "selected gathering active");
        check(ProfessionProfileState.hasProfession(selectedCrafting.section(), crafting),
                "selected crafting active");
        for (final ProfessionType type : ProfessionType.values()) {
            if (type.getCategory() == ProfessionCategory.SECONDARY) {
                check(ProfessionProfileState.hasProfession(selectedCrafting.section(), type),
                        "secondary profession automatically active");
            }
        }

        final var adminOverride = ProfessionProfileState.set(selectedCrafting.section(), otherGathering);
        check(adminOverride.changed(), "admin override changes slot");
        check(ProfessionProfileState.selected(adminOverride.section(), ProfessionCategory.GATHERING)
                == otherGathering, "override persisted");
        final var cleared = ProfessionProfileState.clear(adminOverride.section(), ProfessionCategory.GATHERING);
        check(cleared.changed(), "clear changes occupied slot");
        check(ProfessionProfileState.selected(cleared.section(), ProfessionCategory.GATHERING) == null,
                "slot cleared");
        check(!ProfessionProfileState.clear(cleared.section(), ProfessionCategory.GATHERING).changed(),
                "clear empty slot is idempotent");
    }

    private static void experienceAndLevelMirrorAreAtomic() {
        final ProfessionType profession = first(ProfessionCategory.GATHERING);
        final ProfessionSection empty = ProfessionSection.empty(0L);
        final var first = ProfessionProfileState.setExperience(empty, profession,
                100L, 100, 15, 50);
        check(first.changed(), "setting xp changes section");
        check(first.previousExperience() == 0L && first.experience() == 100L,
                "xp before/after recorded");
        check(first.previousLevel() == 1 && first.level() == 2,
                "level transition recorded");
        check(first.section().experience().get(profession.getId()) == 100L,
                "xp persisted");
        check(first.section().levels().get(profession.getId()) == 2,
                "level mirror persisted");
        check(ProfessionProfileState.level(first.section(), profession, 100, 15, 50) == 2,
                "level mirror verified");

        final var added = ProfessionProfileState.addExperience(first.section(), profession,
                115L, 100, 15, 50);
        check(added.level() == 3, "progressive second level cost applied");
        check(added.experience() == 215L, "xp addition exact");
        check(!ProfessionProfileState.setExperience(added.section(), profession,
                215L, 100, 15, 50).changed(), "same xp is idempotent");

        final ProfessionSection drifted = new ProfessionSection(
                added.section().experience(), Map.of(profession.getId(), 49),
                added.section().specializations(), added.section().recipes(),
                added.section().weeklyProgress(), added.section().extensions());
        expect(IllegalStateException.class, () ->
                ProfessionProfileState.level(drifted, profession, 100, 15, 50));
    }

    private static void recipesAreCanonicalAndIdempotent() {
        final ProfessionSection empty = ProfessionSection.empty(0L);
        final var learned = ProfessionProfileState.learnRecipe(empty, "Iron_Sword");
        check(learned.changed(), "new recipe changes section");
        check(learned.recipeId().equals("iron_sword"), "recipe normalized");
        check(learned.section().recipes().equals(Set.of("iron_sword")),
                "recipe persisted");
        check(!ProfessionProfileState.learnRecipe(learned.section(), "IRON_SWORD").changed(),
                "recipe learning idempotent");
    }

    private static void invalidInputsFailClosed() {
        final ProfessionSection empty = ProfessionSection.empty(0L);
        final ProfessionType secondary = first(ProfessionCategory.SECONDARY);
        final ProfessionType profession = first(ProfessionCategory.GATHERING);
        expect(IllegalArgumentException.class, () -> ProfessionProfileState.select(empty, secondary));
        expect(IllegalArgumentException.class, () ->
                ProfessionProfileState.setExperience(empty, profession, -1L, 100, 15, 50));
        expect(IllegalArgumentException.class, () ->
                ProfessionProfileState.addExperience(empty, profession, 0L, 100, 15, 50));
        expect(IllegalArgumentException.class, () -> ProfessionProfileState.learnRecipe(empty, "bad:id"));
    }

    private static ProfessionType first(final ProfessionCategory category) {
        for (final ProfessionType type : ProfessionType.values()) {
            if (type.getCategory() == category) {
                return type;
            }
        }
        throw new AssertionError("No profession for " + category);
    }

    private static ProfessionType second(final ProfessionCategory category) {
        boolean found = false;
        for (final ProfessionType type : ProfessionType.values()) {
            if (type.getCategory() != category) {
                continue;
            }
            if (found) {
                return type;
            }
            found = true;
        }
        throw new AssertionError("Less than two professions for " + category);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(final Class<? extends Throwable> expected,
                               final Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (final Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
