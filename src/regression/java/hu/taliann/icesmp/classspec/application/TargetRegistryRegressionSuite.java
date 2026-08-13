package hu.taliann.icesmp.classspec.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/** Dependency-free behavior regression for transient caster-target lifecycle indexes. */
public final class TargetRegistryRegressionSuite {
    private static int assertions;

    private TargetRegistryRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        for (final String service : List.of("Warrior", "Paladin", "Evoker", "Monk")) {
            targetExitClearsEveryCaster(service);
            casterExitClearsEveryReverseLink(service);
        }
        concurrentLinksStayBidirectional();
        serviceLifecycleContracts();
        System.out.println("Target registry regression suite passed. assertions=" + assertions);
    }

    private static void targetExitClearsEveryCaster(final String service) {
        final TargetRegistry registry = new TargetRegistry();
        final UUID target = id(service, 1);
        final UUID first = id(service, 2);
        final UUID second = id(service, 3);
        registry.link(first, target);
        registry.link(second, target);
        check(registry.unlinkTarget(target).equals(java.util.Set.of(first, second)),
                service + " target death/quit did not return every caster");
        check(registry.targetsOf(first).isEmpty() && registry.targetsOf(second).isEmpty(),
                service + " target death/quit left an owner-side stale pair");
        check(registry.ownersOf(target).isEmpty(),
                service + " target death/quit left a reverse-index entry");
    }

    private static void casterExitClearsEveryReverseLink(final String service) {
        final TargetRegistry registry = new TargetRegistry();
        final UUID caster = id(service, 4);
        final UUID first = id(service, 5);
        final UUID second = id(service, 6);
        registry.link(caster, first);
        registry.link(caster, second);
        check(registry.unlinkOwner(caster).equals(java.util.Set.of(first, second)),
                service + " caster exit did not return every target");
        check(registry.ownersOf(first).isEmpty() && registry.ownersOf(second).isEmpty(),
                service + " caster exit left a target-side stale pair");
        check(registry.targetsOf(caster).isEmpty(),
                service + " caster exit left an owner entry");
    }

    private static void concurrentLinksStayBidirectional() throws InterruptedException {
        final TargetRegistry registry = new TargetRegistry();
        final UUID target = id("shared", 1);
        final int workers = 12;
        final CountDownLatch ready = new CountDownLatch(workers);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            final UUID owner = id("worker", index);
            threads.add(Thread.ofPlatform().start(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int cycle = 0; cycle < 1_000; cycle++) {
                    registry.link(owner, target);
                    registry.unlink(owner, target);
                }
                registry.link(owner, target);
            }));
        }
        ready.await();
        start.countDown();
        for (final Thread thread : threads) thread.join();
        check(registry.ownersOf(target).size() == workers,
                "concurrent region use lost a reverse link");
        for (final UUID owner : registry.ownersOf(target)) {
            check(registry.targetsOf(owner).contains(target),
                    "concurrent region use produced a one-sided link");
        }
        registry.unlinkTarget(target);
        check(registry.ownersOf(target).isEmpty(),
                "concurrent target cleanup did not empty the index");
    }

    private static void serviceLifecycleContracts() throws Exception {
        for (final String type : List.of("warrior/Warrior", "paladin/Paladin",
                "evoker/Evoker", "monk/Monk")) {
            final String source = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/",
                    type + "GameplayService.java"));
            check(source.contains("new TargetRegistry()"),
                    type + " service does not use the shared UUID-only registry");
            check(source.contains("unlinkTarget(playerId)")
                            || source.contains("clearBeaconTarget(playerId)")
                            || source.contains("clearMarkTarget(playerId)")
                            || source.contains("clearLinkTarget(playerId)"),
                    type + " player cleanup does not clear target-side links");
            check(source.contains("onPlayerDeath") && source.contains("onQuit"),
                    type + " service lacks death/quit lifecycle entry points");
        }
    }

    private static UUID id(final String namespace, final int value) {
        return UUID.nameUUIDFromBytes((namespace + ':' + value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
