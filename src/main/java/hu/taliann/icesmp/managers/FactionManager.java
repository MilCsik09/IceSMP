package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for player faction assignments.
 * Tracks which faction each player belongs to with YAML-based persistent storage.
 */
public final class FactionManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Map<UUID, FactionType> playerFactions = new ConcurrentHashMap<>();
    /** PDC key storing the epoch-millis timestamp of the player's last PAID faction switch. */
    private final NamespacedKey lastSwitchKey;

    public FactionManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "factions.yml");
        this.lastSwitchKey = new NamespacedKey(plugin, "faction_last_switch");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        playerFactions.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);

            for (final String uuidKey : yaml.getKeys(false)) {
                try {
                    final UUID uuid = UUID.fromString(uuidKey);
                    final String factionName = yaml.getString(uuidKey, FactionType.NEUTRAL.name());
                    final FactionType faction = FactionType.fromString(factionName);
                    playerFactions.put(uuid, faction);
                } catch (final IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in factions.yml: " + uuidKey);
                }
            }

            plugin.getLogger().info("Loaded " + playerFactions.size() + " faction assignments.");
        } catch (final Exception e) {
            plugin.getLogger().severe("Failed to load factions: " + e.getMessage());
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();

            for (final Map.Entry<UUID, FactionType> entry : playerFactions.entrySet()) {
                yaml.set(entry.getKey().toString(), entry.getValue().name());
            }

            YamlStore.saveAtomic(storageFile, yaml);
            plugin.getLogger().info("Saved " + playerFactions.size() + " faction assignments.");
        } catch (final IOException e) {
            plugin.getLogger().severe("Failed to save factions: " + e.getMessage());
        }
    }

    /** @return the player's faction, or NEUTRAL if not found */
    public FactionType getFaction(final UUID uuid) {
        return playerFactions.getOrDefault(uuid, FactionType.NEUTRAL);
    }

    /**
     * Gets a snapshot of every stored player → faction assignment
     * (used by the periodic faction tax).
     *
     * @return immutable copy of the assignments
     */
    public Map<UUID, FactionType> getFactionAssignments() {
        return Map.copyOf(playerFactions);
    }

    /** @param factionType the faction to set (null defaults to NEUTRAL) */
    public void setFaction(final UUID uuid, final FactionType factionType) {
        playerFactions.put(uuid, factionType == null ? FactionType.NEUTRAL : factionType);
        save();
    }

    /**
     * Checks whether the player has already made an explicit faction choice
     * (as opposed to merely defaulting to NEUTRAL because no record exists yet).
     * Used to tell a free first join apart from a paid/cooldown-gated switch.
     *
     * @param uuid the player UUID
     * @return true if a faction assignment is on record for this player
     */
    public boolean hasChosenFaction(final UUID uuid) {
        return playerFactions.containsKey(uuid);
    }

    /**
     * Gets the currency cost of switching from one faction to another
     * (charged in the player's CURRENT faction currency). First join is free.
     *
     * @return the switch cost, from {@code factions.switch.cost} (default 500.0)
     */
    public double getSwitchCost() {
        return configManager.getDouble("factions.switch.cost", 500.0);
    }

    /**
     * Gets the minimum number of hours a player must wait between faction switches.
     *
     * @return the cooldown in hours, from {@code factions.switch.cooldown-hours} (default 72.0, 0 = off)
     */
    public double getSwitchCooldownHours() {
        return configManager.getDouble("factions.switch.cooldown-hours", 72.0);
    }

    /** @return the cooldown in milliseconds (0 = no cooldown) */
    public long getSwitchCooldownMillis() {
        return Math.round(getSwitchCooldownHours() * 3_600_000.0D);
    }

    /**
     * Gets how much longer the player must wait before their next paid faction switch.
     *
     * @param player the player
     * @return remaining cooldown in milliseconds, or 0 if the player may switch now
     */
    public long getRemainingSwitchCooldownMillis(final Player player) {
        final long cooldownMillis = getSwitchCooldownMillis();
        if (cooldownMillis <= 0) {
            return 0L;
        }

        final long lastSwitchMillis = player.getPersistentDataContainer()
                .getOrDefault(lastSwitchKey, PersistentDataType.LONG, 0L);
        final long elapsed = System.currentTimeMillis() - lastSwitchMillis;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    /**
     * Records "now" as the player's last paid faction switch timestamp, starting the cooldown.
     *
     * @param player the player who just paid to switch factions
     */
    public void recordSwitch(final Player player) {
        player.getPersistentDataContainer().set(lastSwitchKey, PersistentDataType.LONG, System.currentTimeMillis());
    }

    public void removeFaction(final UUID uuid) {
        if (uuid == null) {
            return;
        }

        playerFactions.remove(uuid);
        save();
    }

    /** @return comma-separated faction display names */
    public String describeAvailableFactions() {
        final StringBuilder builder = new StringBuilder();
        for (final FactionType factionType : FactionType.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(factionType.getDisplayName());
        }
        return builder.toString();
    }

    public void cleanup(final UUID playerId) {
        // No volatile per-session faction state exists; assignments are persisted data.
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}


