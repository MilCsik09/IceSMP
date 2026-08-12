package hu.taliann.icesmp.playerprofile.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * A részleges startup / sikertelen shutdown-drain utáni erőforrás-zárást bizonyítja:
 * a repository shutdown idempotens és utána minden új munkát elutasít, a core disable
 * pedig forrás-szerződés szinten always-cleanup útvonalon zárja a PlayerProfile
 * erőforrásokat (finally + idempotens platform-teardown), nem csak a happy pathen.
 */
public final class PlayerProfileLifecycleTeardownRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static int assertions;

    private PlayerProfileLifecycleTeardownRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        repositoryShutdownIsIdempotentAndFencesNewWork();
        coreDisableAlwaysClosesProfileResources();
        profileAuthorityOutlivesStatefulConsumers();
        platformTeardownIsUnconditional();
        httpAuthPrecedesLookupInSource();
        System.out.println("PlayerProfile lifecycle teardown regression suite passed. assertions=" + assertions);
    }

    private static void repositoryShutdownIsIdempotentAndFencesNewWork() throws Exception {
        final Path root = Files.createTempDirectory("pp-teardown");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        repository.load(PLAYER).toCompletableFuture().get(10, TimeUnit.SECONDS);
        check(true, "repository accepts work before shutdown");

        final PlayerProfileRepository.ShutdownResult first =
                repository.shutdown(Duration.ofSeconds(5)).toCompletableFuture().get(10, TimeUnit.SECONDS);
        check(first.drained(), "first shutdown drains");

        final PlayerProfileRepository.ShutdownResult second =
                repository.shutdown(Duration.ofSeconds(5)).toCompletableFuture().get(10, TimeUnit.SECONDS);
        check(second.drained(), "second shutdown is an idempotent no-op");

        // Friss, cache-elhetetlen owner kell: a load() cache-találatra submit nélkül válaszol,
        // a fence-t csak a tényleges I/O-útvonal bizonyítja.
        boolean fenced = false;
        try {
            repository.load(UUID.fromString("00000000-0000-0000-0000-000000000912"))
                    .toCompletableFuture().join();
        } catch (final CompletionException expected) {
            fenced = expected.getCause() instanceof IllegalStateException;
        }
        check(fenced, "post-shutdown work is rejected with a repository-stopped failure");
    }

    private static void coreDisableAlwaysClosesProfileResources() throws Exception {
        final String core = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        final int tryIndex = core.indexOf("disableStateful();");
        final int finallyIndex = core.indexOf("closePlayerProfileResources();");
        check(tryIndex > 0 && finallyIndex > tryIndex,
                "disable() wraps the stateful path and always reaches the resource closer");
        check(core.contains("} finally {"), "the resource closer runs from a finally block");

        final int closer = core.indexOf("private void closePlayerProfileResources()");
        check(closer > 0, "the resource closer exists");
        final String closerBody = core.substring(closer, core.indexOf("private void shutdownStep", closer));
        check(closerBody.contains("profileSessionBridge.stopRuntime"),
                "closer stops runtime admission");
        check(closerBody.contains("playerProfilePlatform.shutdown"),
                "closer tears down the platform (service, HTTP, repository executor)");
        check(closerBody.contains("playerProfileAuthority.uninstall"),
                "closer uninstalls the static authority");

        final int abortBranch = core.indexOf("IceSMP enable did not complete");
        check(abortBranch > 0, "partial-enable branch exists");
        final String abortBody = core.substring(abortBranch, core.indexOf("return;", abortBranch));
        check(!abortBody.contains("playerProfileAuthority.uninstall"),
                "partial-enable branch defers ALL profile teardown to the shared closer");
    }

    private static void platformTeardownIsUnconditional() throws Exception {
        final String platform = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/playerprofile/integration/PlayerProfilePlatform.java"));
        check(platform.contains("if(httpConfig.enabled())"),
                "HTTP adapter is only instantiated when the API is enabled");
        check(!platform.contains("if(!started)return repository.shutdown"),
                "shutdown no longer short-circuits on the started flag");
        check(platform.contains("serviceRegistered"),
                "service registration has its own lifecycle state");
        final int shutdownIndex = platform.indexOf("shutdown(Duration timeout)");
        check(shutdownIndex > 0, "platform shutdown exists");
        final String shutdownBody = platform.substring(shutdownIndex);
        check(shutdownBody.contains("service.shutdown(timeout)"),
                "platform shutdown always reaches the repository teardown");
    }

    private static void profileAuthorityOutlivesStatefulConsumers() throws Exception {
        final String core = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        final int methodStart = core.indexOf("private void disableStateful()");
        final int methodEnd = core.indexOf("private void closePlayerProfileResources()",
                methodStart);
        check(methodStart > 0 && methodEnd > methodStart,
                "stateful shutdown source section exists");
        final String shutdown = core.substring(methodStart, methodEnd);
        final int respecGuard = shutdown.indexOf("respecService.prepareShutdown");
        final int crateShutdown = shutdown.indexOf(
                "shutdownStep(\"crateManager\", crateManager::shutdown)");
        final int finalSave = shutdown.indexOf("storeCoordinator.saveForShutdown");
        final int profileDrain = shutdown.indexOf("profileSessionBridge.prepareDisable");
        final int platformShutdown = shutdown.indexOf("playerProfilePlatform.shutdown");
        final int authorityUninstall = shutdown.indexOf("playerProfileAuthority.uninstall");
        check(respecGuard > 0 && crateShutdown > respecGuard,
                "respec admission guard remains ahead of consumer shutdown");
        check(finalSave > crateShutdown && profileDrain > finalSave,
                "crate rollback and final store save run before profile drain");
        check(platformShutdown > profileDrain && authorityUninstall > platformShutdown,
                "platform drain completes before authority uninstall");

        final String crate = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/CrateManager.java"));
        final int crateMethod = crate.indexOf("public void shutdown()");
        final int crateMethodEnd = crate.indexOf("public void clearPlayerState", crateMethod);
        check(crateMethod > 0 && crateMethodEnd > crateMethod
                        && crate.substring(crateMethod, crateMethodEnd)
                        .contains("drainDeferredCurrencyRollbacks()"),
                "crate shutdown still drains durable currency rollbacks");
        final String currency = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/CurrencyManager.java"));
        final int save = currency.indexOf("public void save()");
        final int saveEnd = currency.indexOf("public void requestSave()", save);
        check(save > 0 && saveEnd > save
                        && currency.substring(save, saveEnd)
                        .contains("PlayerProfileAuthority.installed()"),
                "currency final save still flushes through the live profile authority");
    }

    private static void httpAuthPrecedesLookupInSource() throws Exception {
        final String http = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/playerprofile/http/PlayerProfileHttpServer.java"));
        final int branch = http.indexOf("\"by-name\".equals(parts[4])");
        check(branch > 0, "by-name branch exists");
        final int auth = http.indexOf("authenticate(exchange, Scope.SELF)", branch);
        final int scan = http.indexOf("api.findByName(name)", branch);
        check(auth > 0 && scan > 0 && auth < scan,
                "by-name authenticates before any storage lookup");
    }

    private static void check(final boolean condition, final String label) {
        if (!condition) {
            throw new IllegalStateException("Lifecycle teardown regression failed: " + label);
        }
        assertions++;
    }
}
