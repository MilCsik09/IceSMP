package hu.taliann.icesmp.playerprofile.persistence;

import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerProfileYamlRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001101");
    private static final Instant NOW = Instant.parse("2026-08-03T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static int assertions;

    private PlayerProfileYamlRegressionSuite() { }

    public static void main(String[] args) throws Exception {
        greenfieldStructuredRoundTrip();
        sectionCasAndExtensions();
        quarantineIsolationAndRecovery();
        manifestDeclaredMissingSectionFailsClosed();
        invalidUtf8OversizeOwnerAndTempRecovery();
        strictStructuredCodecPolicies();
        concurrentJvmLockIdentityRemainsStable();
        System.out.println("PlayerProfile YAML regression suite passed. assertions=" + assertions);
    }

    private static void greenfieldStructuredRoundTrip() throws Exception {
        Path root = Files.createTempDirectory("pp-yaml-greenfield-");
        YamlPlayerProfileRepository repo = repository(root);
        try {
            UUID missing = UUID.randomUUID();
            check(join(repo.find(missing)).isEmpty(), "find missing remains empty");
            check(!Files.exists(root.resolve(missing.toString())), "read does not initialize");

            PlayerProfileSnapshot first = join(repo.load(PLAYER));
            Path dir = root.resolve(PLAYER.toString());
            check(first.profileRevision() == 0, "greenfield profile revision");
            check(Files.isRegularFile(dir.resolve("manifest.yml")), "manifest written");
            for (ProfileSectionId id : ProfileSectionId.values()) {
                check(Files.isRegularFile(dir.resolve(id.fileName())), "section file " + id.id());
            }
            String manifest = Files.readString(dir.resolve("manifest.yml"));
            String classSpec = Files.readString(dir.resolve("class-spec.yml"));
            check(manifest.contains("format: ICESMP-PLAYER-PROFILE"), "manifest format");
            check(manifest.contains("profile-revision: 0"), "manifest revision");
            check(classSpec.contains("format: ICESMP-PLAYER-PROFILE-SECTION"), "section format");
            check(classSpec.contains("section: class-spec"), "section id");
            check(!classSpec.contains("ICS2") && !classSpec.contains("payload:"),
                    "no opaque class-spec blob");
            try (var paths = Files.walk(dir)) {
                check(paths.noneMatch(p -> p.getFileName().toString().endsWith(".b64")),
                        "no Base64 payload files");
            }
            repo.invalidate(PLAYER);
            PlayerProfileSnapshot second = join(repo.load(PLAYER));
            check(second.sectionMap().size() == ProfileSectionId.values().length,
                    "roundtrip section count");
            check(second.sectionRevisions().equals(first.sectionRevisions()),
                    "roundtrip revisions");
        } finally {
            shutdown(repo);
        }
    }

    private static void sectionCasAndExtensions() throws Exception {
        Path root = Files.createTempDirectory("pp-yaml-cas-");
        YamlPlayerProfileRepository repo = repository(root);
        try {
            PlayerProfileSnapshot p = join(repo.load(PLAYER));
            PreferenceSection current = p.preferences().value();
            PreferenceSection value = new PreferenceSection(
                    "en_US", true, false, true, true, true, true, true, true,
                    Map.of("theme", "compact"), Map.of("future-data", List.of("x")));
            ProfileSectionSnapshot<PreferenceSection> next = new ProfileSectionSnapshot<>(
                    ProfileSectionId.PREFERENCES, p.preferences().schema(), 1,
                    NOW.plusSeconds(1), value, SectionHealth.healthy(),
                    Map.of("future-root", Map.of("enabled", true)));
            PlayerProfileRepository.SectionSaveResult committed = join(repo.saveSection(
                    PLAYER, ProfileSectionId.PREFERENCES, 0, 0, next));
            check(committed.status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED,
                    "CAS commit");
            check(committed.snapshot().profileRevision() == 1, "generation increment");
            check(committed.snapshot().preferences().revision() == 1, "section increment");

            PlayerProfileRepository.SectionSaveResult stale = join(repo.saveSection(
                    PLAYER, ProfileSectionId.PREFERENCES, 0, next));
            check(stale.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_REVISION,
                    "stale revision rejected");

            ProfileSectionSnapshot<PreferenceSection> skipped = new ProfileSectionSnapshot<>(
                    ProfileSectionId.PREFERENCES, 1, 3, NOW, value,
                    SectionHealth.healthy(), next.extensions());
            PlayerProfileRepository.SectionSaveResult rejected = join(repo.saveSection(
                    PLAYER, ProfileSectionId.PREFERENCES, 1, skipped));
            check(rejected.status() == PlayerProfileRepository.SectionSaveResult.Status.REJECTED,
                    "skipped revision rejected");

            ProfileSectionSnapshot<PreferenceSection> revisionTwo = new ProfileSectionSnapshot<>(
                    ProfileSectionId.PREFERENCES, 1, 2, NOW, value,
                    SectionHealth.healthy(), next.extensions());
            PlayerProfileRepository.SectionSaveResult staleGeneration = join(repo.saveSection(
                    PLAYER, ProfileSectionId.PREFERENCES, 1, 0, revisionTwo));
            check(staleGeneration.status()
                            == PlayerProfileRepository.SectionSaveResult.Status.STALE_GENERATION,
                    "stale generation rejected");

            repo.invalidate(PLAYER);
            PlayerProfileSnapshot loaded = join(repo.load(PLAYER));
            check(loaded.preferences().extensions().containsKey("future-root"),
                    "unknown root extension preserved");
            check(loaded.preferences().value().extensions().containsKey("future-data"),
                    "unknown data extension preserved");
            check(loaded.preferences().value().language().equals("en_US"), "structured data roundtrip");
        } finally {
            shutdown(repo);
        }
    }

    private static void quarantineIsolationAndRecovery() throws Exception {
        Path root = Files.createTempDirectory("pp-yaml-quarantine-");
        YamlPlayerProfileRepository repo = repository(root);
        try {
            join(repo.load(PLAYER));
            PlayerProfileRepository.QuarantineResult quarantined = join(repo.quarantineSection(
                    PLAYER, ProfileSectionId.STATISTICS,
                    "corrupt-statistics".getBytes(StandardCharsets.UTF_8), "bad stats"));
            check(quarantined.snapshot().health().status() == ProfileHealth.Status.PARTIAL,
                    "section quarantine is partial");
            check(!quarantined.snapshot().statistics().health().usable(), "statistics blocked");
            check(quarantined.snapshot().classSpec().health().usable(), "class spec remains usable");
            Path evidence = root.resolve(PLAYER.toString()).resolve("quarantine")
                    .resolve("statistics").resolve(quarantined.evidenceId() + ".evidence");
            check(Files.isRegularFile(evidence), "evidence preserved");

            PlayerProfileRepository.RecoveryResult recovered = join(repo.recoverSection(
                    PLAYER, ProfileSectionId.STATISTICS,
                    quarantined.evidenceId(), "audit-1"));
            check(!recovered.idempotent(), "first recovery mutates");
            check(recovered.snapshot().statistics().health().usable(), "recovered usable");
            check(recovered.snapshot().statistics().health().recoveryAuditId().equals("audit-1"),
                    "audit retained");
            check(Files.isRegularFile(evidence), "recovery never deletes evidence");

            PlayerProfileRepository.RecoveryResult replay = join(repo.recoverSection(
                    PLAYER, ProfileSectionId.STATISTICS,
                    quarantined.evidenceId(), "audit-1"));
            check(replay.idempotent(), "recovery replay idempotent");
        } finally {
            shutdown(repo);
        }
    }

    private static void manifestDeclaredMissingSectionFailsClosed() throws Exception {
        Path root = Files.createTempDirectory("pp-yaml-missing-section-");
        YamlPlayerProfileRepository repo = repository(root);
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000001102");
        try {
            PlayerProfileSnapshot initial = join(repo.load(player));
            PreferenceSection value = new PreferenceSection(
                    "hu_HU", true, true, true, true, true, true, true, true,
                    Map.of("theme", "missing-section-proof"), Map.of());
            ProfileSectionSnapshot<PreferenceSection> next = new ProfileSectionSnapshot<>(
                    ProfileSectionId.PREFERENCES, initial.preferences().schema(), 1,
                    NOW.plusSeconds(1), value, SectionHealth.healthy(), Map.of());
            PlayerProfileRepository.SectionSaveResult committed = join(repo.saveSection(
                    player, ProfileSectionId.PREFERENCES, 0, 0, next));
            check(committed.status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED,
                    "missing-section fixture revision committed");

            Path dir = root.resolve(player.toString());
            Path section = dir.resolve(ProfileSectionId.PREFERENCES.fileName());
            Files.delete(section);
            repo.invalidate(player);

            PlayerProfileSnapshot loaded = join(repo.load(player));
            check(loaded.health().status() == ProfileHealth.Status.PARTIAL,
                    "declared missing section makes profile partial");
            check(!loaded.preferences().health().usable(),
                    "declared missing section quarantined");
            check(loaded.preferences().revision() == 1,
                    "manifest-declared revision preserved in quarantine snapshot");
            check(loaded.preferences().health().diagnostic().contains("MISSING_SECTION"),
                    "missing-section diagnostic is explicit");
            check(!Files.exists(section),
                    "repository never writes a default over a manifest-declared missing section");
            check(loaded.classSpec().health().usable(),
                    "unrelated section remains usable after missing-section quarantine");

            String evidenceId = loaded.preferences().health().evidenceId();
            check(evidenceId != null && !evidenceId.isBlank(), "missing-section evidence id recorded");
            Path marker = dir.resolve("quarantine").resolve("preferences.marker.yml");
            Path evidence = dir.resolve("quarantine").resolve("preferences")
                    .resolve(evidenceId + ".evidence");
            check(Files.isRegularFile(marker), "missing-section quarantine marker written");
            check(Files.isRegularFile(evidence), "missing-section evidence written");

            repo.invalidate(player);
            PlayerProfileSnapshot reloaded = join(repo.load(player));
            check(!reloaded.preferences().health().usable(),
                    "missing-section quarantine survives reload");
            check(evidenceId.equals(reloaded.preferences().health().evidenceId()),
                    "missing-section evidence identity is stable across reload");
            check(!Files.exists(section),
                    "reload still does not synthesize the lost section file");
        } finally {
            shutdown(repo);
        }
    }

    private static void invalidUtf8OversizeOwnerAndTempRecovery() throws Exception {
        Path root = Files.createTempDirectory("pp-yaml-invalid-");
        YamlPlayerProfileRepository repo = repository(root);
        try {
            UUID invalid = UUID.randomUUID();
            join(repo.load(invalid));
            Path invalidDir = root.resolve(invalid.toString());
            Path orphan = invalidDir.resolve("preferences.yml.tmp-orphan");
            Files.writeString(orphan, "orphan");
            Files.write(invalidDir.resolve("preferences.yml"), new byte[]{(byte) 0xC3, 0x28});
            repo.invalidate(invalid);
            PlayerProfileSnapshot partial = join(repo.load(invalid));
            check(!Files.exists(orphan), "orphan temp cleaned");
            check(!partial.preferences().health().usable(), "invalid UTF-8 quarantined");
            check(partial.classSpec().health().usable(), "unrelated section survives UTF-8 error");

            UUID oversized = UUID.randomUUID();
            join(repo.load(oversized));
            Path stats = root.resolve(oversized.toString()).resolve("statistics.yml");
            Files.write(stats, new byte[(int) YamlPlayerProfileRepository.MAX_YAML_BYTES + 1]);
            repo.invalidate(oversized);
            PlayerProfileSnapshot oversizedProfile = join(repo.load(oversized));
            check(!oversizedProfile.statistics().health().usable(), "oversized section quarantined");

            UUID owner = UUID.randomUUID();
            join(repo.load(owner));
            Path manifest = root.resolve(owner.toString()).resolve("manifest.yml");
            String content = Files.readString(manifest).replace(owner.toString(), UUID.randomUUID().toString());
            Files.writeString(manifest, content);
            repo.invalidate(owner);
            expectStage(IllegalArgumentException.class, repo.load(owner));
        } finally {
            shutdown(repo);
        }
    }

    @SuppressWarnings("unchecked")
    private static void strictStructuredCodecPolicies() {
        ProfileSectionSnapshot<PreferenceSection> section = new ProfileSectionSnapshot<>(
                ProfileSectionId.PREFERENCES, 1, 0, NOW,
                PreferenceSection.empty(0), SectionHealth.healthy(),
                Map.of("future", Map.of("number", 7L)));
        Map<String, Object> encoded = StructuredPlayerProfileYaml.encodeSection(section);
        ProfileSectionSnapshot<?> decoded = StructuredPlayerProfileYaml.decodeSection(
                ProfileSectionId.PREFERENCES, encoded);
        check(decoded.extensions().containsKey("future"), "structured extension roundtrip");

        Map<String, Object> missingOptional = new LinkedHashMap<>(encoded);
        Map<String, Object> data = new LinkedHashMap<>((Map<String, Object>) missingOptional.get("data"));
        data.remove("values");
        data.remove("extensions");
        missingOptional.put("data", data);
        PreferenceSection defaults = (PreferenceSection) StructuredPlayerProfileYaml.decodeSection(
                ProfileSectionId.PREFERENCES, missingOptional).value();
        check(defaults.values().isEmpty(), "missing optional map defaults empty");

        Map<String, Object> futureSchema = new LinkedHashMap<>(encoded);
        futureSchema.put("schema", 2);
        expect(IllegalArgumentException.class, () -> StructuredPlayerProfileYaml.decodeSection(
                ProfileSectionId.PREFERENCES, futureSchema));

        Map<String, Object> invalidType = new LinkedHashMap<>(encoded);
        Map<String, Object> badData = new LinkedHashMap<>((Map<String, Object>) invalidType.get("data"));
        badData.put("language", 42);
        invalidType.put("data", badData);
        expect(IllegalArgumentException.class, () -> StructuredPlayerProfileYaml.decodeSection(
                ProfileSectionId.PREFERENCES, invalidType));
    }

    private static void concurrentJvmLockIdentityRemainsStable() throws Exception {
        Path root = Files.createTempDirectory("pp-yaml-lock-race-");
        YamlPlayerProfileRepository first = repository(root);
        YamlPlayerProfileRepository second = repository(root);
        ExecutorService callers = Executors.newFixedThreadPool(8);
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000001103");
        Method withLock = YamlPlayerProfileRepository.class.getDeclaredMethod(
                "withLock", UUID.class, Callable.class);
        withLock.setAccessible(true);
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                YamlPlayerProfileRepository repository = worker % 2 == 0 ? first : second;
                attempts.add(callers.submit(() -> {
                    for (int iteration = 0; iteration < 500; iteration++) {
                        invokeWithLock(withLock, repository, player);
                    }
                    return null;
                }));
            }
            for (Future<?> attempt : attempts) attempt.get(30, TimeUnit.SECONDS);
            check(true, "concurrent lock cycles retain a single JVM lock identity");
        } finally {
            callers.shutdownNow();
            shutdown(first);
            shutdown(second);
        }
    }

    private static void invokeWithLock(Method withLock, YamlPlayerProfileRepository repository,
                                       UUID player) throws Exception {
        try {
            withLock.invoke(repository, player, (Callable<Void>) () -> {
                Thread.yield();
                return null;
            });
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new AssertionError(cause);
        }
    }

    private static YamlPlayerProfileRepository repository(Path root) {
        return new YamlPlayerProfileRepository(root, CLOCK,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void shutdown(YamlPlayerProfileRepository repo) {
        join(repo.shutdown(Duration.ofSeconds(5)));
    }

    private static void expectStage(Class<? extends Throwable> expected, CompletionStage<?> stage) {
        assertions++;
        try {
            stage.toCompletableFuture().join();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (!expected.isInstance(cause)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + cause, cause);
            }
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> expected, Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
