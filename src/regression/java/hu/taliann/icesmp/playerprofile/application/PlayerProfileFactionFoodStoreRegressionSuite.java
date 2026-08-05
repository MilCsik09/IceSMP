package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Faction-food timestamp binding and restart durability regressions. */
public final class PlayerProfileFactionFoodStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileFactionFoodStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-food-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001088");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileFactionFoodStore store = new PlayerProfileFactionFoodStore();
            check(store.read(player).lastHomeFoodAt() == -1L, "greenfield timestamp missing");
            final var first = store.record(player, "blue", 1000L).toCompletableFuture().join();
            check(first.lastHomeFoodAt() == 1000L && "BLUE".equals(first.faction()),
                    "timestamp and faction normalized");
            final long revision = repository.cached(player).orElseThrow().faction().revision();
            store.record(player, "BLUE", 1000L).toCompletableFuture().join();
            check(repository.cached(player).orElseThrow().faction().revision() == revision,
                    "identical food state is no-op");
            store.record(player, "RED", 2000L).toCompletableFuture().join();
            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final var durable = store.read(player);
            check(durable.lastHomeFoodAt() == 2000L, "timestamp restart durable");
            check("RED".equals(durable.faction()), "faction restart durable");
            expect(IllegalArgumentException.class, () -> store.record(player, "INVALID", 3L));
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
        System.out.println("PlayerProfile faction food regression suite passed. assertions=" + assertions);
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
