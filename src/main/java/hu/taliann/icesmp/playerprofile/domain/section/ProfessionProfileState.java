package hu.taliann.icesmp.playerprofile.domain.section;

import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure immutable operations for the PlayerProfile profession section. */
public final class ProfessionProfileState {

    private ProfessionProfileState() {
    }

    public static ProfessionType selected(final ProfessionSection section,
                                          final ProfessionCategory category) {
        Objects.requireNonNull(section, "section");
        final String slot = slot(category);
        return slot == null ? null : ProfessionType.fromId(section.specializations().get(slot));
    }

    public static boolean hasProfession(final ProfessionSection section,
                                        final ProfessionType profession) {
        if (profession == null) {
            return false;
        }
        if (profession.getCategory() == ProfessionCategory.SECONDARY) {
            return true;
        }
        return selected(section, profession.getCategory()) == profession;
    }

    public static Mutation select(final ProfessionSection section,
                                  final ProfessionType profession) {
        requirePrimary(profession);
        final String slot = Objects.requireNonNull(slot(profession.getCategory()));
        if (section.specializations().containsKey(slot)) {
            return new Mutation(section, false);
        }
        return set(section, profession);
    }

    public static Mutation set(final ProfessionSection section,
                               final ProfessionType profession) {
        Objects.requireNonNull(section, "section");
        requirePrimary(profession);
        final String slot = Objects.requireNonNull(slot(profession.getCategory()));
        if (profession.getId().equals(section.specializations().get(slot))) {
            return new Mutation(section, false);
        }
        final Map<String, String> slots = new LinkedHashMap<>(section.specializations());
        slots.put(slot, profession.getId());
        return new Mutation(copy(section, section.experience(), section.levels(), slots,
                section.recipes()), true);
    }

    public static Mutation clear(final ProfessionSection section,
                                 final ProfessionCategory category) {
        Objects.requireNonNull(section, "section");
        final String slot = slot(category);
        if (slot == null || !section.specializations().containsKey(slot)) {
            return new Mutation(section, false);
        }
        final Map<String, String> slots = new LinkedHashMap<>(section.specializations());
        slots.remove(slot);
        return new Mutation(copy(section, section.experience(), section.levels(), slots,
                section.recipes()), true);
    }

    public static long experience(final ProfessionSection section,
                                  final ProfessionType profession) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(profession, "profession");
        return section.experience().getOrDefault(profession.getId(), 0L);
    }

    public static int level(final ProfessionSection section,
                            final ProfessionType profession,
                            final int baseXp,
                            final int incrementPerLevel,
                            final int maxLevel) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(profession, "profession");
        final int derived = levelForExperience(experience(section, profession),
                baseXp, incrementPerLevel, maxLevel);
        final Integer stored = section.levels().get(profession.getId());
        if (stored != null && stored != derived) {
            throw new IllegalStateException("Profession level mirror drift for " + profession.getId()
                    + ": stored=" + stored + ", derived=" + derived);
        }
        return derived;
    }

    public static ExperienceMutation setExperience(final ProfessionSection section,
                                                   final ProfessionType profession,
                                                   final long experience,
                                                   final int baseXp,
                                                   final int incrementPerLevel,
                                                   final int maxLevel) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(profession, "profession");
        if (experience < 0L || experience > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("profession experience out of range");
        }
        final long previous = experience(section, profession);
        final int previousLevel = levelForExperience(previous, baseXp, incrementPerLevel, maxLevel);
        final int nextLevel = levelForExperience(experience, baseXp, incrementPerLevel, maxLevel);
        if (previous == experience
                && section.levels().getOrDefault(profession.getId(), nextLevel) == nextLevel) {
            return new ExperienceMutation(section, false, previous, experience,
                    previousLevel, nextLevel);
        }
        final Map<String, Long> xp = new LinkedHashMap<>(section.experience());
        final Map<String, Integer> levels = new LinkedHashMap<>(section.levels());
        if (experience == 0L) {
            xp.remove(profession.getId());
        } else {
            xp.put(profession.getId(), experience);
        }
        levels.put(profession.getId(), nextLevel);
        return new ExperienceMutation(copy(section, xp, levels, section.specializations(),
                section.recipes()), true, previous, experience, previousLevel, nextLevel);
    }

    public static ExperienceMutation addExperience(final ProfessionSection section,
                                                   final ProfessionType profession,
                                                   final long amount,
                                                   final int baseXp,
                                                   final int incrementPerLevel,
                                                   final int maxLevel) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("profession experience amount must be positive");
        }
        return setExperience(section, profession,
                Math.addExact(experience(section, profession), amount),
                baseXp, incrementPerLevel, maxLevel);
    }

    public static RecipeMutation learnRecipe(final ProfessionSection section,
                                             final String rawRecipeId) {
        Objects.requireNonNull(section, "section");
        final String recipeId = normalizeId(rawRecipeId, "recipe id");
        if (section.recipes().contains(recipeId)) {
            return new RecipeMutation(section, false, recipeId);
        }
        final Set<String> recipes = new LinkedHashSet<>(section.recipes());
        recipes.add(recipeId);
        return new RecipeMutation(copy(section, section.experience(), section.levels(),
                section.specializations(), recipes), true, recipeId);
    }

    public static int levelForExperience(final long rawExperience,
                                         final int rawBaseXp,
                                         final int rawIncrement,
                                         final int rawMaxLevel) {
        if (rawExperience < 0L) {
            throw new IllegalArgumentException("profession experience cannot be negative");
        }
        final long baseXp = Math.max(1L, rawBaseXp);
        final long increment = Math.max(0L, rawIncrement);
        final int maxLevel = Math.max(1, rawMaxLevel);
        int level = 1;
        long remaining = rawExperience;
        while (level < maxLevel) {
            final long levelCost = Math.addExact(baseXp,
                    Math.multiplyExact((long) level - 1L, increment));
            if (remaining < levelCost) {
                break;
            }
            remaining -= levelCost;
            level++;
        }
        return level;
    }

    private static ProfessionSection copy(final ProfessionSection section,
                                          final Map<String, Long> experience,
                                          final Map<String, Integer> levels,
                                          final Map<String, String> specializations,
                                          final Set<String> recipes) {
        return new ProfessionSection(experience, levels, specializations, recipes,
                section.weeklyProgress(), section.extensions());
    }

    private static void requirePrimary(final ProfessionType profession) {
        Objects.requireNonNull(profession, "profession");
        if (profession.getCategory() == ProfessionCategory.SECONDARY) {
            throw new IllegalArgumentException("secondary profession has no primary slot");
        }
    }

    private static String slot(final ProfessionCategory category) {
        if (category == null || category == ProfessionCategory.SECONDARY) {
            return null;
        }
        return category.name().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]{0,95}")) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
        return normalized;
    }

    public record Mutation(ProfessionSection section, boolean changed) {
        public Mutation {
            Objects.requireNonNull(section, "section");
        }
    }

    public record ExperienceMutation(ProfessionSection section, boolean changed,
                                     long previousExperience, long experience,
                                     int previousLevel, int level) {
        public ExperienceMutation {
            Objects.requireNonNull(section, "section");
        }
    }

    public record RecipeMutation(ProfessionSection section, boolean changed,
                                 String recipeId) {
        public RecipeMutation {
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(recipeId, "recipeId");
        }
    }
}
