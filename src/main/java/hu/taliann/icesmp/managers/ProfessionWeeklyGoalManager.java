package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileWeeklyGoalStore;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I16 — szakma-céh heti közös cél: az azonos szakmát űzők FRAKCIÓ-FÜGGETLEN,
 * globális heti számlálót töltenek (a szakma-XP-termelés a hozzájárulás-egység —
 * a ProfessionXpListener hívja). A hét fordulásakor az elért célok hozzájárulói
 * (küszöb felett) szakma-XP jutalmat kapnak.
 *
 * <p>Autoritás-vágás: a hét-index és a globális számlálók megosztott aggregátumként
 * YAML-ban élnek; a játékosonkénti hozzájárulás, a jutalom-odaítélés és a függő
 * jutalom a PlayerProfile PROFESSIONS szekcióban, CAS-mutációval. A kiértékelés a
 * tartós profil-tulajdonos felsoroláson megy, ezért restart nem veszíthet
 * hozzájárulót; az odaítélés (player, hét) szinten idempotens.</p>
 */
public final class ProfessionWeeklyGoalManager implements PersistentStore, Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final PlayerProfileWeeklyGoalStore weeklyStore = new PlayerProfileWeeklyGoalStore();

    private volatile long week;
    private final Map<ProfessionType, AtomicLong> counters = new ConcurrentHashMap<>();
    private int pendingContributions;

    public ProfessionWeeklyGoalManager(final JavaPlugin plugin, final ConfigManager configManager,
                                       final ProfessionManager professionManager,
                                       final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.professionManager = professionManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "profession-weekly.yml");
        this.week = currentWeek();
    }

    private static long currentWeek() {
        return System.currentTimeMillis() / (7L * 86_400_000L);
    }

    /** Hozzájárulás (a ProfessionXpListener hívja a játékos saját régió-szálán). */
    public synchronized void add(final Player player, final ProfessionType profession, final int units) {
        if (units <= 0 || !configManager.getBoolean("profession-weekly.enabled", true)) {
            return;
        }
        if (PlayerProfileAuthority.installed().isEmpty()) {
            counters.computeIfAbsent(profession, key -> new AtomicLong()).addAndGet(units);
            return;
        }
        final UUID playerId = player.getUniqueId();
        final long contributionWeek = week;
        pendingContributions++;
        weeklyStore.recordContribution(playerId, profession, units, contributionWeek)
                .whenComplete((total, failure) -> {
                    completeContribution(playerId, profession, units, contributionWeek, failure);
                });
    }

    private synchronized void completeContribution(final UUID playerId, final ProfessionType profession,
                                                    final int units, final long contributionWeek,
                                                    final Throwable failure) {
        try {
            if (failure != null) {
                plugin.getLogger().severe("Heti céh-hozzájárulás mentése sikertelen: "
                        + playerId + ": " + failure.getMessage());
                return;
            }
            if (week == contributionWeek) {
                counters.computeIfAbsent(profession, key -> new AtomicLong()).addAndGet(units);
            }
        } finally {
            pendingContributions--;
        }
    }

    public long counterOf(final ProfessionType profession) {
        final AtomicLong counter = counters.get(profession);
        return counter == null ? 0L : counter.get();
    }

    public long goalOf(final ProfessionType profession) {
        return Math.max(0L, configManager.getInt(
                "profession-weekly.goals." + profession.getId(), 5000));
    }

    /** A world-events tick hívja: hét-forduláskor kiértékelés + reset. */
    public synchronized void tick() {
        final long now = currentWeek();
        if (now == week) {
            return;
        }
        // A hétváltás megvárja az előző hét már elindított durable contribution-commitjait.
        if (pendingContributions > 0) {
            return;
        }
        evaluateWeek(week);
        week = now;
        counters.clear();
        save();
    }

    private void evaluateWeek(final long evaluatedWeek) {
        final int rewardXp = Math.max(0, configManager.getInt("profession-weekly.reward-xp", 300));
        final long minContribution = Math.max(1, configManager.getInt("profession-weekly.min-contribution", 100));
        final Map<String, Integer> goalMetRewards = new LinkedHashMap<>();
        for (final Map.Entry<ProfessionType, AtomicLong> entry : counters.entrySet()) {
            final ProfessionType profession = entry.getKey();
            final long goal = goalOf(profession);
            if (goal <= 0 || entry.getValue().get() < goal) {
                continue;
            }
            Bukkit.getServer().broadcast(messageManager.getMessage("profession-weekly-done",
                    "<gold>⚒ A(z) <white>{profession}</white> szakma-céh teljesítette a heti közös célt (<white>{total}</white> egység)! A hozzájárulók jutalma úton van.</gold>",
                    Map.of("profession", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(profession.getDisplayName()),
                            "total", String.valueOf(entry.getValue().get()))));
            if (rewardXp > 0) {
                goalMetRewards.put(profession.getId(), rewardXp);
            }
        }
        if (goalMetRewards.isEmpty()) {
            return;
        }
        if (PlayerProfileAuthority.installed().isEmpty()) {
            plugin.getLogger().warning("Heti céh-kiértékelés kihagyva: nincs PlayerProfile authority.");
            return;
        }
        PlayerProfileAuthority.current().repository().listPlayerIds().whenComplete((owners, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Heti céh-kiértékelés: profil-felsorolás sikertelen: "
                        + failure.getMessage());
                return;
            }
            for (final UUID owner : owners) {
                weeklyStore.award(owner, evaluatedWeek, goalMetRewards, minContribution)
                        .whenComplete((awarded, awardFailure) -> {
                            if (awardFailure != null) {
                                plugin.getLogger().severe("Heti céh-jutalom odaítélése sikertelen: "
                                        + owner + ": " + awardFailure.getMessage());
                                return;
                            }
                            if (awarded.isEmpty()) {
                                return;
                            }
                            final Player online = Bukkit.getPlayer(owner);
                            if (online != null) {
                                deliverPending(online);
                            }
                        });
            }
        });
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (PlayerProfileAuthority.installed().isEmpty()) {
            return;
        }
        deliverPending(event.getPlayer());
    }

    private void deliverPending(final Player player) {
        final UUID playerId = player.getUniqueId();
        weeklyStore.claim(playerId, professionManager.baseXp(),
                professionManager.incrementPerLevel(), ProfessionManager.MAX_PROFESSION_LEVEL)
                .whenComplete((claimed, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Heti céh-jutalom jóváírása sikertelen: "
                                + playerId + ": " + failure.getMessage());
                        return;
                    }
                    if (claimed == null || claimed.isEmpty()) {
                        return;
                    }
                    professionManager.runOnOwnerThread(player, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        for (final PlayerProfileWeeklyGoalStore.ClaimedReward reward : claimed) {
                            player.sendMessage(messageManager.getMessage(
                                    "profession-weekly-reward",
                                    "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                    Map.of("xp", String.valueOf(reward.xp()),
                                            "profession", reward.professionId())));
                        }
                    });
                });
    }

    @Override
    public synchronized void load() {
        counters.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        week = yaml.getLong("week", currentWeek());
        final ConfigurationSection counterSection = yaml.getConfigurationSection("counters");
        if (counterSection != null) {
            for (final String key : counterSection.getKeys(false)) {
                final ProfessionType profession = ProfessionType.fromId(key);
                if (profession != null) {
                    counters.put(profession, new AtomicLong(counterSection.getLong(key)));
                }
            }
        }
    }

    @Override
    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("week", week);
            for (final Map.Entry<ProfessionType, AtomicLong> entry : counters.entrySet()) {
                yaml.set("counters." + entry.getKey().getId(), entry.getValue().get());
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save profession-weekly.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save profession-weekly.yml", exception);
        }
    }
}
