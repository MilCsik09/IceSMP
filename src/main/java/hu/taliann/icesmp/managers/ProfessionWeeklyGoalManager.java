package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I16 — szakma-céh heti közös cél: az azonos szakmát űzők FRAKCIÓ-FÜGGETLEN,
 * globális heti számlálót töltenek (a szakma-XP-termelés a hozzájárulás-egység —
 * a ProfessionXpListener hívja). A hét fordulásakor az elért célok hozzájárulói
 * (küszöb felett) szakma-XP jutalmat kapnak — online azonnal, offline belépéskor
 * (perzisztált függő jutalom). Konkurrencia: AtomicLong számlálók + concurrent
 * mapek (a spec buktatója szerint), a mentés a heti tick-en és disable-kor fut.
 */
public final class ProfessionWeeklyGoalManager implements PersistentStore, Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;
    private final File storageFile;

    private volatile long week;
    private final Map<ProfessionType, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<ProfessionType, Map<UUID, AtomicLong>> contributors = new ConcurrentHashMap<>();
    /** uuid -> (profession-id -> járó XP) — offline hozzájárulók belépéskor kapják. */
    private final Map<UUID, Map<String, Integer>> pendingRewards = new ConcurrentHashMap<>();

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
    public void add(final Player player, final ProfessionType profession, final int units) {
        if (units <= 0 || !configManager.getBoolean("profession-weekly.enabled", true)) {
            return;
        }
        counters.computeIfAbsent(profession, key -> new AtomicLong()).addAndGet(units);
        contributors.computeIfAbsent(profession, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(player.getUniqueId(), key -> new AtomicLong()).addAndGet(units);
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
        evaluateWeek();
        week = now;
        counters.clear();
        contributors.clear();
        save();
    }

    private void evaluateWeek() {
        final int rewardXp = Math.max(0, configManager.getInt("profession-weekly.reward-xp", 300));
        final long minContribution = Math.max(1, configManager.getInt("profession-weekly.min-contribution", 100));
        for (final Map.Entry<ProfessionType, AtomicLong> entry : counters.entrySet()) {
            final ProfessionType profession = entry.getKey();
            final long goal = goalOf(profession);
            if (goal <= 0 || entry.getValue().get() < goal) {
                continue;
            }
            Bukkit.getServer().broadcast(messageManager.getMessage("profession-weekly-done",
                    "<gold>⚒ A(z) <white>{profession}</white> szakma-céh teljesítette a heti közös célt (<white>{total}</white> egység)! A hozzájárulók jutalma úton van.</gold>",
                    Map.of("profession", profession.getId(), "total", String.valueOf(entry.getValue().get()))));
            final Map<UUID, AtomicLong> profContribs = contributors.getOrDefault(profession, Map.of());
            for (final Map.Entry<UUID, AtomicLong> contributor : profContribs.entrySet()) {
                if (contributor.getValue().get() < minContribution || rewardXp <= 0) {
                    continue;
                }
                final Player online = Bukkit.getPlayer(contributor.getKey());
                if (online != null) {
                    // Folia: a jutalom a JÁTÉKOS saját régió-szálán íródik (PDC).
                    online.getScheduler().run(plugin, task -> {
                        professionManager.addXpFor(online, profession, rewardXp);
                        online.sendMessage(messageManager.getMessage("profession-weekly-reward",
                                "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                Map.of("xp", String.valueOf(rewardXp), "profession", profession.getId())));
                    }, null);
                } else {
                    pendingRewards.computeIfAbsent(contributor.getKey(), key -> new ConcurrentHashMap<>())
                            .merge(profession.getId(), rewardXp, Integer::sum);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Map<String, Integer> pending = pendingRewards.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        for (final Map.Entry<String, Integer> entry : pending.entrySet()) {
            final ProfessionType profession = ProfessionType.fromId(entry.getKey());
            if (profession != null) {
                professionManager.addXpFor(event.getPlayer(), profession, entry.getValue());
                event.getPlayer().sendMessage(messageManager.getMessage("profession-weekly-reward",
                        "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                        Map.of("xp", String.valueOf(entry.getValue()), "profession", entry.getKey())));
            }
        }
        save();
    }

    @Override
    public synchronized void load() {
        counters.clear();
        contributors.clear();
        pendingRewards.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
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
        final ConfigurationSection contribSection = yaml.getConfigurationSection("contributors");
        if (contribSection != null) {
            for (final String profId : contribSection.getKeys(false)) {
                final ProfessionType profession = ProfessionType.fromId(profId);
                final ConfigurationSection perPlayer = contribSection.getConfigurationSection(profId);
                if (profession == null || perPlayer == null) {
                    continue;
                }
                final Map<UUID, AtomicLong> map = new ConcurrentHashMap<>();
                for (final String uuid : perPlayer.getKeys(false)) {
                    try {
                        map.put(UUID.fromString(uuid), new AtomicLong(perPlayer.getLong(uuid)));
                    } catch (final IllegalArgumentException ignored) {
                        // sérült kulcs — kihagyjuk
                    }
                }
                contributors.put(profession, map);
            }
        }
        final ConfigurationSection pendingSection = yaml.getConfigurationSection("pending-rewards");
        if (pendingSection != null) {
            for (final String uuid : pendingSection.getKeys(false)) {
                final ConfigurationSection perProf = pendingSection.getConfigurationSection(uuid);
                if (perProf == null) {
                    continue;
                }
                try {
                    final Map<String, Integer> map = new ConcurrentHashMap<>();
                    for (final String profId : perProf.getKeys(false)) {
                        map.put(profId, perProf.getInt(profId));
                    }
                    pendingRewards.put(UUID.fromString(uuid), map);
                } catch (final IllegalArgumentException ignored) {
                    // sérült kulcs — kihagyjuk
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
            for (final Map.Entry<ProfessionType, Map<UUID, AtomicLong>> entry : contributors.entrySet()) {
                for (final Map.Entry<UUID, AtomicLong> contributor : entry.getValue().entrySet()) {
                    yaml.set("contributors." + entry.getKey().getId() + "." + contributor.getKey(),
                            contributor.getValue().get());
                }
            }
            for (final Map.Entry<UUID, Map<String, Integer>> entry : pendingRewards.entrySet()) {
                for (final Map.Entry<String, Integer> reward : entry.getValue().entrySet()) {
                    yaml.set("pending-rewards." + entry.getKey() + "." + reward.getKey(), reward.getValue());
                }
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save profession-weekly.yml: " + exception.getMessage());
        }
    }
}
