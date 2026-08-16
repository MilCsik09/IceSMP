package hu.taliann.icesmp.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/** Narrow WAL for one-player itemization inventory snapshots; not a general transaction framework. */
public final class ItemMutationJournal {

    private static final int MAX_PENDING_OPERATIONS = 256;
    private static final int MAX_ENCODED_SLOT_LENGTH = 1_048_576;
    private static final Set<String> OPERATION_TYPES = Set.of("REROLL", "ASCEND", "SALVAGE");

    public record Entry(UUID operationId, UUID playerId, String type, UUID itemId,
                        List<String> beforeInventory, List<String> afterInventory,
                        long createdAt) {
        public Entry {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(itemId, "itemId");
            type = Objects.requireNonNull(type, "type").trim();
            beforeInventory = List.copyOf(beforeInventory);
            afterInventory = List.copyOf(afterInventory);
            if (!OPERATION_TYPES.contains(type) || createdAt < 0L
                    || beforeInventory.size() != afterInventory.size()
                    || beforeInventory.size() > 64
                    || beforeInventory.stream().anyMatch(ItemMutationJournal::invalidSlot)
                    || afterInventory.stream().anyMatch(ItemMutationJournal::invalidSlot)) {
                throw new IllegalArgumentException("invalid item mutation inventory snapshot");
            }
        }
    }

    private final File file;
    private final Logger logger;
    private final JavaPlugin plugin;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private volatile boolean healthy = true;

    public ItemMutationJournal(final JavaPlugin plugin, final File file, final Logger logger) {
        this.plugin = plugin;
        this.file = file;
        this.logger = logger;
        YamlStore.registerCriticalWrite(file);
    }

    public synchronized void load() {
        entries.clear();
        if (!file.exists()) return;
        try {
            final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
            final ConfigurationSection root = yaml.getConfigurationSection("operations");
            if (root == null) return;
            if (root.getKeys(false).size() > MAX_PENDING_OPERATIONS) {
                throw new IllegalStateException("too many pending item mutations");
            }
            final Set<UUID> pendingPlayers = new HashSet<>();
            for (final String rawId : root.getKeys(false)) {
                final UUID id = UUID.fromString(rawId);
                final String path = "operations." + rawId + '.';
                final Entry entry = new Entry(id,
                        UUID.fromString(yaml.getString(path + "player", "")),
                        yaml.getString(path + "type", ""),
                        UUID.fromString(yaml.getString(path + "item", "")),
                        yaml.getStringList(path + "before"), yaml.getStringList(path + "after"),
                        yaml.getLong(path + "created-at"));
                if (!pendingPlayers.add(entry.playerId())) {
                    throw new IllegalStateException("multiple pending mutations for one player");
                }
                entries.put(id, entry);
            }
        } catch (final Exception corrupt) {
            healthy = false;
            YamlStore.failCorrupt(file, logger, "Item mutation journal: " + corrupt.getMessage());
        }
    }

    public boolean isHealthy() { return healthy && !YamlStore.isLoadFailed(file); }

    public synchronized List<Entry> entriesFor(final UUID playerId) {
        return entries.values().stream().filter(entry -> entry.playerId().equals(playerId)).toList();
    }

    public CompletionStage<Boolean> prepare(final Entry entry) {
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                synchronized (this) {
                    if (!isHealthy() || entries.containsKey(entry.operationId())) {
                        result.complete(false);
                        return;
                    }
                    if (entries.size() >= MAX_PENDING_OPERATIONS
                            || entries.values().stream().anyMatch(existing ->
                            existing.playerId().equals(entry.playerId()))) {
                        result.complete(false);
                        return;
                    }
                    entries.put(entry.operationId(), entry);
                    if (flush()) result.complete(true);
                    else {
                        entries.remove(entry.operationId());
                        result.complete(false);
                    }
                }
            });
        } catch (final RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    public CompletionStage<Boolean> complete(final UUID operationId) {
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                synchronized (this) {
                    final Entry removed = entries.remove(operationId);
                    if (removed == null) result.complete(true);
                    else if (flush()) result.complete(true);
                    else {
                        entries.put(operationId, removed);
                        result.complete(false);
                    }
                }
            });
        } catch (final RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    private boolean flush() {
        if (!isHealthy()) return false;
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Entry entry : entries.values()) {
            final String path = "operations." + entry.operationId() + '.';
            yaml.set(path + "player", entry.playerId().toString());
            yaml.set(path + "type", entry.type());
            yaml.set(path + "item", entry.itemId().toString());
            yaml.set(path + "before", entry.beforeInventory());
            yaml.set(path + "after", entry.afterInventory());
            yaml.set(path + "created-at", entry.createdAt());
        }
        try {
            YamlStore.saveAtomic(file, yaml);
            return true;
        } catch (final IOException failure) {
            logger.severe("Item mutation journal save failed: " + failure.getMessage());
            return false;
        }
    }

    public static List<String> encodeInventory(final ItemStack[] contents) {
        final ArrayList<String> encoded = new ArrayList<>(contents.length);
        for (final ItemStack item : contents) {
            encoded.add(item == null || item.getType().isAir() ? "-"
                    : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        return List.copyOf(encoded);
    }

    public static ItemStack[] decodeInventory(final List<String> encoded) {
        final ItemStack[] contents = new ItemStack[encoded.size()];
        for (int slot = 0; slot < encoded.size(); slot++) {
            final String value = encoded.get(slot);
            contents[slot] = "-".equals(value) ? null
                    : ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        }
        return contents;
    }

    private static boolean invalidSlot(final String encoded) {
        return encoded == null || encoded.length() > MAX_ENCODED_SLOT_LENGTH
                || (!"-".equals(encoded) && encoded.isBlank());
    }
}
