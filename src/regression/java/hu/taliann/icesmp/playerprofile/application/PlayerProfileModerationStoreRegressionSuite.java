package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ModerationSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/** Moderation reference/summary CAS idempotency and restart durability regressions. */
public final class PlayerProfileModerationStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileModerationStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-moderation-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000003077");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileModerationStore store = new PlayerProfileModerationStore();

            final ModerationSection greenfield = store.read(player);
            check(greenfield.activePunishmentRefs().isEmpty() && greenfield.strikeCount() == 0,
                    "greenfield summary is empty");

            final String muteRef = "11111111-1111-1111-1111-111111111111";
            final String banRef = "22222222-2222-2222-2222-222222222222";
            check(store.syncSummary(player, Set.of(muteRef, banRef), 2)
                    .toCompletableFuture().join(), "summary publish commits");
            final long revision = repository.cached(player).orElseThrow()
                    .section(ProfileSectionId.MODERATION).orElseThrow().revision();
            check(!store.syncSummary(player, Set.of(banRef, muteRef), 2)
                    .toCompletableFuture().join(), "identical summary is a no-op");
            check(repository.cached(player).orElseThrow()
                    .section(ProfileSectionId.MODERATION).orElseThrow().revision() == revision,
                    "no-op summary keeps the revision");

            check(store.syncSummary(player, Set.of(banRef), 3)
                    .toCompletableFuture().join(), "revoked reference removed, strike added");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final ModerationSection durable = store.read(player);
            check(durable.activePunishmentRefs().equals(Set.of(banRef)),
                    "active reference restart durable");
            check(durable.strikeCount() == 3, "strike count restart durable");

            check(store.syncSummary(player, Set.of(), 3).toCompletableFuture().join(),
                    "full lift clears references");
            check(store.read(player).activePunishmentRefs().isEmpty(),
                    "lifted summary reads empty");

            expect(IllegalArgumentException.class,
                    () -> store.syncSummary(player, Set.of(" "), 1));
            expect(IllegalArgumentException.class,
                    () -> store.syncSummary(player, Set.of("x"), -1));
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
        System.out.println("PlayerProfile moderation summary regression suite passed. assertions=" + assertions);
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
