package hu.taliann.icesmp.listeners;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ResourcePackDeliveryRegressionSuite {
    private static int assertions;

    public static void main(final String[] args) throws Exception {
        exactOwnedLayersAndLateSends();
        failedPacketsRemainObservable();
        closeCannotMissAnEnteredSend();
        runtimeUsesOwnerCallbacksBeforeRetirement();
        System.out.println("Resource-pack delivery lifecycle passed. assertions=" + assertions);
    }

    private static void exactOwnedLayersAndLateSends() {
        final var delivery = new ResourcePackListener.DeliveryState();
        final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        final List<String> packets = new ArrayList<>();
        check(delivery.offer(first, a, pack -> packets.add("remove:" + pack), () -> packets.add("send:a")), "first offer refused");
        check(delivery.offer(first, b, pack -> packets.add("remove:" + pack), () -> packets.add("send:b")), "replacement refused");
        check(packets.equals(List.of("send:a", "remove:" + a, "send:b")), "actual old layer was not removed before replacement");
        delivery.offer(second, a, pack -> { throw new AssertionError("unrelated layer removal"); }, () -> {});
        check(delivery.outstandingPlayers() == 2, "delivery lost another player");
        delivery.beginClose();
        check(delivery.closed(), "close did not fence delivery");
        check(!delivery.offer(first, a, pack -> { throw new AssertionError("late removal"); },
                () -> { throw new AssertionError("late send"); }), "late request reopened pack delivery");
        delivery.remove(first, pack -> packets.add("remove:" + pack));
        check(packets.getLast().equals("remove:" + b), "shutdown removed a guessed/current-config layer");
        check(delivery.outstandingPlayers() == 1, "one cleanup erased other player state");
        delivery.retire(second);
        check(delivery.outstandingPlayers() == 0, "retired connection retained delivery state");
        delivery.remove(first, pack -> { throw new AssertionError("duplicate removal"); });
        check(true, "cleanup is not idempotent");
    }

    private static void failedPacketsRemainObservable() {
        final var delivery = new ResourcePackListener.DeliveryState();
        final UUID player = UUID.randomUUID(), pack = UUID.randomUUID(), replacement = UUID.randomUUID();
        final RuntimeException original = new IllegalStateException("packet enqueue uncertain");
        try {
            delivery.offer(player, pack, ignored -> {}, () -> { throw original; });
            throw new AssertionError("send failure swallowed");
        } catch (RuntimeException failure) { check(failure == original, "original send failure lost"); }
        check(delivery.outstandingPlayers() == 1, "uncertain send became known absence");
        try {
            delivery.offer(player, replacement, ignored -> { throw original; }, () -> { throw new AssertionError("unsafe replacement"); });
            throw new AssertionError("remove failure swallowed");
        } catch (RuntimeException failure) { check(failure == original, "original removal failure lost"); }
        delivery.beginClose();
        try {
            delivery.remove(player, ignored -> { throw original; });
            throw new AssertionError("shutdown remove failure swallowed");
        } catch (RuntimeException failure) { check(failure == original, "shutdown failure lost"); }
        check(delivery.outstandingPlayers() == 1, "failed removal claimed restoration");
        final List<UUID> removed = new ArrayList<>();
        delivery.remove(player, removed::add);
        check(removed.equals(List.of(pack)), "recovery did not remove the actually offered ID");
        check(delivery.outstandingPlayers() == 0, "acknowledged removal retained outstanding state");
        delivery.clear();
        check(!delivery.offer(player, replacement, ignored -> {}, () -> {}), "clear reopened a closed lifecycle");
    }

    private static void closeCannotMissAnEnteredSend() throws Exception {
        final var delivery = new ResourcePackListener.DeliveryState();
        final UUID player = UUID.randomUUID(), pack = UUID.randomUUID();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var send = executor.submit(() -> delivery.offer(player, pack, ignored -> {}, () -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("send not released");
                } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            }));
            check(entered.await(5, TimeUnit.SECONDS), "native offer did not enter");
            final var close = executor.submit(delivery::beginClose);
            release.countDown();
            check(send.get(5, TimeUnit.SECONDS), "already entered send was lost");
            close.get(5, TimeUnit.SECONDS);
            final List<UUID> removed = new ArrayList<>();
            delivery.remove(player, removed::add);
            check(removed.equals(List.of(pack)), "close raced past an admitted layer");
            check(!delivery.offer(player, pack, ignored -> {}, () -> {}), "close/send race reopened delivery");
        } finally { release.countDown(); }
    }

    private static void runtimeUsesOwnerCallbacksBeforeRetirement() throws Exception {
        final String pack = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java"));
        final String plugin = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/IceSMP.java"));
        final String core = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        check(pack.contains("delivery.offer(player.getUniqueId(), current.id(), player::removeResourcePack"), "real send bypasses native delivery lifecycle");
        final String prepare = pack.substring(pack.indexOf("public java.util.concurrent.CompletableFuture<Void> prepareClose("), pack.indexOf("private void removeOwnedPacks"));
        check(prepare.indexOf("closeDelivery();") < prepare.indexOf("player.getScheduler().run"), "owner cleanup scheduled before closing late sends");
        check(prepare.contains("if (scheduled == null) retired.run();") && prepare.contains("completion.completeExceptionally"), "null/rejected/failed cleanup is not accounted");
        check(prepare.contains("Bukkit.isOwnedByCurrentRegion(player)") && prepare.contains("removeOwnedPacks(player)"), "cleanup does not remove native layers on the owner");
        final String close = pack.substring(pack.indexOf("public void close()"), pack.indexOf("private synchronized void closeDelivery()"));
        check(!close.contains("getScheduler()") && close.contains("Bukkit.isOwnedByCurrentRegion(player)"), "external disable schedules illegally or mutates a foreign player");
        check(!pack.contains("player.removeResourcePacks()"), "cleanup removes another plugin's packs");
        check(plugin.contains("core.prepareDisable()") && plugin.contains("core.playerShutdownCompletion()")
                && plugin.contains("resourcePackListener.prepareClose("), "real fail-closed route does not wait for native owner cleanup");
        check(core.contains("hu.taliann.icesmp.IceSMP.requestDisable(plugin)")
                && core.contains("if (!report.healthy())"), "NPC readiness gate was bypassed");
        check(core.contains("prepareDisable();\n            if (statefulShutdownPrepared) finishProfileShutdown();"), "profile teardown no longer follows native preparation");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
