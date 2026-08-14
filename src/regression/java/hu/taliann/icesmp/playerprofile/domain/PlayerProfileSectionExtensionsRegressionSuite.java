package hu.taliann.icesmp.playerprofile.domain;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Regression coverage for immutable extension mutations and migrated authority suites. */
public final class PlayerProfileSectionExtensionsRegressionSuite {

    private static int assertions;

    private PlayerProfileSectionExtensionsRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final PlayerProfileSnapshot profile = PlayerProfileSnapshot.greenfield(
                UUID.fromString("00000000-0000-0000-0000-000000001086"),
                Instant.parse("2026-08-05T12:00:00Z"));
        for (final ProfileSectionSnapshot<?> snapshot : profile.sectionMap().values()) {
            final ProfileSectionData section = snapshot.value();
            if (section.getClass().isRecord()) verifyRecordSection(section);
            else verifyNonRecordSectionFailsClosed(section);
        }
        invalidInputsFailClosed(profile.preferences().value());
        System.out.println("PlayerProfile section extension regression suite passed. assertions="
                + assertions);

        hu.taliann.icesmp.playerprofile.application.PlayerProfileIntroStoreRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepositoryEnumerationRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileStatisticsStoreRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStoreRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStoreRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyQuestStoreRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStoreRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionAuthorityRegressionSuite.main(args);
        hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStoreRegressionSuite.main(args);
    }

    private static void verifyRecordSection(final ProfileSectionData original) throws Exception {
        final Map<String, Object> callerOwned = new LinkedHashMap<>();
        callerOwned.put("authority.test", Map.of("nested", 7L));
        final ProfileSectionData changed = PlayerProfileSectionExtensions.copyWithExtensions(
                original, callerOwned);
        callerOwned.put("authority.after-copy", true);

        check(changed != original, "copy identity " + original.sectionId());
        check(changed.getClass() == original.getClass(), "copy type " + original.sectionId());
        check(changed.sectionId() == original.sectionId(), "copy section id " + original.sectionId());
        check(changed.extensions().containsKey("authority.test"), "new extension " + original.sectionId());
        check(!changed.extensions().containsKey("authority.after-copy"),
                "caller map copied " + original.sectionId());
        check(!original.extensions().containsKey("authority.test"),
                "original unchanged " + original.sectionId());

        for (final RecordComponent component : original.getClass().getRecordComponents()) {
            if (component.getName().equals("extensions")) continue;
            final Object before = component.getAccessor().invoke(original);
            final Object after = component.getAccessor().invoke(changed);
            check(java.util.Objects.equals(before, after),
                    "non-extension component changed: " + original.sectionId() + '.'
                            + component.getName());
        }

        expect(UnsupportedOperationException.class,
                () -> changed.extensions().put("illegal", true));
        final ProfileSectionData removed = PlayerProfileSectionExtensions.put(changed,
                "authority.test", null);
        check(!removed.extensions().containsKey("authority.test"),
                "extension removed " + original.sectionId());
    }

    private static void verifyNonRecordSectionFailsClosed(final ProfileSectionData section) {
        check(!section.getClass().isRecord(), "expected non-record section " + section.sectionId());
        check(section.extensions().isEmpty(), "non-record section must not expose extension authority");
        expect(IllegalArgumentException.class,
                () -> PlayerProfileSectionExtensions.copyWithExtensions(
                        section, Map.of("authority.test", true)));
        expect(IllegalArgumentException.class,
                () -> PlayerProfileSectionExtensions.put(section, "authority.test", true));
    }

    private static void invalidInputsFailClosed(final ProfileSectionData section) {
        expect(NullPointerException.class,
                () -> PlayerProfileSectionExtensions.copyWithExtensions(null, Map.of()));
        expect(NullPointerException.class,
                () -> PlayerProfileSectionExtensions.mutate(section, null));
        expect(NullPointerException.class,
                () -> PlayerProfileSectionExtensions.mutate(section, ignored -> null));
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
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

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
