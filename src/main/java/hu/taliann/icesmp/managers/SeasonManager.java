package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Seasonal league: factions earn points from raid victories and world boss kills over a
 * configurable season. State persists to season.yml; expiry is checked on the global tick.
 */
public final class SeasonManager implements PersistentStore, org.bukkit.event.Listener {

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
    /** Offline bajnok-tagok függő tárgyjutalma. */
    private final java.util.Set<java.util.UUID> pendingChampionSpoils = ConcurrentHashMap.newKeySet();
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
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        synchronized (stateLock) {
            points.clear();
            appliedGrants.clear();
            seasonStart = System.currentTimeMillis();

            if (!storageFile.exists()) {
                writeStateLocked();
                return;
            }

            try {
                final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
                seasonStart = yaml.getLong("season.start", System.currentTimeMillis());
                seasonNumber = Math.max(1, yaml.getInt("season.number", 1));
                final ConfigurationSection grants = yaml.getConfigurationSection("season.applied-grants");
                if (grants != null) {
                    for (final String key : grants.getKeys(false)) {
                        appliedGrants.put(key, grants.getLong(key, System.currentTimeMillis()));
                    }
                }
                final ConfigurationSection pointsSection = yaml.getConfigurationSection("season.points");
                if (pointsSection != null) {
                    for (final String factionKey : pointsSection.getKeys(false)) {
                        final FactionType faction = FactionType.fromInput(factionKey);
                        if (faction != null) {
                            points.put(faction, Math.max(0, pointsSection.getInt(factionKey, 0)));
                        }
                    }
                }
                pendingChampionSpoils.clear();
                for (final String uuid : yaml.getStringList("season.pending-champion-spoils")) {
                    try {
                        pendingChampionSpoils.add(java.util.UUID.fromString(uuid));
                    } catch (final IllegalArgumentException ignored) {
                        // Sérült bejegyzés — kihagyjuk.
                    }
                }
            } catch (final Exception exception) {
                plugin.getLogger().severe("Failed to load season.yml: " + exception.getMessage());
            }
        }
    }

    public void save() {
        synchronized (stateLock) {
            writeStateLocked();
        }
    }

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
            if (!pendingChampionSpoils.isEmpty()) {
                yaml.set("season.pending-champion-spoils",
                        pendingChampionSpoils.stream().map(java.util.UUID::toString).toList());
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
            }
        } catch (final RuntimeException blocked) {
            plugin.getLogger().severe("Szezonzárás elhalasztva: " + blocked.getMessage());
        }
    }

    /**
     * Performs the existing season-close side effects and commits the new season generation.
     * The community coordinator holds its monitor for the whole callback, so community
     * contributions cannot cross the standings snapshot/reset boundary.
     *
     * <p>Champion reward delivery is still the separate HIGH-05 state-machine scope: side effects
     * intentionally retain their previous ordering here. This method only reports success after the
     * season number, start and carried points are durably written.</p>
     */
    private boolean closeExpiredSeason(final int expectedSeason, final int openedSeason) {
        final java.util.Map<FactionType, Integer> closingPoints;
        synchronized (stateLock) {
            if (seasonNumber != expectedSeason
                    || System.currentTimeMillis() < getSeasonEndMillisLocked()) {
                return false;
            }
            closingPoints = new java.util.EnumMap<>(FactionType.class);
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

        final java.util.List<Map.Entry<FactionType, Integer>> standings = closingPoints.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<FactionType, Integer>comparingByValue().reversed())
                .toList();
        if (!standings.isEmpty()) {
            final StringBuilder summary = new StringBuilder();
            for (int i = 0; i < standings.size(); i++) {
                if (i > 0) {
                    summary.append(" <gray>•</gray> ");
                }
                summary.append(i + 1).append(". ")
                        .append(standings.get(i).getKey().getDisplayName())
                        .append(" <gray>(").append(standings.get(i).getValue()).append(")</gray>");
            }
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-final-standings",
                    "<gold>🏁 Szezon-végeredmény: {standings}</gold>",
                    Map.of("standings", summary.toString())));
        }

        final SeasonStoryTeller storyRef = storyTeller;
        if (champion == null || tie || best <= 0) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended-no-champion",
                    "<gold>🏁 A szezon véget ért bajnok nélkül — új szezon kezdődik!</gold>"));
            if (storyRef != null) {
                storyRef.tellTransition(null);
            }
        } else {
            final double reward = Math.max(0.0D,
                    configManager.getDouble("world-events.season.treasury-reward", 1000.0D));
            if (reward > 0.0D) {
                treasuryManager.deposit(champion, reward);
                final java.util.List<Double> ratios = configManager.getDoubleList(
                        "world-events.season.runner-up-ratios");
                final java.util.List<Double> liveRatios = ratios.isEmpty()
                        ? java.util.List.of(0.5D, 0.25D) : ratios;
                for (int i = 1; i < standings.size() && i - 1 < liveRatios.size(); i++) {
                    final double share = reward * Math.max(0.0D, liveRatios.get(i - 1));
                    if (share > 0.0D) {
                        treasuryManager.deposit(standings.get(i).getKey(), share);
                    }
                }
            }

            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended",
                    "<gold>🏆 A szezon bajnoka: <white>{champion}</white> ({points} pont)! A frakciókassza <white>{reward}</white> jutalmat kap. Új szezon kezdődik!</gold>",
                    Map.of(
                            "champion", champion.getDisplayName(),
                            "points", String.valueOf(best),
                            "reward", String.valueOf(reward))));

            awardChampionMembers(champion);
            if (storyRef != null) {
                storyRef.tellTransition(champion);
            }
            final SeasonMonumentManager monumentRef = monumentManager;
            if (monumentRef != null) {
                monumentRef.recordSeason(champion);
            }
        }

        final int openedChapter;
        synchronized (stateLock) {
            if (seasonNumber != expectedSeason) {
                return false;
            }
            final java.util.EnumMap<FactionType, Integer> previousPoints =
                    new java.util.EnumMap<>(FactionType.class);
            previousPoints.putAll(points);
            final long previousStart = seasonStart;
            final int previousNumber = seasonNumber;
            final boolean previousFinale = grandFinaleAnnounced;

            // The closing snapshot is fixed while community contributions are blocked. Other point
            // sources may still arrive; their delta above the snapshot belongs to the opened season.
            final java.util.EnumMap<FactionType, Integer> carry =
                    new java.util.EnumMap<>(FactionType.class);
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
            openedChapter = seasonNumber;
            grandFinaleAnnounced = false;

            try {
                if (!writeStateLocked()) {
                    points.clear();
                    points.putAll(previousPoints);
                    seasonStart = previousStart;
                    seasonNumber = previousNumber;
                    grandFinaleAnnounced = previousFinale;
                    return false;
                }
            } catch (final RuntimeException | Error failure) {
                points.clear();
                points.putAll(previousPoints);
                seasonStart = previousStart;
                seasonNumber = previousNumber;
                grandFinaleAnnounced = previousFinale;
                throw failure;
            }
        }

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "season-chapter-opened",
                "<gold>📖 Új fejezet nyílik a krónikában: <white>{chapter}. fejezet</white> — a régi fejezet küldetései lezárultak, újak várnak!</gold>",
                Map.of("chapter", String.valueOf(openedChapter))));
        return true;
    }

    /** Grants the champion faction's online members their season spoils. */
    private void awardChampionMembers(final FactionType champion) {
        final int buffMinutes = Math.max(0,
                configManager.getInt("world-events.season.champion-buff-minutes", 30));
        final java.util.List<String> rewardItems = configManager.getStringList(
                "world-events.season.champion-reward-items");
        final boolean firework = configManager.getBoolean(
                "world-events.season.champion-firework", true);

        for (final Map.Entry<java.util.UUID, FactionType> member
                : factionManager.getFactionAssignments().entrySet()) {
            if (member.getValue() == champion && Bukkit.getPlayer(member.getKey()) == null) {
                pendingChampionSpoils.add(member.getKey());
            }
        }

        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (factionManager.getFaction(online.getUniqueId()) != champion) {
                continue;
            }

            online.getScheduler().run(plugin, task -> {
                if (buffMinutes > 0) {
                    final int durationTicks = buffMinutes * 60 * 20;
                    online.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                            durationTicks, 0, false, true, true));
                    online.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                            durationTicks, 0, false, true, true));
                    online.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
                            durationTicks, 0, false, true, true));
                }

                giveRewardItems(online, rewardItems);

                if (firework) {
                    spawnCelebrationFirework(online);
                }

                online.sendMessage(messageManager.getMessage(
                        "season-champion-member",
                        "<gold>🏆 A frakciód lett a szezon bajnoka — fogadd a győzelmi jutalmadat!</gold>"));
            }, null);
        }
    }

    /** Offline bajnok-tag belépéskor kapja meg a tárgy-jutalmát. */
    @org.bukkit.event.EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        if (!pendingChampionSpoils.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        giveRewardItems(event.getPlayer(), configManager.getStringList(
                "world-events.season.champion-reward-items"));
        event.getPlayer().sendMessage(messageManager.getMessage(
                "season-champion-member-late",
                "<gold>🏆 A frakciód megnyerte az előző szezont — a győzelmi jutalmad megőriztük, fogadd!</gold>"));
        requestSave();
    }

    /** Hands over the configured MATERIAL:AMOUNT reward items, dropping overflow. */
    private void giveRewardItems(final Player player, final java.util.List<String> rewardItems) {
        for (final String entry : rewardItems) {
            final String[] parts = entry.split(":");
            final Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir()) {
                continue;
            }
            int amount = 1;
            if (parts.length >= 2) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (final NumberFormatException ignored) {
                    // Malformed amount: give one.
                }
            }
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(
                    new ItemStack(material, amount));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(
                    player.getLocation(), item));
        }
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
