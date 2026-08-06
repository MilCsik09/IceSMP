package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.LifecycleSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Death escrow deposit accumulation, exact-once claim and restart durability regressions. */
public final class PlayerProfileDeathEscrowStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileDeathEscrowStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-escrow-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000005044");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileDeathEscrowStore store = new PlayerProfileDeathEscrowStore();

            check(store.claim(player).toCompletableFuture().join().isEmpty(),
                    "greenfield escrow is empty");
            check(store.deposit(player, List.of("cGF5bG9hZC1B"), 1_000L)
                    .toCompletableFuture().join() == 1, "first deposit stored");
            check(store.deposit(player, List.of("cGF5bG9hZC1C"), 2_000L)
                    .toCompletableFuture().join() == 2, "second deposit accumulates");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final LifecycleSection durable = (LifecycleSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.LIFECYCLE).orElseThrow().value();
            check(PlayerProfileDeathEscrowStore.readItems(durable)
                    .equals(List.of("cGF5bG9hZC1B", "cGF5bG9hZC1C")),
                    "escrow payloads restart durable and ordered");

            final List<String> claimed = store.claim(player).toCompletableFuture().join();
            check(claimed.equals(List.of("cGF5bG9hZC1B", "cGF5bG9hZC1C")), "claim returns all payloads");
            check(store.claim(player).toCompletableFuture().join().isEmpty(),
                    "second claim is empty (exact-once)");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final LifecycleSection cleared = (LifecycleSection) repository.cached(player)
                    .orElseThrow().section(ProfileSectionId.LIFECYCLE).orElseThrow().value();
            check(PlayerProfileDeathEscrowStore.readItems(cleared).isEmpty(),
                    "claim clears the escrow durably");

            final List<String> flood = new ArrayList<>();
            for (int index = 0; index < 33; index++) {
                flood.add("cGF5bG9hZA" + index);
            }
            expect(IllegalStateException.class,
                    () -> store.deposit(player, flood, 3_000L).toCompletableFuture().join());
            expect(IllegalArgumentException.class,
                    () -> store.deposit(player, List.of(), 3_000L));
            expect(IllegalArgumentException.class,
                    () -> store.deposit(player, List.of(" "), 3_000L));
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
        System.out.println("PlayerProfile death escrow regression suite passed. assertions=" + assertions);
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
                if (failure instanceof RuntimeException runtime && runtime.getCause() != null
                        && expected.isInstance(runtime.getCause())) {
                    return;
                }
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
