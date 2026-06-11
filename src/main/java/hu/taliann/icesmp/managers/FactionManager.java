package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import org.bukkit.configuration.file.YamlConfiguration;
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
public final class FactionManager {

    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<UUID, FactionType> playerFactions = new ConcurrentHashMap<>();

    /**
     * Constructs a new FactionManager.
     *
     * @param plugin the plugin instance
     */
    public FactionManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "factions.yml");
        plugin.getDataFolder().mkdirs();
    }

    /**
     * Loads faction data from YAML file.
     */
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

    /**
     * Saves faction data to YAML file.
     */
    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();

            for (final Map.Entry<UUID, FactionType> entry : playerFactions.entrySet()) {
                yaml.set(entry.getKey().toString(), entry.getValue().name());
            }

            yaml.save(storageFile);
            plugin.getLogger().info("Saved " + playerFactions.size() + " faction assignments.");
        } catch (final IOException e) {
            plugin.getLogger().severe("Failed to save factions: " + e.getMessage());
        }
    }

    /**
     * Gets the faction for a player.
     *
     * @param uuid the player UUID
     * @return the player's faction, or NEUTRAL if not found
     */
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

    /**
     * Sets the faction for a player.
     *
     * @param uuid the player UUID
     * @param factionType the faction to set (null defaults to NEUTRAL)
     */
    public void setFaction(final UUID uuid, final FactionType factionType) {
        playerFactions.put(uuid, factionType == null ? FactionType.NEUTRAL : factionType);
        save();
    }

    public void removeFaction(final UUID uuid) {
        if (uuid == null) {
            return;
        }

        playerFactions.remove(uuid);
        save();
    }

    /**
     * Gets a human-readable list of all available factions.
     *
     * @return comma-separated faction display names
     */
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


