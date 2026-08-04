package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionMembership;
import hu.taliann.icesmp.factions.FactionMembershipMutation;
import hu.taliann.icesmp.factions.DurableRecoveryPolicy;
import hu.taliann.icesmp.factions.DurableTransactionProtocol;
import hu.taliann.icesmp.factions.FactionSwitchJournal;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manager for player faction assignments.
 * Tracks which faction each player belongs to with YAML-based persistent storage.
 */
public final class FactionManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final File storageFile;
    private final FactionSwitchJournal switchJournal;
    /** Assignment/history mutation and full-snapshot persistence boundary. */
    private final Object stateLock = new Object();

    private record MembershipState(Map<UUID, FactionType> assignments,
                                   Map<UUID, FactionType> history) {
        private MembershipState {
            assignments = Map.copyOf(assignments);
            history = Map.copyOf(history);
        }

        private static MembershipState empty() {
            return new MembershipState(Map.of(), Map.of());
        }
    }

    /** One all-old or all-new durable membership generation. */
    private volatile MembershipState liveState = MembershipState.empty();
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

    public FactionManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.storageFile = new File(plugin.getDataFolder(), "factions.yml");
        this.switchJournal = new FactionSwitchJournal(
                new File(plugin.getDataFolder(), "faction-switch-journal.yml"), plugin.getLogger());
        this.lastSwitchKey = new NamespacedKey(plugin, "faction_last_switch");
        this.switchSeasonKey = new NamespacedKey(plugin, "faction_switch_season");
        this.switchCountKey = new NamespacedKey(plugin, "faction_switch_count");
        this.everChosenKey = new NamespacedKey(plugin, "faction_ever_chosen");
        this.lastChosenFactionKey = new NamespacedKey(plugin, "faction_last_chosen");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    public void setSeasonManager(final SeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }

    public void load() {
        final Map<UUID, FactionType> loadedAssignments = new HashMap<>();
        final Map<UUID, FactionType> loadedHistory = new HashMap<>();

        if (storageFile.exists()) {
            final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
            final org.bukkit.configuration.ConfigurationSection history =
                    yaml.getConfigurationSection(HISTORY_SECTION);
            if (history != null) {
                for (final String uuidKey : history.getKeys(false)) {
                    final UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidKey);
                    } catch (final IllegalArgumentException exception) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen UUID a tagsági előzményben: " + uuidKey);
                        return;
                    }
                    final String factionName = history.getString(uuidKey);
                    final FactionType faction = FactionType.fromInput(factionName);
                    if (faction == null) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen tagsági előzmény: " + uuidKey + " -> " + factionName);
                    }
                    loadedHistory.put(uuid, faction);
                }
            }

            for (final String uuidKey : yaml.getKeys(false)) {
                if (HISTORY_SECTION.equals(uuidKey)) {
                    continue;
                }
                final UUID uuid;
                try {
                    uuid = UUID.fromString(uuidKey);
                } catch (final IllegalArgumentException exception) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen UUID a factions.yml-ben: " + uuidKey);
                    return;
                }
                final String factionName = yaml.getString(uuidKey);
                final FactionType faction = FactionType.fromInput(factionName);
                if (faction == null) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen frakció a factions.yml-ben: " + uuidKey + " -> " + factionName);
                }
                loadedAssignments.put(uuid, faction);
                loadedHistory.putIfAbsent(uuid, faction);
            }
        }

        liveState = new MembershipState(loadedAssignments, loadedHistory);
        switchJournal.load();
        recoverPendingSwitch();
        plugin.getLogger().info("Loaded " + liveState.assignments().size() + " faction assignments.");
    }

    public void save() {
        synchronized (stateLock) {
            writeStateLocked(liveState);
        }
    }

    /** The caller must hold stateLock. Candidate is persisted before publication. */
    private void writeStateLocked(final MembershipState candidate) {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<UUID, FactionType> entry : candidate.assignments().entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue().name());
        }
        for (final Map.Entry<UUID, FactionType> entry : candidate.history().entrySet()) {
            yaml.set(HISTORY_SECTION + "." + entry.getKey(), entry.getValue().name());
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            plugin.getLogger().info("Saved " + candidate.assignments().size()
                    + " faction assignments.");
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save factions: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save factions", exception);
        }
    }

    /**
     * Display/currency fallback only. Gameplay entitlement must use {@link #getMembership(UUID)},
     * {@link #isEligibleForFactionBenefits(UUID)} or {@link #isMember(UUID, FactionType)}.
     *
     * @return the chosen faction, or NEUTRAL for an unassigned Menedék guest
     */
    public FactionType getEconomyFaction(final UUID uuid) {
        return liveState.assignments().getOrDefault(uuid, FactionType.NEUTRAL);
    }

    public FactionMembership getMembership(final UUID uuid) {
        final FactionType chosen = liveState.assignments().get(uuid);
        return chosen == null ? FactionMembership.guest() : FactionMembership.citizen(chosen);
    }

    public Optional<FactionType> getChosenFaction(final UUID uuid) {
        return Optional.ofNullable(liveState.assignments().get(uuid));
    }

    public boolean isEligibleForFactionBenefits(final UUID uuid) {
        return liveState.assignments().containsKey(uuid);
    }

    public boolean isMember(final UUID uuid, final FactionType faction) {
        return faction != null && liveState.assignments().get(uuid) == faction;
    }

    public boolean sameChosenFaction(final UUID first, final UUID second) {
        final Map<UUID, FactionType> assignments = liveState.assignments();
        final FactionType faction = assignments.get(first);
        return faction != null && faction == assignments.get(second);
    }

    /**
     * Gets a snapshot of every stored player → faction assignment
     * (used by the periodic faction tax).
     *
     * @return immutable copy of the assignments
     */
    public Map<UUID, FactionType> getFactionAssignments() {
        return liveState.assignments();
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
            final MembershipState previous = liveState;
            final Map<UUID, FactionType> assignments = new HashMap<>(previous.assignments());
            final Map<UUID, FactionType> history = new HashMap<>(previous.history());
            changed = assignments.get(playerId) != target;
            FactionMembershipMutation.assign(assignments, history, playerId, target);
            final MembershipState candidate = new MembershipState(assignments, history);
            writeStateLocked(candidate);
            liveState = candidate;
        }
        publishMembershipChange(playerId, target, changed);
    }

    private void publishMembershipChange(final UUID playerId, final FactionType target,
                                         final boolean changed) {
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
     * Paid switch transaction: WAL prepare -> durable wallet deduction -> durable membership
     * snapshot -> journal completion. A rejected membership write compensates the wallet before
     * the failure escapes; an uncompleted journal is recovered during the next startup.
     */
    public boolean switchFactionDurably(final UUID playerId,
                                        final FactionType expectedCurrent,
                                        final FactionType target,
                                        final CurrencyType currency,
                                        final double cost) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(currency, "currency");
        if (!Double.isFinite(cost) || cost < 0.0D) {
            throw new IllegalArgumentException("Invalid faction switch cost");
        }
        final boolean changed;
        synchronized (stateLock) {
            final MembershipState previous = liveState;
            if (previous.assignments().get(playerId) != expectedCurrent) {
                return false;
            }
            if (cost == 0.0D) {
                final Map<UUID, FactionType> assignments =
                        new HashMap<>(previous.assignments());
                final Map<UUID, FactionType> history = new HashMap<>(previous.history());
                FactionMembershipMutation.assign(assignments, history, playerId, target);
                final MembershipState candidate = new MembershipState(assignments, history);
                writeStateLocked(candidate);
                liveState = candidate;
                changed = expectedCurrent != target;
            } else {
                final CurrencyManager.DurableMutation wallet =
                        currencyManager.planDurableDeduction(playerId, currency, cost);
                if (wallet == null) {
                    return false;
                }
                final FactionMembershipMutation.Snapshot membershipBefore =
                        FactionMembershipMutation.capture(previous.assignments(),
                                previous.history(), playerId);
                final MembershipState candidate;
                {
                    final Map<UUID, FactionType> assignments =
                            new HashMap<>(previous.assignments());
                    final Map<UUID, FactionType> history = new HashMap<>(previous.history());
                    FactionMembershipMutation.assign(assignments, history, playerId, target);
                    candidate = new MembershipState(assignments, history);
                }
                final FactionSwitchJournal.Entry[] journalEntry =
                        new FactionSwitchJournal.Entry[1];
                final DurableTransactionProtocol.ExecutionResult transactionResult =
                        DurableTransactionProtocol.execute(new DurableTransactionProtocol.Steps() {
                    @Override
                    public void prepare() {
                        journalEntry[0] = switchJournal.prepare(
                                membershipBefore, target, currency, cost, wallet);
                    }

                    @Override
                    public boolean hasWalletMutation() {
                        return true;
                    }

                    @Override
                    public void applyWallet() {
                        currencyManager.applyDurably(wallet);
                    }

                    @Override
                    public void commitDomain() {
                        writeStateLocked(candidate);
                        liveState = candidate;
                    }

                    @Override
                    public void rollbackWallet() {
                        currencyManager.rollbackDurably(wallet);
                    }

                    @Override
                    public void completeJournal() {
                        switchJournal.complete(journalEntry[0]);
                    }
                });
                if (transactionResult.recoveryPending()) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Faction switch committed, but WAL cleanup failed; startup recovery will finalize it",
                            transactionResult.cleanupFailure());
                }
                changed = expectedCurrent != target;
            }
        }
        publishMembershipChange(playerId, target, changed);
        return true;
    }

    private void recoverPendingSwitch() {
        final FactionSwitchJournal.Entry entry = switchJournal.pending();
        if (entry == null) {
            return;
        }
        synchronized (stateLock) {
            final FactionMembershipMutation.Snapshot before = entry.membershipBefore();
            final Map<UUID, FactionType> assignments = liveState.assignments();
            final Map<UUID, FactionType> history = liveState.history();
            final boolean membershipBefore =
                    assignments.containsKey(entry.playerId()) == before.hadAssignment()
                    && assignments.get(entry.playerId()) == before.assignment()
                    && history.containsKey(entry.playerId()) == before.hadHistory()
                    && history.get(entry.playerId()) == before.lastChosenFaction();
            final boolean membershipAfter =
                    assignments.get(entry.playerId()) == entry.targetFaction()
                    && history.get(entry.playerId()) == entry.targetFaction();
            final boolean walletBefore = currencyManager.walletMatches(
                    entry.walletMutation(), false);
            final boolean walletAfter = currencyManager.walletMatches(
                    entry.walletMutation(), true);

            switch (DurableRecoveryPolicy.decide(
                    membershipBefore, membershipAfter, walletBefore, walletAfter, true)) {
                case COMPLETE_COMMITTED -> {
                    switchJournal.complete(entry);
                    plugin.getLogger().warning(
                            "Recovered committed faction switch " + entry.id());
                }
                case DISCARD_UNAPPLIED -> {
                    switchJournal.complete(entry);
                    plugin.getLogger().warning(
                            "Discarded unapplied faction switch " + entry.id());
                }
                case ROLLBACK_WALLET -> {
                    currencyManager.rollbackDurably(entry.walletMutation());
                    switchJournal.complete(entry);
                    plugin.getLogger().warning(
                            "Rolled back interrupted faction switch " + entry.id());
                }
                case ROLLBACK_DOMAIN -> {
                    final MembershipState currentState = liveState;
                    final Map<UUID, FactionType> restoredAssignments =
                            new HashMap<>(currentState.assignments());
                    final Map<UUID, FactionType> restoredHistory =
                            new HashMap<>(currentState.history());
                    FactionMembershipMutation.restore(
                            restoredAssignments, restoredHistory, before);
                    final MembershipState restored = new MembershipState(
                            restoredAssignments, restoredHistory);
                    writeStateLocked(restored);
                    liveState = restored;
                    switchJournal.complete(entry);
                    plugin.getLogger().warning(
                            "Rolled back unpaid faction switch " + entry.id());
                }
                case AMBIGUOUS -> switchJournal.failCorrupt(
                        "Ambiguous faction-switch recovery state for " + entry.id());
            }
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
        return liveState.history().containsKey(player.getUniqueId())
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
        return Optional.ofNullable(uuid == null ? null : liveState.history().get(uuid));
    }

    /** Called on the player's owner thread at join to backfill history for pre-rework citizens. */
    public void reconcileMembershipHistory(final Player player) {
        final FactionType chosen = liveState.assignments().get(player.getUniqueId());
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
        final boolean changed;
        synchronized (stateLock) {
            final MembershipState previous = liveState;
            if (!previous.assignments().containsKey(uuid)) {
                return;
            }
            final Map<UUID, FactionType> assignments = new HashMap<>(previous.assignments());
            assignments.remove(uuid);
            final MembershipState candidate = new MembershipState(assignments, previous.history());
            writeStateLocked(candidate);
            liveState = candidate;
            changed = true;
        }
        if (changed) {
            membershipChangeHook.accept(uuid);
        }
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
