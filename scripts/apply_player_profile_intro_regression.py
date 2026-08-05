#!/usr/bin/env python3
"""Add targeted PlayerProfile intro/onboarding persistence regressions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write_suite() -> None:
    path = ROOT / "src/regression/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileIntroStoreRegressionSuite.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

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
''', encoding="utf-8")


def patch_gradle() -> None:
    path = ROOT / "build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    task = '''val playerProfileIntroRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs PlayerProfile intro seen and cinematic recovery regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.application.PlayerProfileIntroStoreRegressionSuite")
}

'''
    anchor = 'val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {'
    if task not in text:
        if text.count(anchor) != 1:
            raise RuntimeError(f"intro Gradle task anchor count={text.count(anchor)}")
        text = text.replace(anchor, task + anchor, 1)
    old = '''        professionProfileStateRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
    new = '''        professionProfileStateRegressionTest, playerProfileIntroRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest,
        playerProfileApiRegressionTest,
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError(f"intro check dependency anchor count={text.count(old)}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_suite()
    patch_gradle()
    print("PlayerProfile intro regression suite registered.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
