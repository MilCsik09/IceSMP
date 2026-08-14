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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/** Restart-safe death escrow deposit, delivery and settlement regressions. */
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

            check(store.pending(player).toCompletableFuture().join().isEmpty(),
                    "greenfield escrow is empty");
            final var deposited = store.deposit(player, "death:1000", List.of("item-A"), 1_000L)
                    .toCompletableFuture().join();
            check(deposited.applied() && deposited.pendingItems() == 1,
                    "death deposit is durable before drop removal");
            check(!store.deposit(player, "death:1000", List.of("item-A"), 1_000L)
                    .toCompletableFuture().join().applied(), "deposit replay is idempotent");
            expect(IllegalStateException.class, () -> store.deposit(
                    player, "death:1000", List.of("different"), 1_000L)
                    .toCompletableFuture().join());

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileDeathEscrowStore.Batch afterDeath = store.pending(player)
                    .toCompletableFuture().join().getFirst();
            check(afterDeath.encodedItems().equals(List.of("item-A")),
                    "escrow survives the death-to-respawn restart");

            final Set<String> simulatedInventory = new HashSet<>();
            final List<DeathEscrowDeliveryPlan.Item> firstDelivery =
                    DeathEscrowDeliveryPlan.missing(afterDeath, simulatedInventory);
            check(firstDelivery.size() == 1, "first join plans one physical relic delivery");
            simulatedInventory.add(firstDelivery.getFirst().deliveryId());

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileDeathEscrowStore.Batch recovery = store.pending(player)
                    .toCompletableFuture().join().getFirst();
            check(DeathEscrowDeliveryPlan.missing(recovery, simulatedInventory).isEmpty(),
                    "restart after inventory write detects the delivery marker");
            check(store.settle(player, recovery.receiptId(), 2_000L)
                            .toCompletableFuture().join()
                            == PlayerProfileDeathEscrowStore.SettleStatus.SETTLED,
                    "join recovery settles the durable receipt");
            check(store.settle(player, recovery.receiptId(), 2_001L)
                            .toCompletableFuture().join()
                            == PlayerProfileDeathEscrowStore.SettleStatus.ALREADY_SETTLED,
                    "settlement replay cannot deliver twice");
            check(store.pending(player).toCompletableFuture().join().isEmpty()
                            && simulatedInventory.size() == 1,
                    "death, restart and join leave exactly one kept relic");

            authority.mutateSection(player, ProfileSectionId.LIFECYCLE,
                    LifecycleSection.class, current -> {
                        final var extensions = new LinkedHashMap<>(current.extensions());
                        extensions.put("death-escrow.items", List.of("legacy-item"));
                        extensions.put("death-escrow.created-at", 2_500L);
                        return new LifecycleSection(current.status(), current.profileCreatedAt(),
                                current.updatedAt(), current.lastJoinAt(), current.lastQuitAt(),
                                current.sessionGeneration(), extensions);
                    }).toCompletableFuture().join();
            final var legacy = store.pending(player).toCompletableFuture().join().getFirst();
            check(legacy.receiptId().equals("legacy-2500")
                            && legacy.encodedItems().equals(List.of("legacy-item")),
                    "legacy escrow keys remain readable");
            check(store.settle(player, legacy.receiptId(), 2_600L)
                            .toCompletableFuture().join()
                            == PlayerProfileDeathEscrowStore.SettleStatus.SETTLED,
                    "legacy escrow migrates through normal settlement");

            final List<String> flood = new ArrayList<>();
            for (int index = 0; index < 33; index++) flood.add("item-" + index);
            expect(IllegalStateException.class, () -> store.deposit(
                    player, "death:flood", flood, 3_000L).toCompletableFuture().join());

            final LifecycleSection lifecycle = repository.cached(player).orElseThrow()
                    .lifecycle().value();
            check(PlayerProfileDeathEscrowStore.readBatches(lifecycle).isEmpty(),
                    "settled escrow has no pending payload");
            final UUID sessionPlayer = UUID.fromString(
                    "00000000-0000-0000-0000-000000005045");
            final long oldSession = service.join(sessionPlayer, "EscrowSession")
                    .toCompletableFuture().join().sessionGeneration();
            check(store.depositForSession(sessionPlayer, oldSession, "death:session",
                            List.of("session-item"), 4_000L).toCompletableFuture().join().applied(),
                    "current session may create a death escrow");
            service.quit(sessionPlayer, oldSession).toCompletableFuture().join();
            final long newSession = service.join(sessionPlayer, "EscrowSession")
                    .toCompletableFuture().join().sessionGeneration();
            check(newSession > oldSession, "rejoin receives a distinct monotonic session token");
            expect(IllegalStateException.class, () -> store.depositForSession(
                    sessionPlayer, oldSession, "death:stale", List.of("stale-item"), 4_100L)
                    .toCompletableFuture().join());
            check(store.pending(sessionPlayer).toCompletableFuture().join().size() == 1,
                    "stale death completion cannot mutate the new session escrow");

            final String listener = Files.readString(Path.of(
                    "src/main/java/hu/taliann/icesmp/listeners/RelicPvpTransferListener.java"));
            final int lethal = listener.indexOf("public void onLethalDamage");
            final int barrier = listener.indexOf("event.setCancelled(true);", lethal);
            final int durableWrite = listener.indexOf("escrowStore.depositForSession", lethal);
            final int replayDeath = listener.indexOf("replayFatalDamage(victim, damageSource)", lethal);
            check(lethal > 0 && barrier > lethal && durableWrite > barrier && replayDeath > durableWrite,
                    "fatal damage must cross the durable write barrier before death is replayed");
            final int deathHandler = listener.indexOf("public void onPlayerDeath");
            final int durableRemoval = listener.indexOf("removeDurablyEscrowedDrops", deathHandler);
            check(durableRemoval > deathHandler
                            && listener.contains("event.getDrops().removeIf(drop -> hasDeliveryMarker")
                            && !listener.contains("event.getItemsToKeep().add(drop)"),
                    "drop removal must only consume a previously committed escrow marker");
            check(listener.contains("a normál dropút folytatódik")
                            && listener.contains("clearDeliveryMarkersNow(victim, receiptId)"),
                    "durable write failure must leave the normal death-drop path authoritative");
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile death escrow regression suite passed. assertions="
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
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (!expected.isInstance(cause)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface
    private interface Throwing { void run() throws Exception; }
}
