package hu.taliann.icesmp.trash;

import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/** Shared acquisition pipeline for fishing, mob and ambient sources. */
public final class TrashLootService {

    private final TrashCatalog catalog;
    private final TrashLootSelector selector;
    private final TrashItemFactory itemFactory;
    private final TrashHistoryService history;
    private final TrashRecyclePool recyclePool;
    private final EnumMap<TrashLootSource, LongAdder> generated = new EnumMap<>(TrashLootSource.class);
    private final LongAdder recycled = new LongAdder();
    private static final SpawnBoost NO_BOOST = new SpawnBoost(1, 0);
    private volatile SpawnBoost spawnBoost = NO_BOOST;

    public record SpawnBoost(int multiplier, long expiresAtNanos) {
        public long remainingSeconds() {
            return multiplier == 1 ? 0 : Math.max(0L, (expiresAtNanos - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
        }
    }

    public SpawnBoost spawnBoost() {
        final SpawnBoost current = spawnBoost;
        return current.multiplier() == 1 || current.expiresAtNanos() - System.nanoTime() <= 0
                ? NO_BOOST : current;
    }

    public void boostSpawns(final int multiplier, final int seconds) {
        if (multiplier < 1 || multiplier > 100 || seconds < 1 || seconds > 3600)
            throw new IllegalArgumentException("Spawn boost bounds");
        spawnBoost = new SpawnBoost(multiplier, System.nanoTime() + seconds * 1_000_000_000L);
    }

    public void resetSpawnBoost() { spawnBoost = NO_BOOST; }

    public ItemStack createFresh(final String id, final int amount, final TrashLootSource source) {
        return history.markOrigin(itemFactory.create(id, amount), source);
    }

    public TrashLootService(final TrashCatalog catalog,
                            final TrashLootSelector selector, final TrashItemFactory itemFactory,
                            final TrashHistoryService history,
                            final TrashRecyclePool recyclePool) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.history = Objects.requireNonNull(history, "history");
        this.recyclePool = Objects.requireNonNull(recyclePool, "recyclePool");
        for (final TrashLootSource source : TrashLootSource.values()) generated.put(source, new LongAdder());
    }

    public Optional<ItemStack> roll(final TrashLootSource source, final Set<TrashContext> contexts) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final TrashLootTuning tuning = catalog.lootTuning();
        if (random.nextDouble() >= Math.min(1.0D, tuning.chance(source) * spawnBoost().multiplier())) return Optional.empty();
        final TrashLootSelector.Selection selection = selector.select(source, contexts, random::nextDouble);
        ItemStack result = null;
        if (random.nextDouble() < tuning.recycleSubstitutionChance()) {
            result = recyclePool.take(selection.definition().id()).orElse(null);
            if (result != null) recycled.increment();
        }
        if (result == null) {
            result = createFresh(selection.definition().id(), 1, source);
        }
        generated.get(source).increment();
        return Optional.of(result);
    }

    public Telemetry telemetry() {
        final EnumMap<TrashLootSource, Long> counts = new EnumMap<>(TrashLootSource.class);
        generated.forEach((source, count) -> counts.put(source, count.sum()));
        return new Telemetry(Map.copyOf(counts), recycled.sum(), recyclePool.pooledCount());
    }

    public record Telemetry(Map<TrashLootSource, Long> generated, long recycled,
                            int recyclePoolSize) { }
}
