package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.moderation.EntityTaskSubmission;
import hu.taliann.icesmp.sit.SitGeometry;
import hu.taliann.icesmp.sit.SitPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free behavioral and production-wiring regressions for the sit-only scope. */
public final class SitRegressionSuite {
    private SitRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        policyAndCommandNormalization();
        seatGeometry();
        atomicReservation();
        schedulerSingleWinner();
        productionWiring();
        System.out.println("Sit regression suite passed.");
    }

    private static void policyAndCommandNormalization() {
        equal(4.5D, SitPolicy.finiteNumber(4.5D), "YAML double accepted");
        equal(4.5D, SitPolicy.finiteNumber(4.5F), "YAML float accepted");
        equal(4.0D, SitPolicy.finiteNumber(4), "YAML integer accepted");
        rejects(() -> SitPolicy.finiteNumber(Double.NaN), "NaN config number");
        rejects(() -> SitPolicy.finiteNumber(Double.NEGATIVE_INFINITY), "infinite config number");
        rejects(() -> SitPolicy.finiteNumber("4.5"), "numeric string rejected");
        equal(4.5D, SitPolicy.validateClickDistance(4.5D), "valid distance");
        rejects(() -> SitPolicy.validateClickDistance(Double.NaN), "NaN distance");
        rejects(() -> SitPolicy.validateClickDistance(Double.POSITIVE_INFINITY), "infinite distance");
        rejects(() -> SitPolicy.validateClickDistance(0.99D), "short distance");
        rejects(() -> SitPolicy.validateClickDistance(16.01D), "long distance");

        equal("home", SitPolicy.commandRoot("/home"), "slash root");
        equal("home", SitPolicy.commandRoot("home arg"), "argument root");
        equal("home", SitPolicy.commandRoot("/plugin:home target"), "namespaced slash root");
        equal("home", SitPolicy.commandRoot("plugin:home"), "namespaced root");
        equal("homes", SitPolicy.commandRoot("/homes"), "similar command remains distinct");
        final Set<String> blocked = SitPolicy.normalizeCommandRoots(List.of("/home", "plugin:spawn"));
        check(SitPolicy.isCommandBlocked(blocked, "/other:home player"), "namespaced bypass blocked");
        check(!SitPolicy.isCommandBlocked(blocked, "/homes"), "similar command not blocked");
        rejects(() -> SitPolicy.normalizeCommandRoots(List.of("sit")), "escape command cannot be blocked");

        check(SitPolicy.isWorldAllowed(Set.of(), Set.of("blocked"), "world"), "open whitelist");
        check(!SitPolicy.isWorldAllowed(Set.of("world"), Set.of("world"), "world"), "blacklist wins");
        check(!SitPolicy.isWorldAllowed(Set.of("other"), Set.of(), "world"), "whitelist restricts");
    }

    private static void seatGeometry() {
        equal(0.50D, SitGeometry.offset(SitGeometry.Shape.STAIRS_BOTTOM, 1), "bottom stair surface");
        equal(1.00D, SitGeometry.offset(SitGeometry.Shape.STAIRS_TOP, 1), "top stair surface");
        equal(0.50D, SitGeometry.offset(SitGeometry.Shape.SLAB_BOTTOM, 1), "bottom slab");
        equal(1.00D, SitGeometry.offset(SitGeometry.Shape.SLAB_TOP_OR_DOUBLE, 1), "top/double slab");
        equal(0.0625D, SitGeometry.offset(SitGeometry.Shape.CARPET, 1), "carpet/moss carpet");
        equal(0.125D, SitGeometry.offset(SitGeometry.Shape.SNOW, 1), "one snow layer");
        equal(1.00D, SitGeometry.offset(SitGeometry.Shape.SNOW, 8), "eight snow layers");
        equal(1.00D, SitGeometry.offset(SitGeometry.Shape.GENERIC, 1), "generic full-block surface");
        rejects(() -> SitGeometry.offset(SitGeometry.Shape.SNOW, 0), "invalid snow layers");

        final SitGeometry.Anchor north = SitGeometry.stairAnchor(false,
                SitGeometry.Facing.NORTH, SitGeometry.StairShape.STRAIGHT);
        equal(0.50D, north.x(), "north stair centered x");
        equal(0.50D, north.y(), "north stair lower tread height");
        equal(0.75D, north.z(), "north stair front tread");

        final SitGeometry.Anchor east = SitGeometry.stairAnchor(false,
                SitGeometry.Facing.EAST, SitGeometry.StairShape.STRAIGHT);
        equal(0.25D, east.x(), "east stair front tread");
        equal(0.50D, east.z(), "east stair centered z");

        final SitGeometry.Anchor innerLeft = SitGeometry.stairAnchor(false,
                SitGeometry.Facing.NORTH, SitGeometry.StairShape.INNER_LEFT);
        equal(0.75D, innerLeft.x(), "inner-left chooses open tread quadrant");
        equal(0.75D, innerLeft.z(), "inner-left stays at stair front");

        final SitGeometry.Anchor outerLeft = SitGeometry.stairAnchor(false,
                SitGeometry.Facing.NORTH, SitGeometry.StairShape.OUTER_LEFT);
        equal(0.25D, outerLeft.x(), "outer-left follows its backrest quadrant");

        final SitGeometry.Anchor top = SitGeometry.stairAnchor(true,
                SitGeometry.Facing.WEST, SitGeometry.StairShape.INNER_RIGHT);
        equal(new SitGeometry.Anchor(0.50D, 1.00D, 0.50D), top, "top stair uses full top surface");
    }

    private static void atomicReservation() throws Exception {
        final SitState state = new SitState();
        final SitState.SeatKey key = new SitState.SeatKey(UUID.randomUUID(), 1, 2, 3);
        final int contenders = 24;
        final CountDownLatch ready = new CountDownLatch(contenders);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(contenders);
        final AtomicInteger winners = new AtomicInteger();
        for (int index = 0; index < contenders; index++) {
            final UUID player = UUID.randomUUID();
            final Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (state.reserve(player, key) == SitState.ReserveResult.RESERVED) {
                        winners.incrementAndGet();
                    }
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        ready.await();
        start.countDown();
        done.await();
        equal(1, winners.get(), "one reservation winner");
        equal(1, state.size(), "one ledger entry");

        final UUID owner = state.occupant(key);
        check(owner != null, "seat owner exists");
        final UUID stand = UUID.randomUUID();
        check(state.activate(owner, key, stand), "reservation activates once");
        check(!state.activate(owner, key, UUID.randomUUID()), "cannot activate twice");
        check(state.release(owner) != null, "release succeeds");
        check(state.release(owner) == null, "release is idempotent");
        equal(0, state.size(), "ledger empty after release");
    }

    private static void schedulerSingleWinner() {
        final AtomicInteger task = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        check(!EntityTaskSubmission.submit((run, retire) -> null,
                task::incrementAndGet, rejected::incrementAndGet), "null handle rejected");
        equal(0, task.get(), "null handle task count");
        equal(1, rejected.get(), "null handle fallback count");

        task.set(0); rejected.set(0);
        check(!EntityTaskSubmission.submit((run, retire) -> { throw new IllegalStateException("boom"); },
                task::incrementAndGet, rejected::incrementAndGet), "submit exception rejected");
        equal(0, task.get(), "exception task count");
        equal(1, rejected.get(), "exception fallback count");

        task.set(0); rejected.set(0);
        final Runnable[] callbacks = new Runnable[2];
        check(EntityTaskSubmission.submit((run, retire) -> {
            callbacks[0] = run;
            callbacks[1] = retire;
            return new Object();
        }, task::incrementAndGet, rejected::incrementAndGet), "accepted handle");
        callbacks[1].run();
        callbacks[0].run();
        equal(0, task.get(), "retirement wins task race");
        equal(1, rejected.get(), "retirement exactly once");
    }

    private static void productionWiring() throws IOException {
        final String manager = source("src/main/java/hu/taliann/icesmp/managers/SitManager.java");
        final String listener = source("src/main/java/hu/taliann/icesmp/listeners/SitListener.java");
        final String campfire = source("src/main/java/hu/taliann/icesmp/listeners/CampfireStoryListener.java");
        final String command = source("src/main/java/hu/taliann/icesmp/commands/SitCommand.java");
        final String core = source("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String permissions = source("src/main/java/hu/taliann/icesmp/core/Permissions.java");
        final String config = source("src/main/resources/config/sit.yml");
        final String messages = source("src/main/resources/messages/sit.yml");
        final String build = source("build.gradle.kts");

        contains(manager, "PaperEntityTaskSubmission.run", "single-winner entity scheduler adapter");
        contains(manager, "EntityTaskSubmission.submit", "region fallback adapter");
        contains(manager, "requestRegistryRemoval(handle)", "retirement safe fallback");
        contains(manager, "SitPolicy.finiteNumber", "finite YAML numeric parser");
        contains(manager, "setPersistent(false)", "non-persistent seat entity");
        contains(manager, "PersistentDataType.BYTE", "PDC seat identity");
        contains(manager, "CompletableFuture.allOf", "bounded shutdown drain");
        contains(manager, "SitState.ReserveResult", "atomic reservation ledger");
        contains(manager, "Tag.CARPETS", "carpet support");
        contains(manager, "Material.MOSS_CARPET", "moss carpet support");
        contains(manager, "Material.PALE_MOSS_CARPET", "pale moss carpet support");
        contains(manager, "data instanceof Snow", "snow shape support");
        contains(manager, "successfulSitHandler.accept", "successful sit callback");
        contains(manager, "SitGeometry.stairAnchor", "directional stair anchor");
        contains(manager, "seatSessionId", "continuous seat identity");
        contains(manager, "isSittingOn", "exact active seat validation");
        contains(campfire, "onSuccessfulSit", "sit-only campfire admission");
        contains(campfire, "seatBlock.getRelative(direction, 2)", "campfire two blocks from seat");
        contains(campfire, "middle.getType().isAir()", "empty middle block requirement");
        contains(campfire, "campfire.isLit()", "lit campfire requirement");
        contains(campfire, "seatSessionId", "continuous seat capture");
        contains(campfire, "stillEligible", "reward-time seating revalidation");
        absent(campfire, "PlayerInteractEvent", "direct campfire click trigger");
        contains(listener, "EquipmentSlot.HAND", "off-hand double-event rejection");
        contains(listener, "EntityDismountEvent", "dismount cleanup");
        contains(listener, "PlayerChangedWorldEvent", "world-change cleanup");
        contains(listener, "BlockBreakEvent", "support-block cleanup");
        contains(listener, "PlayerTeleportEvent", "teleport cleanup");
        contains(listener, "PlayerQuitEvent", "quit cleanup");
        contains(listener, "PlayerKickEvent", "kick cleanup");
        contains(core, "sitManager.reload()", "reload cleanup wiring");
        contains(core, "setSuccessfulSitHandler", "campfire sit callback wiring");
        contains(core, "sitManager::shutdown", "disable cleanup wiring");
        contains(core, "key.startsWith(\"sit.\")", "live config generation refresh");
        contains(permissions, "icesmp.sit", "central player permission");
        contains(build, "sitRegressionTest", "Gradle regression task");
        contains(config, "PALE_MOSS_CARPET", "supported material config");
        contains(messages, "Használat: &f/sit [fel]", "sit-only usage");

        final String all = manager + listener + campfire + command + core + permissions + config + messages + build;
        absent(all, "LayPoseBridge", "lay bridge");
        absent(all, "toggleLay", "lay runtime");
        absent(all, "toggleCrawl", "crawl runtime");
        absent(all, "SIT_POSE", "pose permission");
        absent(all, "crawl-refresh", "crawl config");
        absent(command, "fekves", "lay command");
        absent(command, "maszas", "crawl command");
        check(!Files.exists(Path.of("src/main/java/hu/taliann/icesmp/integration/LayPoseBridge.java")),
                "LayPoseBridge file removed");
    }

    private static String source(final String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    private static void contains(final String text, final String token, final String label) {
        check(text.contains(token), "missing " + label + " token: " + token);
    }

    private static void absent(final String text, final String token, final String label) {
        check(!text.contains(token), "forbidden " + label + " token: " + token);
    }

    private static void rejects(final ThrowingRunnable action, final String label) {
        try {
            action.run();
            throw new AssertionError("expected rejection: " + label);
        } catch (final IllegalArgumentException expected) {
            // expected
        } catch (final Exception unexpected) {
            throw new AssertionError("unexpected exception for " + label, unexpected);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(final Object expected, final Object actual, final String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
