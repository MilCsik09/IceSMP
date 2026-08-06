package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared donation chest. Every mutation is serialized, while inventory ownership
 * is changed only on the donating/taking player's own entity thread.
 */
public final class DonationChestManager implements PersistentStore {

    /** Immutable entry wrapper; ItemStack access always returns a clone. */
    public record DonationEntry(UUID id, UUID donorId, String donorName,
                                ItemStack item, long donatedAt) {
        public DonationEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(donorId, "donorId");
            donorName = donorName == null ? "?" : donorName;
            if (isEmpty(item)) {
                throw new IllegalArgumentException("donation item is empty");
            }
            item = item.clone();
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }

    @FunctionalInterface
    private interface ItemWriter {
        void write(ItemStack item);
    }

    private record ItemSource(java.util.function.Supplier<ItemStack> reader,
                              ItemWriter writer) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Map<UUID, DonationEntry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public DonationChestManager(final JavaPlugin plugin,
                                final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "donations.yml");
        plugin.getDataFolder().mkdirs();
    }

    public boolean isEnabled() {
        return configManager.getBoolean("donation-chest.enabled", true);
    }

    public int getMaxItems() {
        return Math.max(1,
                configManager.getInt("donation-chest.max-items", 270));
    }

    /** Per-donor cap on live entries; <= 0 means unlimited. */
    public int getMaxPerPlayer() {
        return configManager.getInt("donation-chest.max-per-player", 27);
    }

    @Override
    public void load() {
        entries.clear();
        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
            final ConfigurationSection section = yaml.getConfigurationSection("entries");
            if (section != null) {
                for (final String idKey : section.getKeys(false)) {
                    try {
                        final UUID id = UUID.fromString(idKey);
                        final UUID donorId = UUID.fromString(section.getString(idKey + ".donor-id", ""));
                        final ItemStack item = section.getItemStack(idKey + ".item");
                        if (item == null || item.getType() == Material.AIR) {
                            continue;
                        }
                        entries.put(id, new DonationEntry(
                                id,
                                donorId,
                                section.getString(idKey + ".donor-name", "?"),
                                item,
                                section.getLong(idKey + ".donated-at", System.currentTimeMillis())
                        ));
                    } catch (final IllegalArgumentException ignored) {
                        // Skip malformed entries rather than discarding the whole chest.
                    }
                }
            }
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Loaded " + entries.size() + " donation chest entr(y/ies).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load donations.yml: " + exception.getMessage());
        }
        for (final String idKey : section.getKeys(false)) {
            try {
                final UUID id = UUID.fromString(idKey);
                final UUID donorId = UUID.fromString(
                        section.getString(idKey + ".donor-id", ""));
                final ItemStack item =
                        section.getItemStack(idKey + ".item");
                if (isEmpty(item)) {
                    continue;
                }
                entries.put(id, new DonationEntry(
                        id,
                        donorId,
                        section.getString(idKey + ".donor-name", "?"),
                        item,
                        section.getLong(idKey + ".donated-at",
                                System.currentTimeMillis())
                ));
            } catch (final IllegalArgumentException malformed) {
                plugin.getLogger().warning(
                        "Skipping malformed donation entry "
                                + idKey + ": " + malformed.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + entries.size()
                + " donation chest entr(y/ies).");
    }

    private void requestSave() {
        if (!saveScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
        } catch (final RuntimeException unavailable) {
            saveScheduled.set(false);
            throw unavailable;
        }
    }

    @Override
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final DonationEntry entry : entries.values()) {
            final String basePath = "entries." + entry.id();
            yaml.set(basePath + ".donor-id",
                    entry.donorId().toString());
            yaml.set(basePath + ".donor-name", entry.donorName());
            yaml.set(basePath + ".item", entry.item());
            yaml.set(basePath + ".donated-at", entry.donatedAt());
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException failure) {
            plugin.getLogger().severe(
                    "Failed to save donations.yml: "
                            + failure.getMessage());
            throw new UncheckedIOException(
                    "Failed to save donations.yml", failure);
        }
    }

    public List<DonationEntry> getEntriesSorted() {
        return entries.values().stream()
                .sorted(Comparator.comparingLong(
                        DonationEntry::donatedAt).reversed())
                .toList();
    }

    public int count() {
        return entries.size();
    }

    private long countLiveOf(final UUID donorId) {
        return entries.values().stream()
                .filter(entry -> entry.donorId().equals(donorId))
                .count();
    }

    /** Deposits part or all of the live cursor stack. */
    public String donateCursor(final Player donor, final ItemStack expected,
                               final int amount) {
        return donateFromSource(donor, expected, amount, new ItemSource(
                donor::getItemOnCursor,
                donor::setItemOnCursor));
    }

    /** Deposits part or all of a normal player-inventory/hotbar slot. */
    public String donateInventorySlot(final Player donor, final int slot,
                                      final ItemStack expected, final int amount) {
        if (slot < 0 || slot >= 36) {
            return "donation-invalid-source";
        }
        return donateFromSource(donor, expected, amount, new ItemSource(
                () -> donor.getInventory().getItem(slot),
                item -> donor.getInventory().setItem(slot, item)));
    }

    /** Deposits part or all of the off-hand stack. */
    public String donateOffHand(final Player donor, final ItemStack expected,
                                final int amount) {
        return donateFromSource(donor, expected, amount, new ItemSource(
                () -> donor.getInventory().getItemInOffHand(),
                item -> donor.getInventory().setItemInOffHand(
                        isEmpty(item) ? new ItemStack(Material.AIR) : item)));
    }

    /** Compatibility entry point for the old hopper button. */
    public String donateHeldItem(final Player donor) {
        final int slot = donor.getInventory().getHeldItemSlot();
        final ItemStack held = donor.getInventory().getItem(slot);
        return donateInventorySlot(donor, slot, cloneItem(held),
                isEmpty(held) ? 0 : held.getAmount());
    }

    /**
     * Commits one donation and removes exactly the committed amount from its
     * source. If source mutation or task admission fails, the entry is rolled back.
     */
    private synchronized String donateFromSource(final Player donor,
                                                 final ItemStack expected,
                                                 final int requestedAmount,
                                                 final ItemSource source) {
        if (!isEnabled()) {
            return "donation-chest-disabled";
        }
        final ItemStack current = cloneItem(source.reader().get());
        if (!sameItem(current, expected)) {
            return "donation-invalid-source";
        }
        if (isEmpty(current) || requestedAmount <= 0) {
            return "donation-no-item";
        }
        if (requestedAmount > current.getAmount()) {
            return "donation-invalid-source";
        }
        if (entries.size() >= getMaxItems()) {
            return "donation-chest-full";
        }
        final int maxPerPlayer = getMaxPerPlayer();
        if (maxPerPlayer > 0
                && countLiveOf(donor.getUniqueId()) >= maxPerPlayer) {
            return "donation-per-player-limit";
        }

        final ItemStack donated = current.clone();
        donated.setAmount(requestedAmount);
        final ItemStack remaining = current.clone();
        remaining.setAmount(current.getAmount() - requestedAmount);

        final UUID id = UUID.randomUUID();
        final DonationEntry entry = new DonationEntry(id,
                donor.getUniqueId(), donor.getName(), donated,
                System.currentTimeMillis());
        entries.put(id, entry);
        try {
            source.writer().write(
                    remaining.getAmount() <= 0 ? null : remaining);
            requestSave();
            return null;
        } catch (final RuntimeException failure) {
            entries.remove(id, entry);
            try {
                source.writer().write(current);
            } catch (final RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    /**
     * Atomically claims an entry. A failed save-task admission restores the entry.
     */
    public synchronized ItemStack takeEntry(final UUID id) {
        final DonationEntry entry = id == null ? null : entries.remove(id);
        if (entry == null) {
            return null;
        }
        try {
            requestSave();
            return entry.item();
        } catch (final RuntimeException failure) {
            entries.putIfAbsent(entry.id(), entry);
            throw failure;
        }
    }

    public static String defaultErrorFor(final String errorKey) {
        return switch (errorKey == null ? "" : errorKey) {
            case "donation-chest-disabled" ->
                    "&cAz adomány-láda jelenleg ki van kapcsolva.";
            case "donation-no-item" ->
                    "&cNincs adományozható tárgy a kiválasztott helyen.";
            case "donation-chest-full" ->
                    "&cAz adomány-láda megtelt — próbáld később.";
            case "donation-per-player-limit" ->
                    "&cElérted a saját adomány-limitedet "
                            + "(&f{limit} tétel&c).";
            case "donation-invalid-source" ->
                    "&cA tárgy közben megváltozott; próbáld újra.";
            default -> "&cAz adományozás nem sikerült.";
        };
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean sameItem(final ItemStack first, final ItemStack second) {
        if (isEmpty(first) || isEmpty(second)) {
            return isEmpty(first) && isEmpty(second);
        }
        return first.equals(second);
    }

    private static boolean isEmpty(final ItemStack item) {
        return item == null || item.getType().isAir()
                || item.getAmount() <= 0;
    }
}
