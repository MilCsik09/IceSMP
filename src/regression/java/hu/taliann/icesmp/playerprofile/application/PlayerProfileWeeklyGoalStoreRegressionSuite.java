package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Weekly guild-goal contribution, award idempotency and atomic claim regressions. */
public final class PlayerProfileWeeklyGoalStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileWeeklyGoalStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-weekly-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000002099");
            final UUID bystander = UUID.fromString("00000000-0000-0000-0000-000000002100");
            repository.loadSnapshot(player).toCompletableFuture().join();
            repository.loadSnapshot(bystander).toCompletableFuture().join();
            final PlayerProfileWeeklyGoalStore store = new PlayerProfileWeeklyGoalStore();
            final ProfessionType profession = ProfessionType.values()[0];
            final String professionId = profession.getId();

            check(store.claim(player, 100, 15, 50).toCompletableFuture().join().isEmpty(),
                    "greenfield claim is empty");

            check(store.recordContribution(player, profession, 40L, 7L)
                    .toCompletableFuture().join() == 40L, "first contribution recorded");
            check(store.recordContribution(player, profession, 60L, 7L)
                    .toCompletableFuture().join() == 100L, "same-week contribution accumulates");
            check(store.recordContribution(player, profession, 5L, 8L)
                    .toCompletableFuture().join() == 5L, "week rollover resets progress");
            check(store.recordContribution(player, profession, 95L, 8L)
                    .toCompletableFuture().join() == 100L, "post-rollover accumulation");

            final Map<String, Long> awarded = store.award(player, 8L,
                    Map.of(professionId, 300), 100L).toCompletableFuture().join();
            check(awarded.equals(Map.of(professionId, 300L)), "eligible contribution awarded");
            final long revisionAfterAward = repository.cached(player).orElseThrow()
                    .section(ProfileSectionId.PROFESSIONS).orElseThrow().revision();
            check(store.award(player, 8L, Map.of(professionId, 300), 100L)
                    .toCompletableFuture().join().isEmpty(), "repeated award is rejected");
            check(repository.cached(player).orElseThrow()
                    .section(ProfileSectionId.PROFESSIONS).orElseThrow().revision()
                    == revisionAfterAward, "repeated award is a no-op commit");

            final long bystanderRevision = repository.cached(bystander).orElseThrow()
                    .section(ProfileSectionId.PROFESSIONS).orElseThrow().revision();
            check(store.award(bystander, 8L, Map.of(professionId, 300), 100L)
                    .toCompletableFuture().join().isEmpty(), "non-contributor gets no award");
            check(repository.cached(bystander).orElseThrow()
                    .section(ProfileSectionId.PROFESSIONS).orElseThrow().revision()
                    == bystanderRevision, "non-contributor award is a no-op commit");

            check(store.recordContribution(player, profession, 99L, 9L)
                    .toCompletableFuture().join() == 99L, "next week starts clean");
            check(store.award(player, 9L, Map.of(professionId, 300), 100L)
                    .toCompletableFuture().join().isEmpty(), "below-threshold contribution skipped");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final ProfessionSection durable = (ProfessionSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.PROFESSIONS).orElseThrow().value();
            check(PlayerProfileWeeklyGoalStore.pendingOf(durable)
                    .equals(Map.of(professionId, 300L)), "pending reward restart durable");

            final List<PlayerProfileWeeklyGoalStore.ClaimedReward> claimed =
                    store.claim(player, 100, 15, 50).toCompletableFuture().join();
            check(claimed.size() == 1 && claimed.get(0).xp() == 300L
                    && professionId.equals(claimed.get(0).professionId()), "pending claimed once");
            check(claimed.get(0).level() > claimed.get(0).previousLevel(), "claimed XP levels up");
            check(store.claim(player, 100, 15, 50).toCompletableFuture().join().isEmpty(),
                    "second claim is empty");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final ProfessionSection afterClaim = (ProfessionSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.PROFESSIONS).orElseThrow().value();
            check(PlayerProfileWeeklyGoalStore.pendingOf(afterClaim).isEmpty(),
                    "claim clears pending durably");
            check(afterClaim.experience().getOrDefault(professionId, 0L) == 300L,
                    "claimed XP is durable in the same section");

            expect(IllegalArgumentException.class,
                    () -> store.recordContribution(player, profession, 0L, 9L));
            expect(IllegalArgumentException.class,
                    () -> store.award(player, 9L, Map.of(professionId, 300), 0L));
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile weekly goal regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(final Class<? extends Throwable> expected, final Throwing action) {
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
