package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.gui.CrateSpinGUI;
import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Native crate system (replaces the CrazyCrates soft-dependency):
 * a crate is a real block location tagged with a crate-id from
 * {@code config/crates.yml}; a themed key item (see {@link CrateKeyFactory})
 * opens it for a weighted-random item reward. Keys are bought with a currency
 * sink — {@link #buyKey} deducts atomically and the money is never re-credited.
 *
 * <p>Threading (Folia): block-location keys are plain {@code world;x;y;z}
 * strings so lookups never need the actual {@link World} object. {@link #open}
 * and {@link #buyKey} always run on the acting player's own region thread
 * (fired from a block-interact event or a command), matching the block/command
 * they were invoked from — no cross-region hop is needed for the inventory
 * mutation itself. Command-type rewards are dispatched on the GLOBAL region
 * scheduler as the console, since a reward command may touch state outside the
 * opener's own region.
 */
public final class CrateManager implements PersistentStore {

    /** One weighted entry in a crate's loot table: either an item or a console command. */
    public record RewardEntry(double weight, Material material, int amount, String command, String description) {
    }

    /** A reward entry's human-readable odds, for {@code /crate info}. */
    public record RewardOdds(String description, double percent) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final CrateKeyFactory crateKeyFactory;
    private final MessageManager messageManager;
    private final File storageFile;

    /** world;x;y;z -> crate-id. */
    private final Map<String, String> crateBlocks = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();

    public CrateManager(final JavaPlugin plugin, final ConfigManager configManager, final CurrencyManager currencyManager,
                        final CrateKeyFactory crateKeyFactory, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.crateKeyFactory = crateKeyFactory;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "crates-data.yml");
    }

    // ==================== crate-block registry ====================

    /** Marks the block at {@code location} as a crate of {@code crateId}. False if the id is unknown. */
    public boolean setCrate(final Location location, final String crateId) {
        if (location == null || crateId == null || !crateIds().contains(crateId)) {
            return false;
        }
        crateBlocks.put(locationKey(location), crateId);
        persist();
        return true;
    }

    /** Un-registers the crate block at {@code location}. False if it wasn't one. */
    public boolean removeCrate(final Location location) {
        if (location == null || crateBlocks.remove(locationKey(location)) == null) {
            return false;
        }
        persist();
        return true;
    }

    /** The crate-id registered at {@code location}, or null if it isn't a crate block. */
    public String crateAt(final Location location) {
        return location == null ? null : crateBlocks.get(locationKey(location));
    }

    /** All registered crate blocks as human-readable "id @ world (x, y, z)" descriptors. */
    public List<String> listCrates() {
        final List<String> entries = new ArrayList<>();
        for (final Map.Entry<String, String> entry : crateBlocks.entrySet()) {
            entries.add(entry.getValue() + " @ " + entry.getKey().replace(";", ", "));
        }
        return entries;
    }

    /** The configured crate ids (config/crates.yml crates.&lt;id&gt;). */
    public List<String> crateIds() {
        final ConfigurationSection section = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection("crates");
        return section == null ? List.of() : List.copyOf(section.getKeys(false));
    }

    private static String locationKey(final Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    // ==================== crate metadata ====================

    public String displayName(final String crateId) {
        return configManager.getString("crates." + crateId + ".display-name", crateId);
    }

    public CurrencyType keyPriceCurrency(final String crateId) {
        final CurrencyType currencyType = CurrencyType.fromInput(configManager.getString("crates." + crateId + ".key-price.currency", "NEUTRAL"));
        return currencyType == null ? CurrencyType.NEUTRAL : currencyType;
    }

    public double keyPriceAmount(final String crateId) {
        return Math.max(0.0D, configManager.getDouble("crates." + crateId + ".key-price.amount", 0.0D));
    }

    /** The crate's loot table entries with their roll odds, for {@code /crate info}. */
    public List<RewardOdds> rewardOdds(final String crateId) {
        final List<RewardEntry> rewards = loadRewards(crateId);
        final double total = rewards.stream().mapToDouble(RewardEntry::weight).sum();
        if (total <= 0.0D) {
            return List.of();
        }
        final List<RewardOdds> odds = new ArrayList<>();
        for (final RewardEntry reward : rewards) {
            odds.add(new RewardOdds(describeReward(reward), reward.weight() / total * 100.0D));
        }
        return odds;
    }

    // ==================== gameplay ====================

    /**
     * Buys {@code amount} keys for {@code crateId}: atomically deducts the configured
     * price (a pure sink — the money is never credited anywhere) and hands over the
     * key items. Returns null on success, or a {@code messages.} error key.
     */
    public String buyKey(final Player player, final String crateId, final int amount) {
        if (!crateIds().contains(crateId)) {
            return "crate-unknown";
        }
        final int count = Math.max(1, amount);
        final double totalPrice = keyPriceAmount(crateId) * count;
        // Atomic check-and-deduct (never getBalance()+setBalance()) so two concurrent
        // buys from the same player can't both pass a stale balance check.
        if (totalPrice > 0.0D && !currencyManager.deductFromBalance(player.getUniqueId(), keyPriceCurrency(crateId), totalPrice)) {
            return "crate-insufficient-funds";
        }
        giveKeys(player, crateId, count);
        return null;
    }

    /** Admin/no-cost key grant (e.g. {@code /crate give}) — bypasses the currency sink entirely. */
    public void giveKeys(final Player player, final String crateId, final int amount) {
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            final int chunk = Math.min(64, remaining);
            final ItemStack keys = crateKeyFactory.createKey(crateId, chunk);
            player.getInventory().addItem(keys).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            remaining -= chunk;
        }
    }

    /**
     * Opens the crate for {@code player}: weighted-random roll, reward grant, sound +
     * particle feedback and a chat message. Returns false if the crate has no (valid)
     * reward table configured.
     *
     * <p>The roll and the reward grant above always happen immediately and are
     * unconditional — everything below this point is cosmetics only. When
     * {@code crates-settings.spin-animation} is on, the sound/particle/chat feedback is
     * handed to {@link CrateSpinGUI} as a callback and fires only once the reel animation
     * lands on the (already-decided) reward; otherwise it fires immediately, as before.
     */
    public boolean open(final Player player, final String crateId) {
        final List<RewardEntry> rewards = loadRewards(crateId);
        final double totalWeight = rewards.stream().mapToDouble(RewardEntry::weight).sum();
        if (rewards.isEmpty() || totalWeight <= 0.0D) {
            return false;
        }

        final RewardEntry picked = pickWeighted(rewards, totalWeight);
        grantReward(player, picked);

        final Runnable feedback = () -> {
            playFeedback(player);
            player.sendMessage(messageManager.get("crate-opened",
                    "&6[Láda] &eKinyitottad: &f%s &e— nyeremény: &a%s",
                    displayName(crateId), describeReward(picked)));
        };

        if (spinAnimationEnabled()) {
            CrateSpinGUI.open(plugin, player, displayName(crateId), rewards, picked, feedback);
        } else {
            feedback.run();
        }
        return true;
    }

    /** Global switch for the cosmetic reel-spin reveal — config/crates.yml crates-settings.spin-animation. */
    private boolean spinAnimationEnabled() {
        return configManager.getBoolean("crates-settings.spin-animation", true);
    }

    private RewardEntry pickWeighted(final List<RewardEntry> rewards, final double totalWeight) {
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        for (final RewardEntry reward : rewards) {
            roll -= reward.weight();
            if (roll < 0.0D) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private void grantReward(final Player player, final RewardEntry reward) {
        if (reward.material() != null) {
            final ItemStack itemStack = new ItemStack(reward.material(), Math.max(1, reward.amount()));
            player.getInventory().addItem(itemStack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            return;
        }
        if (reward.command() != null && !reward.command().isBlank()) {
            final String dispatched = reward.command().replace("{player}", player.getName());
            // Console command reward: run on the GLOBAL region scheduler — the command may
            // touch state outside the opener's own region (never Bukkit.getScheduler()).
            Bukkit.getServer().getGlobalRegionScheduler().run(plugin, task ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dispatched));
        }
    }

    private void playFeedback(final Player player) {
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.4D, 0.5D, 0.4D, 0.1D);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
    }

    /** Human-readable label for a reward entry — also used by {@link CrateSpinGUI} for the reel icons. */
    public static String describeReward(final RewardEntry reward) {
        if (reward.description() != null && !reward.description().isBlank()) {
            return reward.description();
        }
        if (reward.material() != null) {
            return humanize(reward.material()) + " x" + Math.max(1, reward.amount());
        }
        if (reward.command() != null) {
            return "&dParancs-jutalom";
        }
        return "?";
    }

    private static String humanize(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return builder.toString().strip();
    }

    /** Parses the {@code crates.<id>.rewards} weighted loot table (map-list form). */
    private List<RewardEntry> loadRewards(final String crateId) {
        if (configManager.getConfiguration() == null) {
            return List.of();
        }
        final List<Map<?, ?>> raw = configManager.getConfiguration().getMapList("crates." + crateId + ".rewards");
        final List<RewardEntry> rewards = new ArrayList<>();
        for (final Map<?, ?> entry : raw) {
            final double weight = numberOf(entry.get("weight"), 1.0D);
            if (weight <= 0.0D) {
                continue;
            }
            final Object materialRaw = entry.get("material");
            final Material material = materialRaw == null ? null : Material.matchMaterial(String.valueOf(materialRaw));
            final Object commandRaw = entry.get("command");
            final Object descriptionRaw = entry.get("description");
            final int amount = (int) numberOf(entry.get("amount"), 1.0D);
            if (material == null && commandRaw == null) {
                continue; // Malformed entry: neither an item nor a command — skip rather than crash a roll.
            }
            rewards.add(new RewardEntry(weight, material, amount,
                    commandRaw == null ? null : String.valueOf(commandRaw),
                    descriptionRaw == null ? null : String.valueOf(descriptionRaw)));
        }
        return rewards;
    }

    private static double numberOf(final Object value, final double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    // ==================== persistence ====================

    @Override
    public void load() {
        crateBlocks.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection section = yaml.getConfigurationSection("blocks");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String location = section.getString(key + ".location");
            final String crateId = section.getString(key + ".crate-id");
            if (location != null && crateId != null) {
                crateBlocks.put(location, crateId);
            }
        }
    }

    @Override
    public void save() {
        persist();
    }

    private void persist() {
        synchronized (saveLock) {
            final YamlConfiguration yaml = new YamlConfiguration();
            int index = 0;
            for (final Map.Entry<String, String> entry : crateBlocks.entrySet()) {
                yaml.set("blocks." + index + ".location", entry.getKey());
                yaml.set("blocks." + index + ".crate-id", entry.getValue());
                index++;
            }
            try {
                YamlStore.saveAtomic(storageFile, yaml);
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "A crates-data.yml mentése nem sikerült", exception);
            }
        }
    }
}
