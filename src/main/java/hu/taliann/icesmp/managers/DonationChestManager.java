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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Community donation chest (Adomány-láda): a server-wide, shared item pool. Anyone can donate
 * the item held in their main hand, and anyone can take any entry — pure gifting, no prices, no
 * currency. Entries persist to donations.yml.
 *
 * <p>Thread-safety: {@link #entries} is a {@link ConcurrentHashMap} so plain reads (GUI paging,
 * lore) are safe from any region thread without locking; every compound mutation (donate / take)
 * is {@code synchronized} so a check-then-act (capacity caps, "already taken?") never races —
 * mirrors {@link MarketManager}'s listings map.</p>
 */
public final class DonationChestManager implements PersistentStore {

    /** A single donated item: who gave it, what it is, and when. */
    public record DonationEntry(UUID id, UUID donorId, String donorName, ItemStack item, long donatedAt) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Map<UUID, DonationEntry> entries = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean saveScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public DonationChestManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "donations.yml");
        plugin.getDataFolder().mkdirs();
    }

    public boolean isEnabled() {
        return configManager.getBoolean("donation-chest.enabled", true);
    }

    public int getMaxItems() {
        return Math.max(1, configManager.getInt("donation-chest.max-items", 270));
    }

    /** The per-donor cap on LIVE (not-yet-taken) entries; {@code <= 0} means unlimited. */
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
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
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
            plugin.getLogger().info("Loaded " + entries.size() + " donation chest entr(y/ies).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load donations.yml: " + exception.getMessage());
        }
    }

    /** Debounce-olt mentés: a donate/take a hívó régió-szálán nem blokkolhat lemez-I/O-n. */
    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final DonationEntry entry : entries.values()) {
            final String basePath = "entries." + entry.id();
            yaml.set(basePath + ".donor-id", entry.donorId().toString());
            yaml.set(basePath + ".donor-name", entry.donorName());
            yaml.set(basePath + ".item", entry.item());
            yaml.set(basePath + ".donated-at", entry.donatedAt());
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save donations.yml: " + exception.getMessage());
        }
    }

    /** Every entry, newest donation first — snapshot read, safe from any region thread. */
    public List<DonationEntry> getEntriesSorted() {
        return entries.values().stream()
                .sorted(Comparator.comparingLong(DonationEntry::donatedAt).reversed())
                .toList();
    }

    public int count() {
        return entries.size();
    }

    private long countLiveOf(final UUID donorId) {
        return entries.values().stream().filter(entry -> entry.donorId().equals(donorId)).count();
    }

    /**
     * Donates the item currently held in {@code donor}'s main hand (the whole stack): validates
     * the chest is enabled and the hand isn't empty, enforces the total-capacity and
     * per-donor-live-entries caps, then records the entry and clears the hand — the hand is
     * ONLY cleared once the entry is committed, so a rejected donation never loses the item.
     *
     * @param donor the donating player
     * @return null on success, otherwise a message key describing why it failed
     */
    public synchronized String donateHeldItem(final Player donor) {
        if (!isEnabled()) {
            return "donation-chest-disabled";
        }

        final ItemStack held = donor.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return "donation-no-item";
        }

        if (entries.size() >= getMaxItems()) {
            return "donation-chest-full";
        }

        final int maxPerPlayer = getMaxPerPlayer();
        if (maxPerPlayer > 0 && countLiveOf(donor.getUniqueId()) >= maxPerPlayer) {
            return "donation-per-player-limit";
        }

        final UUID id = UUID.randomUUID();
        entries.put(id, new DonationEntry(id, donor.getUniqueId(), donor.getName(), held.clone(),
                System.currentTimeMillis()));
        donor.getInventory().setItemInMainHand(null);
        requestSave();
        return null;
    }

    /**
     * Atomically removes and returns an entry's item — the single point where "take" is
     * committed, so two players clicking the same slot at once cannot both receive the item
     * (the second call simply finds it already gone and returns null).
     *
     * @param id the entry id to take
     * @return the donated item, or null if it was already taken (or never existed)
     */
    public synchronized ItemStack takeEntry(final UUID id) {
        final DonationEntry entry = id == null ? null : entries.remove(id);
        if (entry == null) {
            return null;
        }
        requestSave();
        return entry.item();
    }
}
