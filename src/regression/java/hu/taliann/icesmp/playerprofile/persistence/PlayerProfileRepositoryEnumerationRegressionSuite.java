package hu.taliann.icesmp.playerprofile.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Durable owner enumeration must ignore junk and never initialize profiles. */
public final class PlayerProfileRepositoryEnumerationRegressionSuite {
    private static int assertions;

    private PlayerProfileRepositoryEnumerationRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-enumeration-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        try {
            final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000010");
            final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
            final UUID manifestless = UUID.fromString("00000000-0000-0000-0000-000000000099");
            repository.loadSnapshot(first).toCompletableFuture().join();
            repository.loadSnapshot(second).toCompletableFuture().join();
            Files.createDirectories(root.resolve(manifestless.toString()));
            Files.createDirectories(root.resolve("not-a-uuid"));
            Files.writeString(root.resolve("ordinary-file"), "ignored");

            final Set<UUID> ids = repository.listPlayerIds().toCompletableFuture().join();
            check(ids.equals(Set.of(first, second)), "only durable manifests enumerated");
            check(List.copyOf(ids).equals(List.of(second, first)), "deterministic UUID order");
            expect(UnsupportedOperationException.class, () -> ids.add(manifestless));
            check(!Files.exists(root.resolve(manifestless.toString()).resolve("manifest.yml")),
                    "enumeration does not initialize manifestless UUID directory");
            check(repository.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile repository enumeration regression suite passed. assertions="
                + assertions);
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

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
