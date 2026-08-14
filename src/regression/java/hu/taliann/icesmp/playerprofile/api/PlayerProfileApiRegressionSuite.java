package hu.taliann.icesmp.playerprofile.api;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionData;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionSnapshot;
import hu.taliann.icesmp.playerprofile.domain.SectionHealth;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.playerprofile.domain.section.EconomySection;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
import hu.taliann.icesmp.playerprofile.domain.section.IdentitySection;
import hu.taliann.icesmp.playerprofile.domain.section.ModerationSection;
import hu.taliann.icesmp.playerprofile.domain.section.PreferenceSection;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;
import hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Public/self/admin redaction and storage-independent query regressions. */
public final class PlayerProfileApiRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final Instant NOW = Instant.parse("2026-08-03T18:00:00Z");
    private static int assertions;

    private PlayerProfileApiRegressionSuite() {
    }

    public static void main(final String[] args) {
        visiblePublicProfileExposesOnlyOptedInData();
        hiddenPublicProfileRedactsPlayerOwnedState();
        selfProfileIncludesPrivateGameplayState();
        adminProfileIncludesHealthAndModerationEvidence();
        repositoryQueriesRemainReadOnlyAndStorageIndependent();
        missingProfileFailsRequiredDtoQueries();
        dtoCollectionsAreImmutable();
        System.out.println("PlayerProfile API regression suite passed. assertions=" + assertions);
    }

    private static void visiblePublicProfileExposesOnlyOptedInData() {
        final PlayerProfileQueryService query = new PlayerProfileQueryService(new FakeRepository(enriched(true)));
        final PublicPlayerProfileDto dto = query.publicDto(PLAYER).toCompletableFuture().join();

        check(dto.playerId().equals(PLAYER), "public player id");
        check(dto.visible(), "public visibility");
        check(dto.name().equals("PlayerOne"), "public name");
        check(dto.faction().equals("red"), "public faction");
        check(dto.primaryClass().equals("wizard"), "public class");
        check(dto.activeSpecialization().equals("necromancer"), "public specialization");
        check(dto.profileRevision() == 7L, "public aggregate revision");
        check(dto.sectionRevisions().get("economy") == 1L, "public revision metadata");
        check(!PublicPlayerProfileDto.class.getRecordComponents()[0].getName().isBlank(), "public dto is a record");
        check(recordComponent(PublicPlayerProfileDto.class, "wallets").isEmpty(), "wallets absent from public dto");
        check(recordComponent(PublicPlayerProfileDto.class, "adminNoteRefs").isEmpty(), "admin notes absent from public dto");
    }

    private static void hiddenPublicProfileRedactsPlayerOwnedState() {
        final PlayerProfileQueryService query = new PlayerProfileQueryService(new FakeRepository(enriched(false)));
        final PublicPlayerProfileDto dto = query.publicDto(PLAYER).toCompletableFuture().join();

        check(!dto.visible(), "hidden visibility");
        check(dto.name().isEmpty(), "hidden name");
        check(dto.faction().isEmpty(), "hidden faction");
        check(dto.primaryClass().isEmpty(), "hidden class");
        check(dto.activeSpecialization().isEmpty(), "hidden specialization");
        check(dto.achievements().isEmpty(), "hidden achievements");
        check(dto.publicStatistics().isEmpty(), "hidden statistics");
        check(dto.publicCompanion().isEmpty(), "hidden companion");
        check(dto.profileRevision() == 7L, "redaction retains concurrency metadata");
        check(dto.sectionRevisions().size() == ProfileSectionId.values().length, "redaction retains section revisions");
    }

    private static void selfProfileIncludesPrivateGameplayState() {
        final SelfPlayerProfileDto dto = new PlayerProfileQueryService(new FakeRepository(enriched(false)))
            .selfDto(PLAYER).toCompletableFuture().join();

        check(!dto.publicProfile().visible(), "self embeds redacted public projection");
        check(dto.wallets().get("coins") == 125L, "self wallet");
        check(dto.bankBalance() == 700L, "self bank");
        check(dto.activeQuests().get("daily-hunt").get("kills") == 4L, "self active quest");
        check(dto.completedQuests().contains("onboarding"), "self completed quest");
        check(dto.claimableRewards().contains("reward-daily-hunt"), "self claimable reward");
        check(dto.preferences().get("language").equals("hu_HU"), "self language");
        check(dto.preferences().get("hud-enabled").equals("true"), "self preference projection");
        check(recordComponent(SelfPlayerProfileDto.class, "activePunishmentRefs").isEmpty(), "moderation absent from self dto");
    }

    private static void adminProfileIncludesHealthAndModerationEvidence() {
        final AdminPlayerProfileDto dto = new PlayerProfileQueryService(new FakeRepository(enriched(true)))
            .adminDto(PLAYER).toCompletableFuture().join();

        check(dto.profileHealth().equals("PARTIAL"), "aggregate health");
        check(dto.sectionHealth().get("moderation").equals("QUARANTINED"), "section health");
        check(dto.quarantineEvidence().get("moderation").equals("evidence-42"), "quarantine evidence");
        check(dto.activePunishmentRefs().contains("ban-1"), "active punishment");
        check(dto.strikeCount() == 2, "strike count");
        check(dto.adminNoteRefs().contains("note-7"), "admin note");
        check(dto.moderationAuditState().get("review").equals("required"), "moderation audit state");
        check(dto.selfProfile().wallets().get("coins") == 125L, "admin includes self projection");
    }

    private static void repositoryQueriesRemainReadOnlyAndStorageIndependent() {
        final PlayerProfileSnapshot profile = enriched(true);
        final FakeRepository repository = new FakeRepository(profile);
        final PlayerProfileQueryService query = new PlayerProfileQueryService(repository);

        check(query.find(PLAYER).toCompletableFuture().join().orElseThrow().equals(profile), "find delegates");
        check(query.findByName("PLAYERONE").toCompletableFuture().join().orElseThrow().equals(profile), "name lookup delegates");
        check(query.findByName(" ").toCompletableFuture().join().isEmpty(), "blank name is empty");
        check(repository.nameLookups == 1, "blank name avoids repository access");
        final var section = query.section(PLAYER, ProfileSectionId.ECONOMY).toCompletableFuture().join().orElseThrow();
        check(section.sectionId() == ProfileSectionId.ECONOMY, "section lookup delegates");
        check(repository.findCalls == 1, "single direct find call");
        check(repository.sectionCalls == 1, "single section call");
        check(repository.writes == 0, "query service performs no writes");
    }

    private static void missingProfileFailsRequiredDtoQueries() {
        final PlayerProfileQueryService query = new PlayerProfileQueryService(new FakeRepository(null));
        final CompletionException failure = expect(
            CompletionException.class,
            () -> query.publicDto(PLAYER).toCompletableFuture().join()
        );
        check(failure.getCause() instanceof NoSuchElementException, "missing profile cause");
        check(query.find(PLAYER).toCompletableFuture().join().isEmpty(), "missing find is optional");
    }

    private static void dtoCollectionsAreImmutable() {
        final SelfPlayerProfileDto self = new PlayerProfileQueryService(new FakeRepository(enriched(true)))
            .selfDto(PLAYER).toCompletableFuture().join();
        final AdminPlayerProfileDto admin = new PlayerProfileQueryService(new FakeRepository(enriched(true)))
            .adminDto(PLAYER).toCompletableFuture().join();

        expect(UnsupportedOperationException.class, () -> self.wallets().put("coins", 999L));
        expect(UnsupportedOperationException.class, () -> self.activeQuests().get("daily-hunt").put("kills", 999L));
        expect(UnsupportedOperationException.class, () -> self.publicProfile().sectionRevisions().put("economy", 99L));
        expect(UnsupportedOperationException.class, () -> admin.activePunishmentRefs().add("ban-2"));
        expect(UnsupportedOperationException.class, () -> admin.sectionHealth().put("economy", "BROKEN"));
    }

    private static PlayerProfileSnapshot enriched(final boolean publicProfile) {
        PlayerProfileSnapshot profile = PlayerProfileSnapshot.greenfield(PLAYER, NOW);
        profile = replace(profile, new IdentitySection(
            "PlayerOne", NOW.toEpochMilli(), NOW.toEpochMilli(), NOW.toEpochMilli(),
            true, true, Map.of("discord", "private-link"), Map.of()
        ), SectionHealth.healthy());
        profile = replace(profile, new PreferenceSection(
            "hu_HU", true, true, true,
            publicProfile, true, true, true, true,
            Map.of("theme", "ice"), Map.of()
        ), SectionHealth.healthy());
        profile = replace(profile, new FactionSection(
            "red", "red", true, NOW.toEpochMilli(), 0L,
            List.of("red"), Map.of("red", 12L), Map.of(), Map.of()
        ), SectionHealth.healthy());
        profile = replace(profile, new EconomySection(
            Map.of("coins", 125L), 700L, Map.of("tax", 5L),
            Map.of("reward-1", "pending"), Set.of("wallet-op-1"), Map.of()
        ), SectionHealth.healthy());
        profile = replace(profile, new QuestSection(
            Map.of("daily-hunt", Map.of("kills", 4L)),
            Set.of("onboarding"), Set.of("receipt-1"), Map.of(), Map.of(),
            Set.of("reward-daily-hunt"), Map.of()
        ), SectionHealth.healthy());
        profile = replace(profile, ClassSpecSection.builder()
            .primaryClassId("wizard")
            .classLevel(20)
            .activeSlot(LoadoutSlot.FIRST)
            .loadout(LoadoutSlot.FIRST, new ClassLoadout(
                "necromancer", LoadoutStatus.ACTIVE, null, Map.of(),
                MasteryProgress.empty(), null, Set.of(), "", CapstoneStatus.LOCKED,
                Map.of(), Map.of(), ""
            ))
            .build(), SectionHealth.healthy());
        profile = replace(profile, new ModerationSection(
            Set.of("ban-1"), 2, Set.of("note-7"),
            Map.of("review", "required"), Map.of()
        ), SectionHealth.quarantined("manual review", "evidence-42"));
        return profile;
    }

    private static <T extends ProfileSectionData> PlayerProfileSnapshot replace(
        final PlayerProfileSnapshot profile,
        final T value,
        final SectionHealth health
    ) {
        final ProfileSectionId sectionId = value.sectionId();
        final long nextSectionRevision = profile.sectionRevisions().get(sectionId).revision() + 1L;
        final Instant updated = NOW.plusSeconds(profile.profileRevision() + 1L);
        return profile.withSection(
            new ProfileSectionSnapshot<>(
                sectionId,
                sectionId.currentSchema(),
                nextSectionRevision,
                updated,
                value,
                health
            ),
            profile.profileRevision() + 1L,
            updated
        );
    }

    private static Optional<java.lang.reflect.RecordComponent> recordComponent(
        final Class<?> type,
        final String name
    ) {
        return List.of(type.getRecordComponents()).stream()
            .filter(component -> component.getName().equals(name))
            .findFirst();
    }

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expect(
        final Class<T> type,
        final ThrowingRunnable operation
    ) {
        assertions++;
        try {
            operation.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (final Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError(
                    "Expected " + type.getSimpleName() + " but got " + failure,
                    failure
                );
            }
            return type.cast(failure);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class FakeRepository implements PlayerProfileRepository {
        private final PlayerProfileSnapshot profile;
        private int findCalls;
        private int nameLookups;
        private int sectionCalls;
        private int writes;

        private FakeRepository(final PlayerProfileSnapshot profile) {
            this.profile = profile;
        }

        @Override
        public CompletionStage<PlayerProfileSnapshot> load(final UUID playerId) {
            return profile == null
                ? CompletableFuture.failedFuture(new NoSuchElementException("missing"))
                : CompletableFuture.completedFuture(profile);
        }

        @Override
        public CompletionStage<Optional<PlayerProfileSnapshot>> find(final UUID playerId) {
            findCalls++;
            return CompletableFuture.completedFuture(
                profile != null && profile.playerId().equals(playerId)
                    ? Optional.of(profile)
                    : Optional.empty()
            );
        }

        @Override
        public CompletionStage<Optional<PlayerProfileSnapshot>> findByName(final String playerName) {
            nameLookups++;
            return CompletableFuture.completedFuture(
                profile != null && profile.identity().value().lastKnownName().equalsIgnoreCase(playerName)
                    ? Optional.of(profile)
                    : Optional.empty()
            );
        }

        @Override
        public CompletionStage<Optional<ProfileSectionSnapshot<?>>> loadSection(
            final UUID playerId,
            final ProfileSectionId section
        ) {
            sectionCalls++;
            return CompletableFuture.completedFuture(
                profile != null && profile.playerId().equals(playerId)
                    ? profile.section(section)
                    : Optional.empty()
            );
        }

        @Override
        public CompletionStage<SectionSaveResult> saveSection(
            final UUID playerId,
            final ProfileSectionId section,
            final long expectedRevision,
            final ProfileSectionSnapshot<?> next
        ) {
            writes++;
            return CompletableFuture.failedFuture(new UnsupportedOperationException("read only"));
        }

        @Override
        public CompletionStage<SectionSaveResult> saveSection(
            final UUID playerId,
            final ProfileSectionId section,
            final long expectedRevision,
            final long expectedGeneration,
            final ProfileSectionSnapshot<?> next
        ) {
            writes++;
            return CompletableFuture.failedFuture(new UnsupportedOperationException("read only"));
        }

        @Override
        public CompletionStage<QuarantineResult> quarantineSection(
            final UUID playerId,
            final ProfileSectionId section,
            final byte[] originalPayload,
            final String reason
        ) {
            writes++;
            return CompletableFuture.failedFuture(new UnsupportedOperationException("read only"));
        }

        @Override
        public CompletionStage<RecoveryResult> recoverSection(
            final UUID playerId,
            final ProfileSectionId section,
            final String evidenceId,
            final String auditId
        ) {
            writes++;
            return CompletableFuture.failedFuture(new UnsupportedOperationException("read only"));
        }

        @Override
        public Optional<PlayerProfileSnapshot> cached(final UUID playerId) {
            return profile != null && profile.playerId().equals(playerId)
                ? Optional.of(profile)
                : Optional.empty();
        }

        @Override
        public void invalidate(final UUID playerId) {
            writes++;
        }

        @Override
        public CompletionStage<Void> flush(final UUID playerId) {
            writes++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> flushAll() {
            writes++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<ShutdownResult> shutdown(final Duration timeout) {
            return CompletableFuture.completedFuture(new ShutdownResult(true, 0, ""));
        }
    }
}
