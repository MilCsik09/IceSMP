package hu.taliann.icesmp.playerprofile.domain;

import hu.taliann.icesmp.playerprofile.domain.section.*;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.*;

public final class PlayerProfileDomainRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static int assertions;

    private PlayerProfileDomainRegressionSuite() { }

    public static void main(String[] args) {
        greenfieldContainsEverySection();
        replacementAndRevisionRules();
        healthIsolation();
        extensionsAndCollectionsAreImmutable();
        boundsAndStableIds();
        domainContainsNoBukkitObjects();
        System.out.println("PlayerProfile domain regression suite passed. assertions=" + assertions);
    }

    private static void greenfieldContainsEverySection() {
        PlayerProfileSnapshot p = PlayerProfileSnapshot.greenfield(PLAYER, NOW);
        check(p.playerId().equals(PLAYER), "owner");
        check(p.profileRevision() == 0, "greenfield revision");
        check(p.createdAt().equals(NOW) && p.updatedAt().equals(NOW), "timestamps");
        check(p.sectionMap().size() == ProfileSectionId.values().length, "all sections");
        check(p.sectionRevisions().size() == ProfileSectionId.values().length, "all revisions");
        for (ProfileSectionId id : ProfileSectionId.values()) {
            ProfileSectionSnapshot<?> section = p.section(id).orElseThrow();
            check(section.sectionId() == id, "section id " + id.id());
            check(section.value().sectionId() == id, "value id " + id.id());
            check(section.schema() == id.currentSchema(), "schema " + id.id());
            check(section.revision() == 0, "revision " + id.id());
            check(section.health().usable(), "health " + id.id());
            check(p.sectionRevisions().get(id).revision() == 0, "revision map " + id.id());
        }
        check(p.health().status() == ProfileHealth.Status.HEALTHY, "greenfield healthy");
        expect(UnsupportedOperationException.class,
                () -> p.sectionMap().put(ProfileSectionId.IDENTITY, p.identity()));
        expect(UnsupportedOperationException.class,
                () -> p.sectionRevisions().put(ProfileSectionId.IDENTITY, new SectionRevision(1, 1)));
    }

    private static void replacementAndRevisionRules() {
        PlayerProfileSnapshot p = PlayerProfileSnapshot.greenfield(PLAYER, NOW);
        PreferenceSection current = p.preferences().value();
        PreferenceSection nextValue = new PreferenceSection(
                "en_US", current.hudEnabled(), current.scoreboardEnabled(),
                current.notificationsEnabled(), current.publicProfile(),
                current.publicCompanion(), current.publicAchievements(),
                current.publicClassFactionSpec(), true, Map.of("theme", "compact"),
                Map.of("future", Map.of("enabled", true)));
        ProfileSectionSnapshot<PreferenceSection> next = new ProfileSectionSnapshot<>(
                ProfileSectionId.PREFERENCES, p.preferences().schema(), 1,
                NOW.plusSeconds(1), nextValue, SectionHealth.healthy(),
                Map.of("future-root", List.of("a", "b")));
        PlayerProfileSnapshot changed = p.withSection(next, 1, NOW.plusSeconds(1));
        check(changed.profileRevision() == 1, "profile revision advanced");
        check(changed.preferences().revision() == 1, "section revision advanced");
        check(changed.preferences().value().language().equals("en_US"), "value replaced");
        check(changed.identity().equals(p.identity()), "unrelated section stable");
        expect(IllegalArgumentException.class,
                () -> p.withSection(next, 2, NOW.plusSeconds(1)));
        expect(IllegalArgumentException.class, () -> new ProfileSectionSnapshot<>(
                ProfileSectionId.PREFERENCES, 1, 0, NOW,
                IdentitySection.empty(NOW.toEpochMilli()), SectionHealth.healthy()));
        expect(IllegalArgumentException.class, () -> new PlayerProfileSnapshot(
                PLAYER, 0, NOW.plusSeconds(1), NOW, p.sectionRevisions(),
                p.identity(), p.lifecycle(), p.onboarding(), p.faction(), p.economy(),
                p.classSpec(), p.professions(), p.spellbook(), p.talents(), p.quests(),
                p.companions(), p.relics(), p.achievements(), p.statistics(), p.preferences(),
                p.socialLinks(), p.moderation(), p.operations(), p.health()));
    }

    private static void healthIsolation() {
        PlayerProfileSnapshot p = PlayerProfileSnapshot.greenfield(PLAYER, NOW);
        EnumMap<ProfileSectionId, ProfileSectionSnapshot<?>> sections =
                new EnumMap<>(p.sectionMap());
        ProfileSectionSnapshot<?> stats = p.statistics();
        sections.put(ProfileSectionId.STATISTICS, new ProfileSectionSnapshot<>(
                ProfileSectionId.STATISTICS, stats.schema(), stats.revision(), stats.updatedAt(),
                stats.value(), SectionHealth.quarantined("bad stats", "evidence-stats"),
                stats.extensions()));
        PlayerProfileSnapshot partial = PlayerProfileSnapshot.fromMap(
                PLAYER, 0, NOW, NOW, sections);
        check(partial.health().status() == ProfileHealth.Status.PARTIAL, "partial health");
        check(partial.health().usable(), "partial profile usable");
        check(partial.classSpec().health().usable(), "class spec remains usable");
        check(!partial.statistics().health().usable(), "statistics blocked");

        ProfileSectionSnapshot<?> identity = p.identity();
        sections.put(ProfileSectionId.IDENTITY, new ProfileSectionSnapshot<>(
                ProfileSectionId.IDENTITY, identity.schema(), identity.revision(), identity.updatedAt(),
                identity.value(), SectionHealth.quarantined("bad identity", "evidence-identity"),
                identity.extensions()));
        PlayerProfileSnapshot blocked = PlayerProfileSnapshot.fromMap(
                PLAYER, 0, NOW, NOW, sections);
        check(blocked.health().status() == ProfileHealth.Status.BLOCKED, "identity blocks profile");
        check(!blocked.health().usable(), "blocked profile unusable");
        expect(IllegalArgumentException.class,
                () -> SectionHealth.quarantined("missing evidence", ""));
    }

    @SuppressWarnings("unchecked")
    private static void extensionsAndCollectionsAreImmutable() {
        List<String> nested = new ArrayList<>(List.of("first"));
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("future-list", nested);
        ProfileSectionSnapshot<PreferenceSection> section = new ProfileSectionSnapshot<>(
                ProfileSectionId.PREFERENCES, 1, 0, NOW, PreferenceSection.empty(0),
                SectionHealth.healthy(), extensions);
        nested.add("second");
        List<Object> stored = (List<Object>) section.extensions().get("future-list");
        check(stored.size() == 1, "nested extension copied");
        expect(UnsupportedOperationException.class, () -> stored.add("third"));
        expect(UnsupportedOperationException.class,
                () -> section.extensions().put("other", true));

        EconomySection economy = new EconomySection(
                new LinkedHashMap<>(Map.of("coins", 5L)), 10,
                Map.of("dark", 2L), Map.of(), Set.of(), Map.of());
        expect(UnsupportedOperationException.class,
                () -> economy.wallets().put("other", 1L));
        check(economy.wallets().get("coins") == 5L, "wallet copied");
    }

    private static void boundsAndStableIds() {
        check(ProfileSectionId.parse("CLASS_SPEC") == ProfileSectionId.CLASS_SPEC,
                "normalized section id");
        check(ProfileSectionId.SOCIAL_LINKS.fileName().equals("social-links.yml"),
                "stable filename");
        expect(IllegalArgumentException.class, () -> ProfileSectionId.parse("unknown"));
        expect(IllegalArgumentException.class,
                () -> new EconomySection(Map.of("coins", -1L), 0, Map.of(), Map.of(), Set.of(), Map.of()));
        expect(IllegalArgumentException.class,
                () -> new ModerationSection(Set.of(), -1, Set.of(), Map.of(), Map.of()));
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 129; i++) tooMany.put("key-" + i, "value");
        expect(IllegalArgumentException.class, () -> new PreferenceSection(
                "hu_HU", true, true, true, true, true, true, true, false,
                tooMany, Map.of()));
        expect(IllegalArgumentException.class, () -> new PlayerProfileOperation(
                "", "type", PlayerProfileOperation.Status.COMMITTED, "fingerprint",
                NOW, NOW, Map.of()));
    }

    private static void domainContainsNoBukkitObjects() {
        Class<?>[] records = {
                PlayerProfileSnapshot.class, ProfileSectionSnapshot.class,
                IdentitySection.class, LifecycleSection.class, OnboardingSection.class,
                FactionSection.class, EconomySection.class,
                ProfessionSection.class, SpellbookSection.class, TalentSection.class,
                QuestSection.class, CompanionSection.class, RelicSection.class,
                AchievementSection.class, StatisticsSection.class, PreferenceSection.class,
                SocialLinkSection.class, ModerationSection.class, OperationSection.class
        };
        check(java.lang.reflect.Modifier.isFinal(
                ClassSpecSection.class.getModifiers()), "final ClassSpecSection");
        for (java.lang.reflect.Field field : ClassSpecSection.class.getDeclaredFields()) {
            check(!field.getType().getName().startsWith("org.bukkit."),
                    "no Bukkit field ClassSpecSection." + field.getName());
        }
        for (Class<?> type : records) {
            check(type.isRecord(), "record " + type.getSimpleName());
            for (RecordComponent component : type.getRecordComponents()) {
                check(!component.getType().getName().startsWith("org.bukkit."),
                        "no Bukkit component " + type.getSimpleName() + '.' + component.getName());
            }
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> expected, Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
