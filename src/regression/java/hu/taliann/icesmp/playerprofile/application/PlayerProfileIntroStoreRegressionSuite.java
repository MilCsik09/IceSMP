package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Restart-durable intro seen/cinematic recovery authority regressions. */
public final class PlayerProfileIntroStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileIntroStoreRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-intro-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final PlayerProfileIntroStore store = new PlayerProfileIntroStore();
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001086");
            check(!store.hasSeen(player).toCompletableFuture().join(), "greenfield intro unseen");
            check(store.markSeen(player).toCompletableFuture().join(), "first seen commit");
            check(!store.markSeen(player).toCompletableFuture().join(), "seen commit idempotent");
            check(store.hasSeen(player).toCompletableFuture().join(), "seen survives reload");

            check(store.beginCinematic(player, "CREATIVE").toCompletableFuture().join(),
                    "cinematic begin committed");
            check(!store.beginCinematic(player, "SURVIVAL").toCompletableFuture().join(),
                    "duplicate cinematic begin rejected");
            final PlayerProfileIntroStore.CinematicState active =
                    store.cinematicState(player).toCompletableFuture().join();
            check(active.active(), "cinematic active");
            check("CREATIVE".equals(active.previousGamemode()), "previous gamemode retained");

            repository.invalidate(player);
            final PlayerProfileIntroStore.CinematicState recovered =
                    store.cinematicState(player).toCompletableFuture().join();
            check(recovered.active(), "cinematic marker restart durable");
            check(store.completeCinematic(player).toCompletableFuture().join(),
                    "cinematic completion committed");
            check(!store.completeCinematic(player).toCompletableFuture().join(),
                    "cinematic completion idempotent");
            check(!store.cinematicState(player).toCompletableFuture().join().active(),
                    "cinematic marker cleared");

            final var shutdown = service.shutdown(Duration.ofSeconds(5))
                    .toCompletableFuture().join();
            check(shutdown.drained(), "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile intro onboarding regression suite passed. assertions="
                + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
