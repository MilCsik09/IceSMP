package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionMembership;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** PlayerProfile-backed faction membership and switch policy facade. */
public final class FactionManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final PlayerProfileFactionStore factionStore = new PlayerProfileFactionStore();
    /** Rebuildable all-owner projection for taxes, treasury and offline composition. */
    private final ConcurrentMap<UUID, PlayerProfileFactionStore.State> projection =
            new ConcurrentHashMap<>();
    private volatile AutoCloseable profileSubscription;
    private volatile SeasonManager seasonManager;
    private volatile Consumer<UUID> membershipChangeHook = ignored -> { };
    private volatile GuildManager guildManager;

    public FactionManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final CurrencyManager currencyManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.currencyManager = Objects.requireNonNull(currencyManager, "currencyManager");
    }

    public void setSeasonManager(final SeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }

    /** Rebuilds the derived assignment/history projection; no factions.yml is read. */
    @Override
    public void load() {
        final Optional<PlayerProfileAuthority> installed = PlayerProfileAuthority.installed();
        if (installed.isEmpty()) {
            throw new IllegalStateException("PlayerProfile authority is required before FactionManager.load");
        }
        if (profileSubscription == null) {
            profileSubscription = installed.orElseThrow().service().subscribe((playerId, revision, changed) -> {
                if (changed.contains(ProfileSectionId.FACTION)) refreshProjection(playerId);
            });
        }
        installed.orElseThrow().repository().listPlayerIds()
                .thenAccept(ids -> ids.forEach(this::refreshProjection))
                .toCompletableFuture().join();
        plugin.getLogger().info("Rebuilt " + getFactionAssignments().size()
                + " PlayerProfile faction assignments.");
    }

    /** Membership mutations commit immediately; disable only drains PlayerProfile. */
    @Override
    public void save() {
        PlayerProfileAuthority.installed().ifPresent(authority ->
                authority.repository().flushAll().toCompletableFuture().join());
    }

    /** Display/currency fallback only; missing membership is a benefit-free guest. */
    public FactionType getEconomyFaction(final UUID uuid) {
        return state(uuid).flatMap(PlayerProfileFactionStore.State::membership)
                .orElse(FactionType.NEUTRAL);
    }

    public FactionMembership getMembership(final UUID uuid) {
        final Optional<FactionType> chosen = state(uuid)
                .flatMap(PlayerProfileFactionStore.State::membership);
        return chosen.map(FactionMembership::citizen).orElseGet(FactionMembership::guest);
    }

    public Optional<FactionType> getChosenFaction(final UUID uuid) {
        return state(uuid).flatMap(PlayerProfileFactionStore.State::membership);
    }

    public boolean isEligibleForFactionBenefits(final UUID uuid) {
        return getChosenFaction(uuid).isPresent();
    }

    public boolean isMember(final UUID uuid, final FactionType faction) {
        return faction != null && getChosenFaction(uuid).orElse(null) == faction;
    }

    public boolean sameChosenFaction(final UUID first, final UUID second) {
        final FactionType faction = getChosenFaction(first).orElse(null);
        return faction != null && faction == getChosenFaction(second).orElse(null);
    }

    /** Immutable derived projection; PlayerProfile remains the sole durable authority. */
    public Map<UUID, FactionType> getFactionAssignments() {
        final LinkedHashMap<UUID, FactionType> result = new LinkedHashMap<>();
        projection.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().membership()
                        .ifPresent(faction -> result.put(entry.getKey(), faction)));
        return Map.copyOf(result);
    }

    public void setGuildManager(final GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public void setMembershipChangeHook(final Consumer<UUID> membershipChangeHook) {
        this.membershipChangeHook = membershipChangeHook == null ? ignored -> { } : membershipChangeHook;
    }

    public void setFaction(final UUID uuid, final FactionType factionType) {
        final UUID playerId = Objects.requireNonNull(uuid, "player UUID");
        final FactionType target = Objects.requireNonNull(factionType, "chosen faction");
        final FactionType previous = getChosenFaction(playerId).orElse(null);
        try {
            final PlayerProfileFactionStore.State committed = factionStore.assign(playerId, target)
                    .toCompletableFuture().join();
            projection.put(playerId, committed);
            publishMembershipChange(playerId, target, previous != target);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile faction assignment failed", unwrap(failure));
        }
    }

    private void publishMembershipChange(final UUID playerId, final FactionType target,
                                         final boolean changed) {
        if (changed) membershipChangeHook.accept(playerId);
        final GuildManager guildRef = guildManager;
        if (changed && guildRef != null) guildRef.reconcileFaction(playerId, target);
        final Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null && changed) {
            online.getScheduler().run(plugin,
                    task -> AdvancementService.award(online, "faction_join"), null);
        }
    }

    /**
     * Paid switch is one PlayerProfile WAL transaction: wallet, membership, history, paid
     * cooldown and per-season counter commit or roll back together.
     */
    public boolean switchFactionDurably(final UUID playerId,
                                        final FactionType expectedCurrent,
                                        final FactionType target,
                                        final CurrencyType currency,
                                        final double cost) {
        final long seasonId = seasonManager == null ? 0L : seasonManager.getSeasonStart();
        try {
            final boolean committed = factionStore.switchDurably(playerId, expectedCurrent,
                    target, currency, cost, seasonId).toCompletableFuture().join();
            if (!committed) return false;
            final PlayerProfileFactionStore.State state = factionStore.readCached(playerId);
            projection.put(playerId, state);
            publishMembershipChange(playerId, target, expectedCurrent != target);
            return true;
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile faction switch failed", unwrap(failure));
        }
    }

    public boolean hasChosenFaction(final UUID uuid) {
        return isEligibleForFactionBenefits(uuid);
    }

    /** Fail-closed anti-reset history. */
    public boolean hasEverChosenFaction(final Player player) {
        if (player == null) return true;
        return state(player.getUniqueId()).map(PlayerProfileFactionStore.State::everChosen)
                .orElse(true);
    }

    public FactionType getLastChosenFaction(final Player player) {
        return player == null ? FactionType.NEUTRAL
                : getLastChosenFaction(player.getUniqueId()).orElse(FactionType.NEUTRAL);
    }

    public Optional<FactionType> getLastChosenFaction(final UUID uuid) {
        return state(uuid).flatMap(PlayerProfileFactionStore.State::lastChosen);
    }

    /** PlayerProfile already owns history; join only refreshes the derived projection. */
    public void reconcileMembershipHistory(final Player player) {
        if (player != null) refreshProjection(player.getUniqueId());
    }

    public double getSwitchCost() {
        return configManager.getDouble("factions.switch.cost", 500.0D);
    }

    public double getSwitchCooldownHours() {
        return configManager.getDouble("factions.switch.cooldown-hours", 72.0D);
    }

    public long getSwitchCooldownMillis() {
        return Math.round(getSwitchCooldownHours() * 3_600_000.0D);
    }

    public long getRemainingSwitchCooldownMillis(final Player player) {
        final long cooldown = getSwitchCooldownMillis();
        if (cooldown <= 0L) return 0L;
        if (player == null) return cooldown;
        final Optional<PlayerProfileFactionStore.State> state = state(player.getUniqueId());
        if (state.isEmpty()) return cooldown;
        final long elapsed = System.currentTimeMillis() - state.orElseThrow().lastPaidSwitchAt();
        return Math.max(0L, cooldown - elapsed);
    }

    /** Compatibility path for external callers; paid command flow commits this atomically already. */
    public void recordSwitch(final Player player) {
        if (player == null) return;
        final long seasonId = seasonManager == null ? 0L : seasonManager.getSeasonStart();
        try {
            factionStore.recordSeasonSwitch(player.getUniqueId(), seasonId, true)
                    .thenAccept(count -> refreshProjection(player.getUniqueId()));
        } catch (final RuntimeException failure) {
            throw new IllegalStateException("PlayerProfile paid switch marker failed", failure);
        }
    }

    public void recordSeasonSwitch(final Player player) {
        if (player == null || seasonManager == null) return;
        factionStore.recordSeasonSwitch(player.getUniqueId(), seasonManager.getSeasonStart(), false)
                .thenAccept(count -> refreshProjection(player.getUniqueId()))
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile season switch counter failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                });
    }

    public int getSwitchesThisSeason(final Player player) {
        if (player == null || seasonManager == null) return 0;
        return state(player.getUniqueId())
                .map(value -> value.switchSeason() == seasonManager.getSeasonStart()
                        ? value.switchesThisSeason() : 0)
                .orElse(getMaxSwitchesPerSeason());
    }

    public int getMaxSwitchesPerSeason() {
        return configManager.getInt("factions.switch.max-per-season", 2);
    }

    public int getSwitchLockoutFinalDays() {
        return configManager.getInt("factions.switch.lockout-final-days", 7);
    }

    public boolean isInSeasonEndLockout() {
        final SeasonManager seasons = seasonManager;
        final int lockoutDays = getSwitchLockoutFinalDays();
        return seasons != null && lockoutDays > 0
                && seasons.getSeasonEndMillis() - System.currentTimeMillis()
                <= lockoutDays * 86_400_000L;
    }

    public void removeFaction(final UUID uuid) {
        if (uuid == null) return;
        final FactionType previous = getChosenFaction(uuid).orElse(null);
        if (previous == null) return;
        try {
            final PlayerProfileFactionStore.State committed = factionStore.remove(uuid)
                    .toCompletableFuture().join();
            projection.put(uuid, committed);
            membershipChangeHook.accept(uuid);
            final GuildManager guildRef = guildManager;
            if (guildRef != null) guildRef.reconcileFaction(uuid, null);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile faction removal failed", unwrap(failure));
        }
    }

    public String describeAvailableFactions() {
        final StringBuilder builder = new StringBuilder();
        for (final FactionType factionType : FactionType.values()) {
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(factionType.getDisplayName());
        }
        return builder.toString();
    }

    private Optional<PlayerProfileFactionStore.State> state(final UUID playerId) {
        if (playerId == null) return Optional.empty();
        final PlayerProfileFactionStore.State projected = projection.get(playerId);
        if (projected != null) return Optional.of(projected);
        try {
            final PlayerProfileFactionStore.State cached = factionStore.readCached(playerId);
            projection.put(playerId, cached);
            return Optional.of(cached);
        } catch (final RuntimeException notReady) {
            return Optional.empty();
        }
    }

    private void refreshProjection(final UUID playerId) {
        factionStore.load(playerId).thenAccept(state -> projection.put(playerId, state))
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile faction projection failed for "
                            + playerId + ": " + rootMessage(failure));
                    return null;
                });
    }

    @Override
    public void cleanup(final UUID playerId) {
        // The all-owner projection remains rebuildable and is needed by offline tax consumers.
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String rootMessage(final Throwable failure) {
        final Throwable root = unwrap(failure);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
