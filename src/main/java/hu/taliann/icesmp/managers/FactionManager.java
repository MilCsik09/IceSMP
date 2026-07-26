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
    /** PDC key: melyik szezonban (seasonStart-bélyeg) számoltuk a váltásokat. */
    private final NamespacedKey switchSeasonKey;
    /** PDC key: hány FIZETETT váltás történt a {@link #switchSeasonKey} szerinti szezonban. */
    private final NamespacedKey switchCountKey;
    /** Setter-injektált (a SeasonManager a FactionManager UTÁN épül fel a core-ban). */
    private volatile SeasonManager seasonManager;

    public FactionManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "factions.yml");
        this.lastSwitchKey = new NamespacedKey(plugin, "faction_last_switch");
        this.switchSeasonKey = new NamespacedKey(plugin, "faction_switch_season");
        this.switchCountKey = new NamespacedKey(plugin, "faction_switch_count");
        plugin.getDataFolder().mkdirs();
    }

    public void setSeasonManager(final SeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }

    public void load() {
        playerFactions.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());

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
    /**
     * Setter-injektált: a frakcióváltás a céhtagságot is egyezteti. A hívás KÖZPONTILAG itt van,
     * nem a parancsokban — így minden út (belépés, kilépés, admin-beállítás, száműzetés, vezeklés)
     * egyeztet, és egy új út sem tudja kihagyni.
     */
    private volatile hu.taliann.icesmp.managers.GuildManager guildManager;

    public void setGuildManager(final hu.taliann.icesmp.managers.GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void setFaction(final UUID uuid, final FactionType factionType) {
        final FactionType target = factionType == null ? FactionType.NEUTRAL : factionType;
        playerFactions.put(uuid, target);
        save();
        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;
        if (guildRef != null) {
            guildRef.reconcileFaction(uuid, target);
        }
        final Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) {
            AdvancementService.award(online, "faction_join");
        }
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
     * Records "now" as the player's last paid faction switch timestamp, starting the cooldown,
     * and bumps the per-season switch counter (resetting it when a new season has started since
     * the last recorded switch).
     *
     * @param player the player who just paid to switch factions
     */
    public void recordSwitch(final Player player) {
        player.getPersistentDataContainer().set(lastSwitchKey, PersistentDataType.LONG, System.currentTimeMillis());
        recordSeasonSwitch(player);
    }

    /**
     * Bumps the per-season switch counter WITHOUT starting the paid-switch cooldown — az
     * ingyenes váltás-utak (Menedékből kilépés, Sötétbe lépés) is ide számolnak, de nem
     * indítanak fizetős cooldownt.
     *
     * @param player the player whose faction just changed
     */
    public void recordSeasonSwitch(final Player player) {
        final SeasonManager seasons = this.seasonManager;
        if (seasons == null) {
            return;
        }
        final long season = seasons.getSeasonStart();
        final var pdc = player.getPersistentDataContainer();
        final long storedSeason = pdc.getOrDefault(switchSeasonKey, PersistentDataType.LONG, 0L);
        final int count = storedSeason == season
                ? pdc.getOrDefault(switchCountKey, PersistentDataType.INTEGER, 0)
                : 0;
        pdc.set(switchSeasonKey, PersistentDataType.LONG, season);
        pdc.set(switchCountKey, PersistentDataType.INTEGER, count + 1);
    }

    /** @return hány fizetett frakció-váltása volt a játékosnak a FUTÓ szezonban */
    public int getSwitchesThisSeason(final Player player) {
        final SeasonManager seasons = this.seasonManager;
        if (seasons == null) {
            return 0;
        }
        final var pdc = player.getPersistentDataContainer();
        if (pdc.getOrDefault(switchSeasonKey, PersistentDataType.LONG, 0L) != seasons.getSeasonStart()) {
            return 0;
        }
        return pdc.getOrDefault(switchCountKey, PersistentDataType.INTEGER, 0);
    }

    /** @return szezononként engedélyezett fizetett váltások száma ({@code factions.switch.max-per-season}, 0 = korlátlan) */
    public int getMaxSwitchesPerSeason() {
        return configManager.getInt("factions.switch.max-per-season", 2);
    }

    /** @return a szezon-végi váltás-zár hossza napokban ({@code factions.switch.lockout-final-days}, 0 = nincs zár) */
    public int getSwitchLockoutFinalDays() {
        return configManager.getInt("factions.switch.lockout-final-days", 7);
    }

    /**
     * A szezon hajrájában (az utolsó {@code lockout-final-days} napban) a frakció-váltás
     * teljesen tilos — a liga-végjátékot ne lehessen oldalt váltva megjátszani.
     *
     * @return true, ha most a szezon-végi váltás-zár él
     */
    public boolean isInSeasonEndLockout() {
        final SeasonManager seasons = this.seasonManager;
        final int lockoutDays = getSwitchLockoutFinalDays();
        if (seasons == null || lockoutDays <= 0) {
            return false;
        }
        return seasons.getSeasonEndMillis() - System.currentTimeMillis() <= lockoutDays * 86_400_000L;
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


