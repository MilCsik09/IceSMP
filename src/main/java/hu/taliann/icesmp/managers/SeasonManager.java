package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Seasonal league: factions earn points from raid victories and world boss kills over a
 * configurable season. State persists to season.yml; expiry is checked on the global tick.
 */
public final class SeasonManager implements PersistentStore, org.bukkit.event.Listener {

    private record RewardItem(Material material, int amount) {
        private RewardItem {
            if (material == null || material.isAir() || amount <= 0) {
                throw new IllegalArgumentException("Invalid season reward item");
            }
        }
    }

    private record Standing(FactionType faction, int points) {
    }

    private record TreasuryGrant(String grantId, FactionType faction, double amount) {
    }

    private record RewardBatch(
            UUID batchId,
            int closingSeason,
            int openedSeason,
            FactionType champion,
            int championPoints,
            double championTreasuryReward,
            List<Standing> standings,
            List<TreasuryGrant> treasuryGrants,
            boolean monumentPending,
            boolean announcementPending,
            boolean storyPending
    ) {
        private RewardBatch {
            standings = List.copyOf(standings);
            treasuryGrants = List.copyOf(treasuryGrants);
        }
    }

    private record MemberRewardClaim(
            UUID grantId,
            UUID recipient,
            int closingSeason,
            List<RewardItem> items,
            int buffTicks,
            boolean firework
    ) {
        private MemberRewardClaim {
            items = List.copyOf(items);
        }
    }

    private record RewardPlan(RewardBatch batch, Map<UUID, MemberRewardClaim> claims) {
        private RewardPlan {
            claims = Map.copyOf(claims);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final File storageFile;
    /** Minden pont/nyugta mutáció, snapshot, tartós írás és rollback közös monitora. */
    private final Object stateLock = new Object();
    private final Map<FactionType, Integer> points = new ConcurrentHashMap<>();
    /**
     * Már alkalmazott, azonosítóhoz kötött pont-jóváírások. A bejegyzés a pontokkal EGY atomi
     * fájl-képbe kerül, ezért a hívó (kifizetés-outbox) pontosan egyszeri alkalmazást kap.
     */
    private final Map<String, Long> appliedGrants = new ConcurrentHashMap<>();
    // Grant-nyugtát nem szabad véges darabszám alapján kidobni: egy hosszan függő outbox
    // későbbi replaye különben újra alkalmazhatná a már kifizetett rész-jutalmat.
    /** J9 — fejezet-sorszám: a szezon = story-fejezet; váltáskor nő, perzisztens. */
    private volatile int seasonNumber = 1;
    /** G16 — nagydöntő-hétvége: bejelentés-flag (szezononként egyszer, volatilis). */
    private volatile boolean grandFinaleAnnounced;
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final Map<UUID, MemberRewardClaim> pendingMemberClaims = new HashMap<>();
    private final Set<UUID> claimDeliveryQueued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean playerSaveWarningSent = new AtomicBoolean(false);
    private final NamespacedKey seasonRewardReceiptKey;
    private volatile RewardBatch pendingRewardBatch;
    private volatile boolean playerSaveSupported = true;
    private volatile long seasonStart = System.currentTimeMillis();

    public SeasonManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final MessageManager messageManager,
                         final FactionTreasuryManager treasuryManager,
                         final FactionManager factionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.treasuryManager = treasuryManager;
        this.factionManager = factionManager;
        this.storageFile = new File(plugin.getDataFolder(), "season.yml");
        this.seasonRewardReceiptKey = new NamespacedKey(plugin, "season_reward_receipts");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        synchronized (stateLock) {
            points.clear();
            appliedGrants.clear();
            pendingMemberClaims.clear();
            pendingRewardBatch = null;
            seasonStart = System.currentTimeMillis();
            seasonNumber = 1;

            if (!storageFile.exists()) {
                if (!writeStateLocked()) {
                    throw new IllegalStateException("The initial season state could not be persisted");
                }
                return;
            }

            final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
            final long loadedStart = yaml.getLong("season.start", -1L);
            final int loadedNumber = yaml.getInt("season.number", -1);
            if (loadedStart <= 0L || loadedNumber < 1) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "A season start/number mező hiányzik vagy érvénytelen");
                return;
            }
            seasonStart = loadedStart;
            seasonNumber = loadedNumber;

            final ConfigurationSection grants = yaml.getConfigurationSection("season.applied-grants");
            if (grants != null) {
                for (final String key : grants.getKeys(false)) {
                    final long timestamp = grants.getLong(key, -1L);
                    if (key.isBlank() || timestamp <= 0L) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen season applied-grant: " + key);
                        return;
                    }
                    appliedGrants.put(key, timestamp);
                }
            }

            final ConfigurationSection pointsSection = yaml.getConfigurationSection("season.points");
            if (pointsSection != null) {
                for (final String factionKey : pointsSection.getKeys(false)) {
                    final FactionType faction = FactionType.fromInput(factionKey);
                    final int value = pointsSection.getInt(factionKey, -1);
                    if (faction == null || value < 0) {
                        YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                                "Érvénytelen season pontrekord: " + factionKey);
                        return;
                    }
                    points.put(faction, value);
                }
            }

            if (!yaml.getStringList("season.pending-champion-spoils").isEmpty()) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "A legacy pending champion reward nem tartalmaz grant-ID/playerdata nyugtát; kézi egyeztetés szükséges");
                return;
            }
            pendingRewardBatch = loadRewardBatch(yaml);
            loadMemberClaims(yaml);
        }
    }

    public synchronized void save() {
        synchronized (stateLock) {
            writeStateLocked();
        }
    }

    /** A hívónak tartania kell a stateLock monitort. */
    /** A hívónak tartania kell a stateLock monitort. */
    private boolean writeStateLocked() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("season.start", seasonStart);
            yaml.set("season.number", seasonNumber);
            for (final Map.Entry<FactionType, Integer> entry : points.entrySet()) {
                yaml.set("season.points." + entry.getKey().name(), entry.getValue());
            }
            for (final Map.Entry<String, Long> entry : appliedGrants.entrySet()) {
                yaml.set("season.applied-grants." + entry.getKey(), entry.getValue());
            }
            writeRewardBatch(yaml, pendingRewardBatch);
            for (final MemberRewardClaim claim : pendingMemberClaims.values()) {
                final String path = "season.member-claims." + claim.grantId();
                yaml.set(path + ".recipient", claim.recipient().toString());
                yaml.set(path + ".closing-season", claim.closingSeason());
                yaml.set(path + ".buff-ticks", claim.buffTicks());
                yaml.set(path + ".firework", claim.firework());
                yaml.set(path + ".items", claim.items().stream()
                        .map(item -> item.material().name() + ":" + item.amount()).toList());
            }

            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save season.yml: " + exception.getMessage());
            return false;
        }
    }

    /**
     * Kompatibilis idempotens pont-jóváírás: a live szabályok szerint kiszámított deltát ugyanazon
     * lock alatt alkalmazza. No-opot nem ACK-ol, mert az később jogos jutalmat zárhatna le.
     */
    public boolean addPointsOnce(final String grantId, final FactionType faction,
                                 final int amount, final String source) {
        synchronized (stateLock) {
            if (grantId == null || grantId.isBlank()) {
                return false;
            }
            if (appliedGrants.containsKey(grantId)) {
                return true;
            }
            final int exactDelta = calculatePointsDeltaLocked(faction, amount, source);
            if (exactDelta <= 0) {
                return false;
            }
            return addExactPointsOnceLocked(grantId, faction, exactDelta);
        }
    }

    /**
     * A teljesítés pillanatában kiszámítja a VÉGLEGES, frakcióra alkalmazandó deltát. Ezt kell az
     * outbox immutable pillanatképébe írni; replaykor már nem szabad újraszámolni.
     */
    public int calculatePointsDelta(final FactionType faction, final int amount,
                                    final String source) {
        synchronized (stateLock) {
            return calculatePointsDeltaLocked(faction, amount, source);
        }
    }

    /** Több frakció végleges deltáját egyetlen konzisztens season-state snapshot alatt számolja. */
    public Map<FactionType, Integer> calculatePointsDeltas(
            final Iterable<FactionType> factions, final int amount, final String source) {
        if (factions == null) {
            return Map.of();
        }
        synchronized (stateLock) {
            final java.util.EnumMap<FactionType, Integer> deltas =
                    new java.util.EnumMap<>(FactionType.class);
            for (final FactionType faction : factions) {
                final int delta = calculatePointsDeltaLocked(faction, amount, source);
                if (delta > 0) {
                    deltas.put(faction, delta);
                }
            }
            return Map.copyOf(deltas);
        }
    }

    private int calculatePointsDeltaLocked(final FactionType faction, final int amount,
                                            final String source) {
        if (faction == null || amount <= 0
                || !configManager.getBoolean("world-events.season.enabled", true)) {
            return 0;
        }
        final String sourceKey = source == null || source.isBlank() ? "other" : source;
        final double weight = Math.max(0.0D, configManager.getDouble(
                "world-events.season.source-weights." + sourceKey + "."
                        + faction.name().toLowerCase(java.util.Locale.ROOT), 1.0D));
        final long weightedLong = Math.round(amount * weight);
        if (weightedLong <= 0L) {
            return 0;
        }
        final int weighted = (int) Math.min(Integer.MAX_VALUE, weightedLong);

        // A két idő-szorzó nem szorzódik össze; a nagyobbik érvényesül.
        final SeasonFinaleManager finaleRef = seasonFinale;
        double timeMultiplier = finaleRef == null
                ? 1.0D : Math.max(1.0D, finaleRef.leaguePointMultiplier());
        if (isGrandFinaleWindowLocked() && topTwoLocked().contains(faction)) {
            timeMultiplier = Math.max(timeMultiplier, Math.max(1.0D,
                    configManager.getDouble(
                            "world-events.season-finale.top2-point-multiplier", 2.0D)));
        }
        final long scaledLong = Math.round(weighted * timeMultiplier);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(weighted, scaledLong));
    }

    /**
     * Immutable outboxból érkező exact-delta alkalmazása. Sem configot, sem időablakot, sem
     * ranglistát nem olvas újra; a nyugta és a pont egyetlen atomi fájlképbe kerül.
     */
    public boolean addExactPointsOnce(final String grantId, final FactionType faction,
                                      final int exactDelta) {
        synchronized (stateLock) {
            return addExactPointsOnceLocked(grantId, faction, exactDelta);
        }
    }

    private boolean addExactPointsOnceLocked(final String grantId, final FactionType faction,
                                             final int exactDelta) {
        if (grantId == null || grantId.isBlank() || faction == null || exactDelta <= 0) {
            return false;
        }
        if (appliedGrants.containsKey(grantId)) {
            return true;
        }

        final Integer previous = points.get(faction);
        mergeExactPointsLocked(faction, exactDelta);
        appliedGrants.put(grantId, System.currentTimeMillis());
        if (!YamlStore.isLoadFailed(storageFile) && writeStateLocked()) {
            return true;
        }

        appliedGrants.remove(grantId);
        if (previous == null) {
            points.remove(faction);
        } else {
            points.put(faction, previous);
        }
        return false;
    }

    private void mergeExactPointsLocked(final FactionType faction, final int delta) {
        final long sum = (long) points.getOrDefault(faction, 0) + delta;
        points.put(faction, (int) Math.min(Integer.MAX_VALUE, sum));
    }

    public int getPoints(final FactionType faction) {
        synchronized (stateLock) {
            return faction == null ? 0 : points.getOrDefault(faction, 0);
        }
    }

    /** G16 — a nagydöntő-ablak: a szezon utolsó konfigurált órái. */
    public boolean isGrandFinaleWindow() {
        synchronized (stateLock) {
            return isGrandFinaleWindowLocked();
        }
    }

    private boolean isGrandFinaleWindowLocked() {
        if (!configManager.getBoolean("world-events.season-finale.top2-enabled", true)) {
            return false;
        }
        final long windowMillis = Math.max(1, configManager.getInt(
                "world-events.season-finale.top2-window-hours", 48)) * 3_600_000L;
        final long remaining = getSeasonEndMillisLocked() - System.currentTimeMillis();
        return remaining > 0 && remaining <= windowMillis;
    }

    /** A liga-tábla első két helyezettje. */
    public java.util.List<FactionType> topTwo() {
        synchronized (stateLock) {
            return topTwoLocked();
        }
    }

    private java.util.List<FactionType> topTwoLocked() {
        return points.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(2)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** G16 — az ablak nyíltakor egyszeri szerver-broadcast a párosítással. */
    private void announceGrandFinale() {
        final java.util.List<FactionType> top;
        synchronized (stateLock) {
            if (grandFinaleAnnounced || !isGrandFinaleWindowLocked()) {
                return;
            }
            grandFinaleAnnounced = true;
            top = topTwoLocked();
        }
        if (top.size() < 2) {
            return;
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "season-grand-finale",
                "<gold>🏟 NAGYDÖNTŐ! A krónikák ítélnek: <white>{first}</white> ⚔ <white>{second}</white> — a záró hétvégén a két éllovas minden liga-pontja DUPLÁN számít! Királyok, hirdessetek raidet!</gold>",
                Map.of("first", top.get(0).getDisplayName(), "second", top.get(1).getDisplayName())));
    }

    /** A futó szezon hányadik napja (1-től). */
    public int getSeasonDay() {
        synchronized (stateLock) {
            return (int) Math.max(1L,
                    (System.currentTimeMillis() - seasonStart) / 86_400_000L + 1L);
        }
    }

    public int getSeasonNumber() {
        synchronized (stateLock) {
            return seasonNumber;
        }
    }

    /** A futó szezon kezdő-bélyege. */
    public long getSeasonStart() {
        synchronized (stateLock) {
            return seasonStart;
        }
    }

    public long getSeasonEndMillis() {
        synchronized (stateLock) {
            return getSeasonEndMillisLocked();
        }
    }

    private long getSeasonEndMillisLocked() {
        final long lengthDays = Math.max(1L,
                configManager.getLong("world-events.season.length-days", 60L));
        return seasonStart + (lengthDays * 24L * 60L * 60L * 1000L);
    }

    public void addPoints(final FactionType faction, final int amount) {
        addPoints(faction, amount, "other");
    }

    /** Awards league points from a named source. */
    public void addPoints(final FactionType faction, final int amount, final String source) {
        final int exactDelta;
        synchronized (stateLock) {
            exactDelta = calculatePointsDeltaLocked(faction, amount, source);
            if (exactDelta <= 0) {
                return;
            }
            mergeExactPointsLocked(faction, exactDelta);
        }
        requestSave();
    }

    /** Setter-injected finálé-eszkaláció. */
    private volatile SeasonFinaleManager seasonFinale;

    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

    /** Coordinates the season commit with stores whose generation must advance afterwards. */
    @FunctionalInterface
    public interface SeasonTransitionCoordinator {
        boolean commit(int closingSeason, int openedSeason, BooleanSupplier seasonCommit);
    }

    private volatile SeasonTransitionCoordinator seasonTransitionCoordinator;

    public void setSeasonTransitionCoordinator(
            final SeasonTransitionCoordinator seasonTransitionCoordinator) {
        this.seasonTransitionCoordinator = seasonTransitionCoordinator;
    }

    private volatile SeasonStoryTeller storyTeller;

    public void setStoryTeller(final SeasonStoryTeller storyTeller) {
        this.storyTeller = storyTeller;
    }

    /** Setter-injected emlékmű-vésnök. */
    private volatile SeasonMonumentManager monumentManager;

    public void setMonumentManager(final SeasonMonumentManager monumentManager) {
        this.monumentManager = monumentManager;
    }

    /** Debounced async flush. */
    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    /** Periodic check on the global world-events tick: closes expired seasons. */
    public void tick() {
        processPendingSeasonRewards();
        queueOnlineMemberClaims();
        if (!configManager.getBoolean("world-events.season.enabled", true)) {
            return;
        }
        announceGrandFinale();

        final int closingSeason;
        synchronized (stateLock) {
            if (System.currentTimeMillis() < getSeasonEndMillisLocked()) {
                return;
            }
            closingSeason = seasonNumber;
        }
        if (closingSeason == Integer.MAX_VALUE) {
            plugin.getLogger().severe("Szezonzárás elhalasztva: a season number elérte az int határát.");
            return;
        }
        final int openedSeason = closingSeason + 1;
        final BooleanSupplier seasonCommit = () -> closeExpiredSeason(closingSeason, openedSeason);

        final SeasonTransitionCoordinator coordinator = seasonTransitionCoordinator;
        try {
            final boolean committed = coordinator == null
                    ? seasonCommit.getAsBoolean()
                    : coordinator.commit(closingSeason, openedSeason, seasonCommit);
            if (!committed) {
                plugin.getLogger().severe("Szezonzárás elhalasztva: a season.yml commit nem sikerült.");
            } else {
                processPendingSeasonRewards();
                queueOnlineMemberClaims();
            }
        } catch (final RuntimeException blocked) {
            plugin.getLogger().severe("Szezonzárás elhalasztva: " + blocked.getMessage());
        }
    }

    /**
     * Atomically commits the opened season together with an immutable reward outbox and all
     * champion-member claims. The community coordinator holds its monitor for the whole callback,
     * so contributions cannot cross the standings snapshot/generation boundary. Target-store and
     * playerdata side effects run only after this callback and the coordinated community ACK return.
     */
    private boolean closeExpiredSeason(final int expectedSeason, final int openedSeason) {
        final EnumMap<FactionType, Integer> closingPoints = new EnumMap<>(FactionType.class);
        synchronized (stateLock) {
            if (seasonNumber != expectedSeason
                    || pendingRewardBatch != null
                    || System.currentTimeMillis() < getSeasonEndMillisLocked()) {
                return false;
            }
            closingPoints.putAll(points);
        }

        FactionType champion = null;
        int best = 0;
        boolean tie = false;
        for (final Map.Entry<FactionType, Integer> entry : closingPoints.entrySet()) {
            if (entry.getValue() > best) {
                champion = entry.getKey();
                best = entry.getValue();
                tie = false;
            } else if (entry.getValue() == best && best > 0) {
                tie = true;
            }
        }
        if (tie || best <= 0) {
            champion = null;
        }

        final List<Standing> standings = closingPoints.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<FactionType, Integer>comparingByValue().reversed())
                .map(entry -> new Standing(entry.getKey(), entry.getValue()))
                .toList();
        final RewardPlan rewardPlan = buildRewardPlan(expectedSeason, openedSeason, champion, best, standings);

        synchronized (stateLock) {
            if (seasonNumber != expectedSeason || pendingRewardBatch != null) {
                return false;
            }
            final EnumMap<FactionType, Integer> previousPoints = new EnumMap<>(FactionType.class);
            previousPoints.putAll(points);
            final long previousStart = seasonStart;
            final int previousNumber = seasonNumber;
            final boolean previousFinale = grandFinaleAnnounced;
            final RewardBatch previousBatch = pendingRewardBatch;
            final Map<UUID, MemberRewardClaim> previousClaims = new HashMap<>(pendingMemberClaims);

            final EnumMap<FactionType, Integer> carry = new EnumMap<>(FactionType.class);
            for (final FactionType faction : FactionType.values()) {
                final int current = points.getOrDefault(faction, 0);
                final int closed = closingPoints.getOrDefault(faction, 0);
                if (current > closed) {
                    carry.put(faction, current - closed);
                }
            }
            points.clear();
            points.putAll(carry);
            seasonStart = System.currentTimeMillis();
            seasonNumber = openedSeason;
            grandFinaleAnnounced = false;
            pendingRewardBatch = rewardPlan.batch();
            pendingMemberClaims.putAll(rewardPlan.claims());

            try {
                if (!writeStateLocked()) {
                    points.clear();
                    points.putAll(previousPoints);
                    seasonStart = previousStart;
                    seasonNumber = previousNumber;
                    grandFinaleAnnounced = previousFinale;
                    pendingRewardBatch = previousBatch;
                    pendingMemberClaims.clear();
                    pendingMemberClaims.putAll(previousClaims);
                    return false;
                }
            } catch (final RuntimeException | Error failure) {
                points.clear();
                points.putAll(previousPoints);
                seasonStart = previousStart;
                seasonNumber = previousNumber;
                grandFinaleAnnounced = previousFinale;
                pendingRewardBatch = previousBatch;
                pendingMemberClaims.clear();
                pendingMemberClaims.putAll(previousClaims);
                throw failure;
            }
        }

        return true;
    }

    private RewardPlan buildRewardPlan(final int closingSeason, final int openedSeason,
                                       final FactionType champion, final int championPoints,
                                       final List<Standing> standings) {
        final UUID batchId = UUID.randomUUID();
        final List<TreasuryGrant> treasuryGrants = new ArrayList<>();
        final Map<UUID, MemberRewardClaim> claims = new LinkedHashMap<>();

        if (champion != null) {
            final double reward = configManager.getDouble("world-events.season.treasury-reward", 1000.0D);
            if (!Double.isFinite(reward) || reward < 0.0D) {
                throw new IllegalStateException("Invalid season treasury reward: " + reward);
            }
            if (reward > 0.0D) {
                treasuryGrants.add(new TreasuryGrant(
                        "season:" + closingSeason + ":treasury:" + champion.name(), champion, reward));
                final List<Double> configured = configManager.getDoubleList(
                        "world-events.season.runner-up-ratios");
                final List<Double> ratios = configured.isEmpty() ? List.of(0.5D, 0.25D) : configured;
                for (int i = 1; i < standings.size() && i - 1 < ratios.size(); i++) {
                    final double ratio = ratios.get(i - 1);
                    final double share = reward * ratio;
                    if (!Double.isFinite(ratio) || ratio < 0.0D || !Double.isFinite(share)) {
                        throw new IllegalStateException("Invalid season runner-up reward ratio: " + ratio);
                    }
                    if (share > 0.0D) {
                        final FactionType faction = standings.get(i).faction();
                        treasuryGrants.add(new TreasuryGrant(
                                "season:" + closingSeason + ":treasury:" + faction.name(), faction, share));
                    }
                }
            }

            final List<RewardItem> items = parseRewardItemsStrict();
            final long buffMinutes = configManager.getLong(
                    "world-events.season.champion-buff-minutes", 30L);
            final int buffTicks = SeasonRewardStateData.safeBuffTicks(buffMinutes);
            final boolean firework = configManager.getBoolean(
                    "world-events.season.champion-firework", true);
            for (final Map.Entry<UUID, FactionType> member
                    : factionManager.getFactionAssignments().entrySet()) {
                if (member.getValue() != champion) {
                    continue;
                }
                final UUID grantId = UUID.nameUUIDFromBytes((batchId + ":" + member.getKey())
                        .getBytes(StandardCharsets.UTF_8));
                claims.put(grantId, new MemberRewardClaim(
                        grantId, member.getKey(), closingSeason, items, buffTicks, firework));
            }
        }

        final RewardBatch batch = new RewardBatch(
                batchId, closingSeason, openedSeason, champion, championPoints,
                champion == null ? 0.0D : treasuryGrants.stream()
                        .filter(grant -> grant.faction() == champion)
                        .mapToDouble(TreasuryGrant::amount).findFirst().orElse(0.0D),
                standings, treasuryGrants, champion != null, true, true);
        return new RewardPlan(batch, claims);
    }

    private List<RewardItem> parseRewardItemsStrict() {
        final List<RewardItem> items = new ArrayList<>();
        for (final String raw : configManager.getStringList(
                "world-events.season.champion-reward-items")) {
            final String[] parts = raw.split(":", -1);
            if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
                throw new IllegalStateException("Invalid season reward item: " + raw);
            }
            final Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir()) {
                throw new IllegalStateException("Unknown season reward material: " + raw);
            }
            final int amount;
            try {
                amount = parts.length == 1 ? 1 : Integer.parseInt(parts[1].trim());
            } catch (final NumberFormatException invalid) {
                throw new IllegalStateException("Invalid season reward amount: " + raw, invalid);
            }
            if (amount <= 0) {
                throw new IllegalStateException("Season reward amount must be positive: " + raw);
            }
            items.add(new RewardItem(material, amount));
        }
        return List.copyOf(items);
    }

    private void processPendingSeasonRewards() {
        RewardBatch batch;
        synchronized (stateLock) {
            batch = pendingRewardBatch;
        }
        if (batch == null) {
            return;
        }
        SeasonRewardStateData.validateBatchGeneration(
                getSeasonNumber(), batch.closingSeason(), batch.openedSeason());

        for (final TreasuryGrant grant : batch.treasuryGrants()) {
            if (treasuryManager.depositOnce(grant.grantId(), grant.faction(), grant.amount())) {
                acknowledgeTreasuryGrant(batch.batchId(), grant.grantId());
            }
        }

        synchronized (stateLock) {
            batch = pendingRewardBatch;
        }
        if (batch == null) {
            return;
        }
        if (batch.monumentPending()) {
            final SeasonMonumentManager monumentRef = monumentManager;
            if (batch.champion() == null || (monumentRef != null && monumentRef.recordSeasonOnce(
                    "season:" + batch.closingSeason() + ":monument",
                    batch.closingSeason(), batch.champion()))) {
                acknowledgeBatchFlag(batch.batchId(), "monument");
            }
        }
        if (claimBestEffortBatchFlag(batch.batchId(), "announcement")) {
            announceRewardBatch(batch);
        }
        final SeasonStoryTeller storyRef = storyTeller;
        if (storyRef != null && claimBestEffortBatchFlag(batch.batchId(), "story")) {
            storyRef.tellTransition(batch.champion());
        }
        clearCompletedRewardBatch();
    }

    private void announceRewardBatch(final RewardBatch batch) {
        if (!batch.standings().isEmpty()) {
            final StringBuilder summary = new StringBuilder();
            for (int i = 0; i < batch.standings().size(); i++) {
                if (i > 0) {
                    summary.append(" <gray>•</gray> ");
                }
                final Standing standing = batch.standings().get(i);
                summary.append(i + 1).append(". ").append(standing.faction().getDisplayName())
                        .append(" <gray>(").append(standing.points()).append(")</gray>");
            }
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-final-standings",
                    "<gold>🏁 Szezon-végeredmény: {standings}</gold>",
                    Map.of("standings", summary.toString())));
        }
        if (batch.champion() == null) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended-no-champion",
                    "<gold>🏁 A szezon véget ért bajnok nélkül — új szezon kezdődik!</gold>"));
        } else {
            final double championReward = batch.championTreasuryReward();
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended",
                    "<gold>🏆 A szezon bajnoka: <white>{champion}</white> ({points} pont)! A frakciókassza <white>{reward}</white> jutalmat kap. Új szezon kezdődik!</gold>",
                    Map.of("champion", batch.champion().getDisplayName(),
                            "points", String.valueOf(batch.championPoints()),
                            "reward", String.valueOf(championReward))));
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "season-chapter-opened",
                "<gold>📖 Új fejezet nyílik a krónikában: <white>{chapter}. fejezet</white> — a régi fejezet küldetései lezárultak, újak várnak!</gold>",
                Map.of("chapter", String.valueOf(batch.openedSeason()))));
    }

    private void acknowledgeTreasuryGrant(final UUID batchId, final String grantId) {
        synchronized (stateLock) {
            final RewardBatch current = pendingRewardBatch;
            if (current == null || !current.batchId().equals(batchId)) {
                return;
            }
            final List<TreasuryGrant> remaining = current.treasuryGrants().stream()
                    .filter(grant -> !grant.grantId().equals(grantId)).toList();
            if (remaining.size() == current.treasuryGrants().size()) {
                return;
            }
            final RewardBatch updated = new RewardBatch(
                    current.batchId(), current.closingSeason(), current.openedSeason(),
                    current.champion(), current.championPoints(), current.championTreasuryReward(),
                    current.standings(), remaining,
                    current.monumentPending(), current.announcementPending(), current.storyPending());
            pendingRewardBatch = updated;
            if (!writeStateLocked()) {
                pendingRewardBatch = current;
            }
        }
    }

    private void acknowledgeBatchFlag(final UUID batchId, final String flag) {
        updateBatchFlag(batchId, flag, false);
    }

    private boolean claimBestEffortBatchFlag(final UUID batchId, final String flag) {
        return updateBatchFlag(batchId, flag, false);
    }

    private boolean updateBatchFlag(final UUID batchId, final String flag, final boolean value) {
        synchronized (stateLock) {
            final RewardBatch current = pendingRewardBatch;
            if (current == null || !current.batchId().equals(batchId)) {
                return false;
            }
            final boolean oldValue = switch (flag) {
                case "monument" -> current.monumentPending();
                case "announcement" -> current.announcementPending();
                case "story" -> current.storyPending();
                default -> throw new IllegalArgumentException("Unknown reward batch flag: " + flag);
            };
            if (oldValue == value) {
                return false;
            }
            final RewardBatch updated = new RewardBatch(
                    current.batchId(), current.closingSeason(), current.openedSeason(),
                    current.champion(), current.championPoints(), current.championTreasuryReward(),
                    current.standings(), current.treasuryGrants(),
                    "monument".equals(flag) ? value : current.monumentPending(),
                    "announcement".equals(flag) ? value : current.announcementPending(),
                    "story".equals(flag) ? value : current.storyPending());
            pendingRewardBatch = updated;
            if (!writeStateLocked()) {
                pendingRewardBatch = current;
                return false;
            }
            return true;
        }
    }

    private void clearCompletedRewardBatch() {
        synchronized (stateLock) {
            final RewardBatch current = pendingRewardBatch;
            if (current == null || !current.treasuryGrants().isEmpty()
                    || current.monumentPending() || current.announcementPending() || current.storyPending()) {
                return;
            }
            pendingRewardBatch = null;
            if (!writeStateLocked()) {
                pendingRewardBatch = current;
            }
        }
    }

    private void queueOnlineMemberClaims() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!hasPendingClaim(player.getUniqueId()) || !claimDeliveryQueued.add(player.getUniqueId())) {
                continue;
            }
            player.getScheduler().run(plugin, task -> {
                try {
                    deliverMemberClaim(player);
                } finally {
                    claimDeliveryQueued.remove(player.getUniqueId());
                }
            }, null);
        }
    }

    private boolean hasPendingClaim(final UUID recipient) {
        synchronized (stateLock) {
            return pendingMemberClaims.values().stream()
                    .anyMatch(claim -> claim.recipient().equals(recipient));
        }
    }

    @org.bukkit.event.EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        deliverMemberClaim(event.getPlayer());
    }

    private void deliverMemberClaim(final Player player) {
        final MemberRewardClaim claim;
        synchronized (stateLock) {
            claim = pendingMemberClaims.values().stream()
                    .filter(candidate -> candidate.recipient().equals(player.getUniqueId()))
                    .sorted(java.util.Comparator.comparingInt(MemberRewardClaim::closingSeason))
                    .findFirst().orElse(null);
        }
        if (claim == null) {
            return;
        }

        final Set<UUID> receipts;
        try {
            receipts = seasonRewardReceipts(player);
        } catch (final IllegalStateException corruptReceipt) {
            plugin.getLogger().severe("Season reward delivery fail-closed for " + player.getUniqueId()
                    + ": " + corruptReceipt.getMessage());
            return;
        }
        final SeasonRewardStateData.DeliveryDecision decision = SeasonRewardStateData.deliveryDecision(
                claim.recipient(), player.getUniqueId(), claim.grantId(), receipts);
        if (decision == SeasonRewardStateData.DeliveryDecision.WRONG_RECIPIENT) {
            return;
        }
        if (decision == SeasonRewardStateData.DeliveryDecision.ACKNOWLEDGE) {
            if (persistPlayer(player) && acknowledgeMemberClaim(claim.grantId())) {
                celebrateMemberClaim(player, claim, true);
            }
            return;
        }
        if (!canFitAll(player.getInventory(), claim.items())) {
            player.sendMessage(messageManager.getMessage(
                    "season-champion-member-inventory-full",
                    "<yellow>🏆 A szezonjutalmad várakozik. Szabadíts fel elegendő inventoryhelyet!</yellow>"));
            return;
        }

        final ItemStack[] inventoryBefore = cloneStorageContents(player.getInventory());
        final Map<PotionEffectType, PotionEffect> effectsBefore = snapshotPotionEffects(player);
        final Set<UUID> receiptsBefore = Set.copyOf(receipts);
        try {
            for (final ItemStack stack : rewardStacks(claim.items())) {
                final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
                if (!leftovers.isEmpty()) {
                    player.getInventory().setStorageContents(inventoryBefore);
                    return;
                }
            }
            if (claim.buffTicks() > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        claim.buffTicks(), 0, false, true, true), true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        claim.buffTicks(), 0, false, true, true), true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
                        claim.buffTicks(), 0, false, true, true), true);
            }
            final Set<UUID> committedReceipts = new HashSet<>(receipts);
            committedReceipts.add(claim.grantId());
            setSeasonRewardReceipts(player, committedReceipts);

            if (!persistPlayer(player)) {
                player.getInventory().setStorageContents(inventoryBefore);
                restorePotionEffects(player, effectsBefore);
                setSeasonRewardReceipts(player, receiptsBefore);
                return;
            }
        } catch (final RuntimeException | Error failure) {
            player.getInventory().setStorageContents(inventoryBefore);
            restorePotionEffects(player, effectsBefore);
            setSeasonRewardReceipts(player, receiptsBefore);
            throw failure;
        }
        if (acknowledgeMemberClaim(claim.grantId())) {
            celebrateMemberClaim(player, claim, false);
        }
    }

    private boolean canFitAll(final PlayerInventory inventory, final List<RewardItem> items) {
        final ItemStack[] simulated = cloneStorageContents(inventory);
        for (final ItemStack stack : rewardStacks(items)) {
            int remaining = stack.getAmount();
            final int max = Math.max(1, stack.getMaxStackSize());
            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                final ItemStack existing = simulated[slot];
                if (existing != null && !existing.getType().isAir() && existing.isSimilar(stack)) {
                    final int moved = Math.min(remaining, Math.max(0, max - existing.getAmount()));
                    existing.setAmount(existing.getAmount() + moved);
                    remaining -= moved;
                }
            }
            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                final ItemStack existing = simulated[slot];
                if (existing == null || existing.getType().isAir()) {
                    final int moved = Math.min(remaining, max);
                    simulated[slot] = new ItemStack(stack.getType(), moved);
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> rewardStacks(final List<RewardItem> items) {
        final List<ItemStack> stacks = new ArrayList<>();
        for (final RewardItem item : items) {
            int remaining = item.amount();
            final int max = Math.max(1, item.material().getMaxStackSize());
            while (remaining > 0) {
                final int amount = Math.min(remaining, max);
                stacks.add(new ItemStack(item.material(), amount));
                remaining -= amount;
            }
        }
        return stacks;
    }

    private ItemStack[] cloneStorageContents(final PlayerInventory inventory) {
        final ItemStack[] contents = inventory.getStorageContents();
        final ItemStack[] clone = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            clone[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return clone;
    }

    private Map<PotionEffectType, PotionEffect> snapshotPotionEffects(final Player player) {
        final Map<PotionEffectType, PotionEffect> snapshot = new LinkedHashMap<>();
        for (final PotionEffectType type : List.of(
                PotionEffectType.STRENGTH, PotionEffectType.REGENERATION,
                PotionEffectType.HERO_OF_THE_VILLAGE)) {
            snapshot.put(type, player.getPotionEffect(type));
        }
        return snapshot;
    }

    private void restorePotionEffects(final Player player,
                                      final Map<PotionEffectType, PotionEffect> snapshot) {
        for (final PotionEffectType type : snapshot.keySet()) {
            player.removePotionEffect(type);
            final PotionEffect previous = snapshot.get(type);
            if (previous != null) {
                player.addPotionEffect(previous, true);
            }
        }
    }

    private Set<UUID> seasonRewardReceipts(final Player player) {
        final String raw = player.getPersistentDataContainer().get(
                seasonRewardReceiptKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new HashSet<>();
        }
        final Set<UUID> receipts = new HashSet<>();
        for (final String token : raw.split(";")) {
            try {
                receipts.add(UUID.fromString(token));
            } catch (final IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid playerdata receipt token", invalid);
            }
        }
        return receipts;
    }

    private void setSeasonRewardReceipts(final Player player, final Set<UUID> receipts) {
        if (receipts.isEmpty()) {
            player.getPersistentDataContainer().remove(seasonRewardReceiptKey);
            return;
        }
        final String encoded = receipts.stream().map(UUID::toString).sorted()
                .collect(java.util.stream.Collectors.joining(";"));
        player.getPersistentDataContainer().set(
                seasonRewardReceiptKey, PersistentDataType.STRING, encoded);
    }

    private boolean persistPlayer(final Player player) {
        if (!playerSaveSupported) {
            return false;
        }
        try {
            player.saveData();
            playerSaveWarningSent.set(false);
            return true;
        } catch (final LinkageError unsupported) {
            playerSaveSupported = false;
            if (playerSaveWarningSent.compareAndSet(false, true)) {
                plugin.getLogger().severe("Player.saveData() is unavailable; season member rewards remain pending: "
                        + unsupported.getMessage());
            }
            return false;
        } catch (final RuntimeException failure) {
            if (playerSaveWarningSent.compareAndSet(false, true)) {
                plugin.getLogger().severe("Season member playerdata save failed; durable claim remains pending: "
                        + failure.getMessage());
            }
            return false;
        }
    }

    private boolean acknowledgeMemberClaim(final UUID grantId) {
        synchronized (stateLock) {
            final MemberRewardClaim claim = pendingMemberClaims.remove(grantId);
            if (claim == null) {
                return true;
            }
            if (!writeStateLocked()) {
                pendingMemberClaims.put(grantId, claim);
                return false;
            }
            return true;
        }
    }

    private void celebrateMemberClaim(final Player player, final MemberRewardClaim claim,
                                      final boolean recoveredReceipt) {
        if (claim.firework()) {
            spawnCelebrationFirework(player);
        }
        player.sendMessage(messageManager.getMessage(
                recoveredReceipt ? "season-champion-member-late" : "season-champion-member",
                recoveredReceipt
                        ? "<gold>🏆 A frakciód megnyerte az előző szezont — a győzelmi jutalmad tartós nyugtáját helyreállítottuk.</gold>"
                        : "<gold>🏆 A frakciód lett a szezon bajnoka — fogadd a győzelmi jutalmadat!</gold>"));
    }

    private RewardBatch loadRewardBatch(final YamlConfiguration yaml) {
        final ConfigurationSection section = yaml.getConfigurationSection("season.reward-batch");
        if (section == null) {
            return null;
        }
        try {
            if (section.getInt("schema-version", -1) != 1
                    || !section.contains("id")
                    || !section.contains("closing-season")
                    || !section.contains("opened-season")
                    || !section.contains("champion")
                    || !section.contains("champion-points")
                    || !section.contains("champion-treasury-reward")
                    || !section.isList("standings")
                    || !section.isList("treasury-grants")
                    || !section.isBoolean("monument-pending")
                    || !section.isBoolean("announcement-pending")
                    || !section.isBoolean("story-pending")) {
                throw new IllegalArgumentException("incomplete reward-batch schema");
            }
            final UUID batchId = UUID.fromString(section.getString("id", ""));
            final int closingSeason = section.getInt("closing-season", -1);
            final int openedSeason = section.getInt("opened-season", -1);
            SeasonRewardStateData.validateBatchGeneration(seasonNumber, closingSeason, openedSeason);
            final String rawChampion = section.getString("champion", "");
            final FactionType champion = rawChampion.isBlank() ? null : FactionType.fromInput(rawChampion);
            if (!rawChampion.isBlank() && champion == null) {
                throw new IllegalArgumentException("unknown champion");
            }
            final int championPoints = section.getInt("champion-points", 0);
            if (championPoints < 0) {
                throw new IllegalArgumentException("negative champion points");
            }
            final List<Standing> standings = new ArrayList<>();
            for (final Map<?, ?> raw : section.getMapList("standings")) {
                final FactionType faction = FactionType.fromInput(String.valueOf(raw.get("faction")));
                final Object pointsValue = raw.get("points");
                if (faction == null || !(pointsValue instanceof Number number) || number.intValue() < 0) {
                    throw new IllegalArgumentException("invalid standing");
                }
                standings.add(new Standing(faction, number.intValue()));
            }
            final List<TreasuryGrant> grants = new ArrayList<>();
            for (final Map<?, ?> raw : section.getMapList("treasury-grants")) {
                final String grantId = String.valueOf(raw.get("id"));
                final FactionType faction = FactionType.fromInput(String.valueOf(raw.get("faction")));
                final Object amountValue = raw.get("amount");
                if (grantId.isBlank() || faction == null || !(amountValue instanceof Number number)
                        || !Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0.0D) {
                    throw new IllegalArgumentException("invalid treasury grant");
                }
                grants.add(new TreasuryGrant(grantId, faction, number.doubleValue()));
            }
            final double championReward = section.getDouble("champion-treasury-reward", 0.0D);
            if (!Double.isFinite(championReward) || championReward < 0.0D) {
                throw new IllegalArgumentException("invalid champion treasury reward");
            }
            return new RewardBatch(batchId, closingSeason, openedSeason, champion, championPoints,
                    championReward, standings, grants, section.getBoolean("monument-pending", false),
                    section.getBoolean("announcement-pending", false),
                    section.getBoolean("story-pending", false));
        } catch (final RuntimeException invalid) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Érvénytelen season reward-batch: " + invalid.getMessage());
            return null;
        }
    }

    private void loadMemberClaims(final YamlConfiguration yaml) {
        final ConfigurationSection claims = yaml.getConfigurationSection("season.member-claims");
        if (claims == null) {
            return;
        }
        for (final String key : claims.getKeys(false)) {
            try {
                final UUID grantId = UUID.fromString(key);
                final UUID recipient = UUID.fromString(claims.getString(key + ".recipient", ""));
                final int closingSeason = claims.getInt(key + ".closing-season", -1);
                final int buffTicks = claims.getInt(key + ".buff-ticks", -1);
                if (!claims.isList(key + ".items") || !claims.isBoolean(key + ".firework")
                        || closingSeason < 1 || closingSeason >= seasonNumber || buffTicks < 0) {
                    throw new IllegalArgumentException("invalid claim generation/duration");
                }
                final List<RewardItem> items = new ArrayList<>();
                for (final String raw : claims.getStringList(key + ".items")) {
                    final String[] parts = raw.split(":", -1);
                    final Material material = parts.length == 2 ? Material.matchMaterial(parts[0]) : null;
                    final int amount = parts.length == 2 ? Integer.parseInt(parts[1]) : -1;
                    items.add(new RewardItem(material, amount));
                }
                pendingMemberClaims.put(grantId, new MemberRewardClaim(
                        grantId, recipient, closingSeason, items, buffTicks,
                        claims.getBoolean(key + ".firework", false)));
            } catch (final RuntimeException invalid) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Érvénytelen season member claim (" + key + "): " + invalid.getMessage());
                return;
            }
        }
    }

    private void writeRewardBatch(final YamlConfiguration yaml, final RewardBatch batch) {
        if (batch == null) {
            return;
        }
        final String path = "season.reward-batch";
        yaml.set(path + ".schema-version", 1);
        yaml.set(path + ".id", batch.batchId().toString());
        yaml.set(path + ".closing-season", batch.closingSeason());
        yaml.set(path + ".opened-season", batch.openedSeason());
        yaml.set(path + ".champion", batch.champion() == null ? "" : batch.champion().name());
        yaml.set(path + ".champion-points", batch.championPoints());
        yaml.set(path + ".champion-treasury-reward", batch.championTreasuryReward());
        yaml.set(path + ".monument-pending", batch.monumentPending());
        yaml.set(path + ".announcement-pending", batch.announcementPending());
        yaml.set(path + ".story-pending", batch.storyPending());
        yaml.set(path + ".standings", batch.standings().stream().map(standing -> Map.of(
                "faction", standing.faction().name(), "points", standing.points())).toList());
        yaml.set(path + ".treasury-grants", batch.treasuryGrants().stream().map(grant -> Map.of(
                "id", grant.grantId(), "faction", grant.faction().name(),
                "amount", grant.amount())).toList());
    }

    /** Spawns a short celebratory firework at the player. */
    private void spawnCelebrationFirework(final Player player) {
        final Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        final FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.WHITE)
                .withFade(Color.ORANGE)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
}
