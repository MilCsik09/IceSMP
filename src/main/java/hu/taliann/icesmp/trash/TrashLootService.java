package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.managers.ConfigManager;
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

    private final ConfigManager configManager;
    private final TrashCatalog catalog;
    private final TrashLootSelector selector;
    private final TrashItemFactory itemFactory;
    private final TrashRecyclePool recyclePool;
    private final EnumMap<TrashLootSource, LongAdder> generated = new EnumMap<>(TrashLootSource.class);
    private final LongAdder recycled = new LongAdder();

    public TrashLootService(final ConfigManager configManager, final TrashCatalog catalog,
                            final TrashLootSelector selector, final TrashItemFactory itemFactory,
                            final TrashRecyclePool recyclePool) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.recyclePool = Objects.requireNonNull(recyclePool, "recyclePool");
        for (final TrashLootSource source : TrashLootSource.values()) generated.put(source, new LongAdder());
    }

    public Optional<ItemStack> roll(final TrashLootSource source, final Set<TrashContext> contexts) {
        if (!configManager.getBoolean("trash-runtime.enabled", true)) return Optional.empty();
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final TrashLootTuning tuning = catalog.lootTuning();
        if (random.nextDouble() >= tuning.chance(source)) return Optional.empty();
        final TrashLootSelector.Selection selection = selector.select(source, contexts, random::nextDouble);
        ItemStack result = null;
        if (random.nextDouble() < tuning.recycleSubstitutionChance()) {
            result = recyclePool.take(selection.definition().id()).orElse(null);
            if (result != null) recycled.increment();
        }
        if (result == null) result = itemFactory.create(selection.definition().id(), 1);
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
