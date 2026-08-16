package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.itemization.ItemRarity;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.itemization.LootDiversityState;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Restart durability, idempotency and hard-bound regressions for soft loot pity evidence. */
public final class PlayerProfileLootDiversityStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileLootDiversityStoreRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-loot-diversity-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001203");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileLootDiversityStore store = new PlayerProfileLootDiversityStore();
            final LootDiversityState.Drop first = new LootDiversityState.Drop(
                    UUID.fromString("00000000-0000-0000-0000-000000002301"),
                    "durable_sword", ItemRarity.RARE,
                    ItemTemplate.Slot.MAIN_HAND, ItemTemplate.Family.WEAPON);
            check(store.record(player, first).toCompletableFuture().join(),
                    "first delivered item appends diversity evidence");
            check(!store.record(player, first).toCompletableFuture().join(),
                    "replayed item receipt is an idempotent no-op");

            for (int index = 0; index < LootDiversityState.MAX_DROPS + 5; index++) {
                final LootDiversityState.Drop next = new LootDiversityState.Drop(
                        new UUID(0L, 50_000L + index), "durable_armor_" + index,
                        ItemRarity.EPIC, ItemTemplate.Slot.CHEST, ItemTemplate.Family.ARMOR);
                check(store.record(player, next).toCompletableFuture().join(),
                        "unique loot receipt commits");
            }
            check(store.current(player).recentDrops().size() == LootDiversityState.MAX_DROPS,
                    "runtime view retains exactly the bounded tail");
            final UUID newest = store.current(player).recentDrops()
                    .get(LootDiversityState.MAX_DROPS - 1).itemId();
            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            final LootDiversityState durable = store.current(player);
            check(durable.recentDrops().size() == LootDiversityState.MAX_DROPS,
                    "restart preserves the bounded pity window");
            check(durable.recentDrops().get(LootDiversityState.MAX_DROPS - 1)
                            .itemId().equals(newest),
                    "restart preserves newest loot ordering");
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (final Exception ignored) {
                    }
                });
            }
        }
        System.out.println("PlayerProfile loot diversity regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
