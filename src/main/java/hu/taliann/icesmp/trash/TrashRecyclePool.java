package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Durable exact-item recycle pool; substitution happens only after the normal identity roll. */
public final class TrashRecyclePool implements PersistentStore {

    private static final int SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;
    private final File file;
    private final Map<String, ArrayDeque<ItemStack>> pool = new LinkedHashMap<>();

    public TrashRecyclePool(final JavaPlugin plugin, final TrashCatalog catalog,
                            final TrashItemFactory itemFactory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.file = new File(plugin.getDataFolder(), "trash-recycle.yml");
    }

    @Override
    public synchronized void load() {
        pool.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(file, plugin.getLogger());
        if (!file.exists()) return;
        if (yaml.getInt("schema-version", 0) != SCHEMA_VERSION) {
            YamlStore.failCorrupt(file, plugin.getLogger(),
                    "trash recycle schema-version must be exactly " + SCHEMA_VERSION);
        }
        final ConfigurationSection stored = yaml.getConfigurationSection("pool");
        if (stored == null) return;
        for (final String id : stored.getKeys(false)) {
            final TrashDefinition definition = catalog.find(id).orElse(null);
            if (definition == null || definition.internalKind().isInert()) {
                YamlStore.failCorrupt(file, plugin.getLogger(),
                        "ismeretlen vagy nem recycle-eligible Trash identity: " + id);
            }
            final List<?> serialized = stored.getList(id);
            if (serialized == null) {
                YamlStore.failCorrupt(file, plugin.getLogger(), "a recycle entry nem lista: " + id);
            }
            final ArrayDeque<ItemStack> items = new ArrayDeque<>();
            for (final Object value : serialized) {
                if (!(value instanceof ItemStack item)
                        || item.getAmount() != 1
                        || !itemFactory.isBaseIdentity(item)
                        || !id.equals(itemFactory.idOf(item).orElse(null))) {
                    YamlStore.failCorrupt(file, plugin.getLogger(),
                            "érvénytelen exact recycle instance: " + id);
                }
                items.addLast(item.clone());
            }
            if (!items.isEmpty()) pool.put(id, items);
        }
    }

    @Override
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        for (final Map.Entry<String, ArrayDeque<ItemStack>> entry : pool.entrySet()) {
            final List<ItemStack> copies = new ArrayList<>(entry.getValue().size());
            entry.getValue().forEach(item -> copies.add(item.clone()));
            yaml.set("pool." + entry.getKey(), copies);
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException failure) {
            throw new IllegalStateException("Trash recycle pool mentése sikertelen", failure);
        }
    }

    /** Stores sold special units; mundane/story base items deliberately leave no exact instance. */
    public synchronized void offer(final ItemStack sold, final int amount) {
        final String id = itemFactory.idOf(sold).orElse(null);
        if (id == null || amount <= 0) return;
        final TrashDefinition definition = catalog.require(id);
        if (definition.internalKind().isInert()) return;
        final ArrayDeque<ItemStack> instances = pool.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        for (int index = 0; index < amount; index++) {
            final ItemStack unit = sold.clone();
            unit.setAmount(1);
            instances.addLast(unit);
        }
    }

    public synchronized Optional<ItemStack> take(final String id) {
        final ArrayDeque<ItemStack> instances = pool.get(id);
        if (instances == null) return Optional.empty();
        final ItemStack item = instances.pollFirst();
        if (instances.isEmpty()) pool.remove(id);
        return Optional.ofNullable(item == null ? null : item.clone());
    }

    public synchronized int pooledCount() {
        return pool.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    synchronized int pooledCount(final String id) {
        final ArrayDeque<ItemStack> instances = pool.get(id);
        return instances == null ? 0 : instances.size();
    }
}
