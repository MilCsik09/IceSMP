package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for faction territory claims and capitals.
 * Territories are circular regions persisted to territories.yml; admins define
 * them with the /territory command. A faction has at most one capital claim.
 */
public final class TerritoryManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<String, Territory> territories = new ConcurrentHashMap<>();

    public TerritoryManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "territories.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        territories.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection territoriesSection = yaml.getConfigurationSection("territories");
            if (territoriesSection == null) {
                return;
            }

            for (final String territoryId : territoriesSection.getKeys(false)) {
                final ConfigurationSection section = territoriesSection.getConfigurationSection(territoryId);
                if (section == null) {
                    continue;
                }

                final FactionType faction = FactionType.fromInput(section.getString("faction", ""));
                final String world = section.getString("world", "");
                if (faction == null || world.isBlank()) {
                    plugin.getLogger().warning("Invalid territory entry '" + territoryId + "' in territories.yml; skipping.");
                    continue;
                }

                territories.put(territoryId.toLowerCase(Locale.ROOT), new Territory(
                        territoryId.toLowerCase(Locale.ROOT),
                        faction,
                        section.getString("name", territoryId),
                        world,
                        section.getInt("x", 0),
                        section.getInt("z", 0),
                        Math.max(1, section.getInt("radius", 50)),
                        section.getBoolean("capital", false)
                ));
            }

            plugin.getLogger().info("Loaded " + territories.size() + " faction territory claim(s).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load territories: " + exception.getMessage());
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Territory territory : territories.values()) {
                final String basePath = "territories." + territory.id();
                yaml.set(basePath + ".faction", territory.faction().name());
                yaml.set(basePath + ".name", territory.name());
                yaml.set(basePath + ".world", territory.world());
                yaml.set(basePath + ".x", territory.x());
                yaml.set(basePath + ".z", territory.z());
                yaml.set(basePath + ".radius", territory.radius());
                yaml.set(basePath + ".capital", territory.capital());
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save territories: " + exception.getMessage());
        }
    }

    /**
     * Defines (or overwrites) a territory claim and persists it.
     * Marking a claim as capital demotes the faction's previous capital claim.
     *
     * @param id the claim id
     * @param faction the owning faction
     * @param name the display name
     * @param center the center location
     * @param radius the radius in blocks
     * @param capital whether this is the faction capital
     * @return the stored territory
     */
    public Territory define(final String id, final FactionType faction, final String name,
                            final Location center, final int radius, final boolean capital) {
        final String normalizedId = id.toLowerCase(Locale.ROOT);

        if (capital) {
            for (final Map.Entry<String, Territory> entry : territories.entrySet()) {
                final Territory existing = entry.getValue();
                if (existing.capital() && existing.faction() == faction && !existing.id().equals(normalizedId)) {
                    entry.setValue(new Territory(existing.id(), existing.faction(), existing.name(),
                            existing.world(), existing.x(), existing.z(), existing.radius(), false));
                }
            }
        }

        final Territory territory = new Territory(
                normalizedId,
                faction,
                name == null || name.isBlank() ? normalizedId : name,
                center.getWorld().getName(),
                center.getBlockX(),
                center.getBlockZ(),
                Math.max(1, radius),
                capital
        );
        territories.put(normalizedId, territory);
        save();
        return territory;
    }

    public boolean remove(final String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        final boolean removed = territories.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Territory getById(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        return territories.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the territory at a location. When claims overlap, the smallest radius
     * (most specific) claim wins, with capitals breaking ties.
     *
     * @param location the location to check
     * @return the territory, or null if unclaimed wilderness
     */
    /** Whether the location lies inside ANY faction's capital (banking/exchange gate). */
    public boolean isInCapital(final Location location) {
        final Territory territory = getTerritoryAt(location);
        return territory != null && territory.capital();
    }

    public Territory getTerritoryAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Territory best = null;
        for (final Territory territory : territories.values()) {
            if (!territory.contains(location.getWorld().getName(), location.getX(), location.getZ())) {
                continue;
            }

            if (best == null
                    || territory.radius() < best.radius()
                    || (territory.radius() == best.radius() && territory.capital() && !best.capital())) {
                best = territory;
            }
        }

        return best;
    }

    public Territory getCapital(final FactionType faction) {
        for (final Territory territory : territories.values()) {
            if (territory.capital() && territory.faction() == faction) {
                return territory;
            }
        }

        return null;
    }

    public Collection<Territory> all() {
        return territories.values();
    }
}
