#!/usr/bin/env python3
"""Add storage-independent, fail-closed durable profile enumeration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_interface() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/persistence/PlayerProfileRepository.java"
    replace_once(path,
        '    /** Storage-independent case-insensitive identity lookup. */\n'
        '    CompletionStage<Optional<PlayerProfileSnapshot>> findByName(String playerName);\n',
        '    /** Storage-independent case-insensitive identity lookup. */\n'
        '    CompletionStage<Optional<PlayerProfileSnapshot>> findByName(String playerName);\n'
        '    /** Enumerates durable profile owners without initializing missing profiles. */\n'
        '    default CompletionStage<Set<UUID>> listPlayerIds(){\n'
        '        return java.util.concurrent.CompletableFuture.failedFuture(\n'
        '                new UnsupportedOperationException("profile enumeration unsupported"));\n'
        '    }\n',
        'repository enumeration contract')


def patch_yaml_repository() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/persistence/YamlPlayerProfileRepository.java"
    anchor = '''    @Override public CompletionStage<Optional<ProfileSectionSnapshot<?>>> loadSection(UUID id,ProfileSectionId section){Objects.requireNonNull(section);return find(id).thenApply(p->p.flatMap(profile->profile.section(section)));}
'''
    method = '''    @Override public CompletionStage<Set<UUID>> listPlayerIds(){
        return submit(new UUID(0L,1L),()->{
            if(!Files.isDirectory(root))return Set.of();
            TreeSet<UUID> ids=new TreeSet<>();
            try(DirectoryStream<Path> dirs=Files.newDirectoryStream(root)){
                for(Path dir:dirs){
                    if(!Files.isDirectory(dir)||!Files.isRegularFile(dir.resolve("manifest.yml")))continue;
                    try{ids.add(UUID.fromString(dir.getFileName().toString()));}
                    catch(IllegalArgumentException ignored){}
                }
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
        });
    }
'''
    if method not in path.read_text(encoding="utf-8"):
        replace_once(path, anchor, method + anchor, 'YAML enumeration method')


def write_regression() -> None:
    path = ROOT / "src/regression/java/hu/taliann/icesmp/playerprofile/persistence/PlayerProfileRepositoryEnumerationRegressionSuite.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('''package hu.taliann.icesmp.playerprofile.persistence;

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
''', encoding="utf-8")


def patch_gradle() -> None:
    path = ROOT / "build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    task = '''val playerProfileRepositoryEnumerationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs durable PlayerProfile owner-enumeration regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepositoryEnumerationRegressionSuite")
}

'''
    anchor = 'val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {'
    if task not in text:
        if text.count(anchor) != 1:
            raise RuntimeError(f"enumeration task anchor count={text.count(anchor)}")
        text = text.replace(anchor, task + anchor, 1)
    old = '''        professionProfileStateRegressionTest, playerProfileIntroRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest,
'''
    new = '''        professionProfileStateRegressionTest, playerProfileIntroRegressionTest,
        playerProfileRepositoryEnumerationRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest,
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError(f"enumeration dependency anchor count={text.count(old)}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    patch_interface()
    patch_yaml_repository()
    write_regression()
    patch_gradle()
    print("PlayerProfile repository enumeration wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
