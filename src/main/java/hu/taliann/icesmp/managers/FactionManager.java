package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionMembership;
import hu.taliann.icesmp.factions.FactionMembershipMutation;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manager for player faction assignments.
 * Tracks which faction each player belongs to with YAML-based persistent storage.
 */
public final class FactionManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    /** Assignment/history mutation and full-snapshot persistence boundary. */
    private final Object stateLock = new Object();
    private final Map<UUID, FactionType> playerFactions = new ConcurrentHashMap<>();
    /** Durable anti-reset history; survives removal/corruption of the current assignment. */
    private final Map<UUID, FactionType> lastChosenFactions = new ConcurrentHashMap<>();
    private static final String HISTORY_SECTION = "_membership-history";
    /** PDC key storing the epoch-millis timestamp of the player's last PAID faction switch. */
    private final NamespacedKey lastSwitchKey;
    /** PDC key: melyik szezonban (seasonStart-bélyeg) számoltuk a váltásokat. */
    private final NamespacedKey switchSeasonKey;
    /** PDC key: hány, az első választás utáni váltás történt a jelölt szezonban. */
    private final NamespacedKey switchCountKey;
    private final NamespacedKey everChosenKey;
    private final NamespacedKey lastChosenFactionKey;
    /** Setter-injektált (a SeasonManager a FactionManager UTÁN épül fel a core-ban). */
    private volatile SeasonManager seasonManager;
    private volatile Consumer<UUID> membershipChangeHook = ignored -> { };

    public FactionManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "factions.yml");
        this.lastSwitchKey = new NamespacedKey(plugin, "faction_last_switch");
        this.switchSeasonKey = new NamespacedKey(plugin, "faction_switch_season");
        this.switchCountKey = new NamespacedKey(plugin, "faction_switch_count");
        this.everChosenKey = new NamespacedKey(plugin, "faction_ever_chosen");
        this.lastChosenFactionKey = new NamespacedKey(plugin, "faction_last_chosen");
        plugin.getDataFolder().mkdirs();
    }

    public void setSeasonManager(final SeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }

    public void load() {
        playerFactions.clear();
        lastChosenFactions.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());

            final org.bukkit.configuration.ConfigurationSection history =
                    yaml.getConfigurationSection(HISTORY_SECTION);
            if (history != null) {
                for (final String uuidKey : history.getKeys(false)) {
                    try {
                        final UUID uuid = UUID.fromString(uuidKey);
                        final String factionName = history.getString(uuidKey);
                        final FactionType faction = FactionType.fromInput(factionName);
                        if (faction == null) {
                            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                    "Érvénytelen tagsági előzmény: " + uuidKey + " -> " + factionName);
                        }
                        lastChosenFactions.put(uuid, faction);
                    } catch (final IllegalArgumentException exception) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen UUID a tagsági előzményben: " + uuidKey);
                    }
                }
            }

            for (final String uuidKey : yaml.getKeys(false)) {
                if (HISTORY_SECTION.equals(uuidKey)) {
                    continue;
                }
                try {
                    final UUID uuid = UUID.fromString(uuidKey);
                    final String factionName = yaml.getString(uuidKey);
                    final FactionType faction = FactionType.fromInput(factionName);
                    if (faction == null) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen frakció a factions.yml-ben: " + uuidKey + " -> " + factionName);
                    }
                    playerFactions.put(uuid, faction);
                    lastChosenFactions.putIfAbsent(uuid, faction);
                } catch (final IllegalArgumentException e) {
                    // Fail-closed: a hibás rekord átugrása után a következő teljes-snapshot
                    // mentés VÉGLEG eltüntetné a kézzel még javítható bejegyzést.
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen UUID vagy frakció a factions.yml-ben: " + uuidKey);
                }
            }

            plugin.getLogger().info("Loaded " + playerFactions.size() + " faction assignments.");
        } catch (final Exception e) {
            plugin.getLogger().severe("Failed to load factions: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (stateLock) {
            writeStateLocked();
        }
    }

    /** The caller must hold stateLock. */
    private void writeStateLocked() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();

            for (final Map.Entry<UUID, FactionType> entry : playerFactions.entrySet()) {
                yaml.set(entry.getKey().toString(), entry.getValue().name());
            }
            for (final Map.Entry<UUID, FactionType> entry : lastChosenFactions.entrySet()) {
                yaml.set(HISTORY_SECTION + "." + entry.getKey(), entry.getValue().name());
            }

            YamlStore.saveAtomic(storageFile, yaml);
            plugin.getLogger().info("Saved " + playerFactions.size() + " faction assignments.");
        } catch (final IOException e) {
            plugin.getLogger().severe("Failed to save factions: " + e.getMessage());
            throw new java.io.UncheckedIOException("Failed to save factions", e);
        }
    }

    /**
     * Display/currency fallback only. Gameplay entitlement must use {@link #getMembership(UUID)},
     * {@link #isEligibleForFactionBenefits(UUID)} or {@link #isMember(UUID, FactionType)}.
     *
     * @return the chosen faction, or NEUTRAL for an unassigned Menedék guest
     */
    public FactionType getEconomyFaction(final UUID uuid) {
        return playerFactions.getOrDefault(uuid, FactionType.NEUTRAL);
    }

    public FactionMembership getMembership(final UUID uuid) {
        final FactionType chosen = playerFactions.get(uuid);
        return chosen == null ? FactionMembership.guest() : FactionMembership.citizen(chosen);
    }

    public Optional<FactionType> getChosenFaction(final UUID uuid) {
        return Optional.ofNullable(playerFactions.get(uuid));
    }

    public boolean isEligibleForFactionBenefits(final UUID uuid) {
        return playerFactions.containsKey(uuid);
    }

    public boolean isMember(final UUID uuid, final FactionType faction) {
        return faction != null && playerFactions.get(uuid) == faction;
    }

    public boolean sameChosenFaction(final UUID first, final UUID second) {
        final FactionType faction = playerFactions.get(first);
        return faction != null && faction == playerFactions.get(second);
    }

    /**
     * Gets a snapshot of every stored player → faction assignment
     * (used by the periodic faction tax).
     *
     * @return immutable copy of the assignments
     */
    public Map<UUID, FactionType> getFactionAssignments() {
        synchronized (stateLock) {
            return Map.copyOf(playerFactions);
        }
    }

    /**
     * Setter-injektált: a frakcióváltás a céhtagságot is egyezteti. A hívás KÖZPONTILAG itt van,
     * nem a parancsokban — így minden út (belépés, kilépés, admin-beállítás, száműzetés, vezeklés)
     * egyeztet, és egy új út sem tudja kihagyni.
     */
    private volatile hu.taliann.icesmp.managers.GuildManager guildManager;

    public void setGuildManager(final hu.taliann.icesmp.managers.GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void setMembershipChangeHook(final Consumer<UUID> membershipChangeHook) {
        this.membershipChangeHook = membershipChangeHook == null ? ignored -> { } : membershipChangeHook;
    }

    public void setFaction(final UUID uuid, final FactionType factionType) {
        final UUID playerId = Objects.requireNonNull(uuid, "player UUID");
        final FactionType target = Objects.requireNonNull(factionType, "chosen faction");
        final boolean changed;
        synchronized (stateLock) {
            final FactionMembershipMutation.Snapshot previousState =
                    FactionMembershipMutation.capture(
                            playerFactions, lastChosenFactions, playerId);
            changed = previousState.assignment() != target;
            FactionMembershipMutation.assign(
                    playerFactions, lastChosenFactions, playerId, target);
            try {
                writeStateLocked();
            } catch (final RuntimeException | Error persistenceFailure) {
                FactionMembershipMutation.restore(
                        playerFactions, lastChosenFactions, previousState);
                throw persistenceFailure;
            }
        }
        if (changed) {
            membershipChangeHook.accept(playerId);
        }
        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;
        if (changed && guildRef != null) {
            guildRef.reconcileFaction(playerId, target);
        }
        final Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null) {
            online.getScheduler().run(plugin, task -> {
                markMembershipHistory(online, target);
                if (changed) {
                    AdvancementService.award(online, "faction_join");
                }
            }, null);
        }
    }

    /**
     * Checks whether the player currently has an explicit faction assignment. A missing record is
     * the benefit-free guest state; durable prior-choice history is queried separately.
     *
     * @param uuid the player UUID
     * @return true if a faction assignment is on record for this player
     */
    public boolean hasChosenFaction(final UUID uuid) {
        return isEligibleForFactionBenefits(uuid);
    }

    /** Durable anti-reset history; a missing current assignment cannot recreate a first choice. */
    public boolean hasEverChosenFaction(final Player player) {
        return lastChosenFactions.containsKey(player.getUniqueId())
                || player.getPersistentDataContainer().getOrDefault(
                everChosenKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    public FactionType getLastChosenFaction(final Player player) {
        final FactionType durable = getLastChosenFaction(player.getUniqueId()).orElse(null);
        if (durable != null) {
            return durable;
        }
        final String raw = player.getPersistentDataContainer().get(
                lastChosenFactionKey, PersistentDataType.STRING);
        final FactionType parsed = FactionType.fromInput(raw);
        return parsed == null ? FactionType.NEUTRAL : parsed;
    }

    /** Durable history lookup without inventing a faction for an unresolved legacy record. */
    public Optional<FactionType> getLastChosenFaction(final UUID uuid) {
        return Optional.ofNullable(uuid == null ? null : lastChosenFactions.get(uuid));
    }

    /** Called on the player's owner thread at join to backfill history for pre-rework citizens. */
    public void reconcileMembershipHistory(final Player player) {
        final FactionType chosen = playerFactions.get(player.getUniqueId());
        if (chosen != null) {
            markMembershipHistory(player, chosen);
        }
    }

    private void markMembershipHistory(final Player player, final FactionType faction) {
        player.getPersistentDataContainer().set(everChosenKey, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(lastChosenFactionKey,
                PersistentDataType.STRING, faction.name());
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

    /** @return hány, az első választás utáni frakcióváltása volt a játékosnak a futó szezonban */
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

    /** @return szezononként engedélyezett váltások száma ({@code factions.switch.max-per-season}, 0 = korlátlan) */
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

    /** Erases citizenship entirely and returns the player to the benefit-free guest state. */
    public void removeFaction(final UUID uuid) {
        if (uuid == null) {
            return;
        }

        synchronized (stateLock) {
            final FactionMembershipMutation.Snapshot previousState =
                    FactionMembershipMutation.capture(
                            playerFactions, lastChosenFactions, uuid);
            if (!previousState.hadAssignment()) {
                return;
            }
            FactionMembershipMutation.removeAssignment(playerFactions, uuid);
            try {
                writeStateLocked();
            } catch (final RuntimeException | Error persistenceFailure) {
                FactionMembershipMutation.restore(
                        playerFactions, lastChosenFactions, previousState);
                throw persistenceFailure;
            }
        }
        membershipChangeHook.accept(uuid);
        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;
        if (guildRef != null) {
            guildRef.reconcileFaction(uuid, null);
        }
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
