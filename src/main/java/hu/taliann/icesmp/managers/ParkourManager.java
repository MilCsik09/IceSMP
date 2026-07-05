package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parkour / trial courses (ROADMAP phase 12): admins lay out a course in the world
 * and register its start + finish; players run it timed, and finishing pays a
 * reward. Courses persist in parkour.yml. (The obstacles themselves are built
 * in-world — this is the tracking/reward framework.)
 */
public final class ParkourManager implements PersistentStore {

    /** A mutable course definition (start may be set before finish). */
    public static final class Course {
        private String name;
        private Location start;
        private Location finish;
        private double radius = 2.5D;
        private long reward = 100L;
    }

    private record Run(String courseId, long startMillis) { }

    private final JavaPlugin plugin;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Map<String, Course> courses = new ConcurrentHashMap<>();
    private final Map<UUID, Run> activeRuns = new ConcurrentHashMap<>();
    private java.util.function.BiConsumer<Player, String> finishHook;

    public ParkourManager(final JavaPlugin plugin, final CurrencyManager currencyManager,
                          final FactionManager factionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "parkour.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        courses.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection section = yaml.getConfigurationSection("courses");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final Course course = new Course();
            course.name = section.getString(id + ".name", id);
            course.start = section.getLocation(id + ".start");
            course.finish = section.getLocation(id + ".finish");
            course.radius = section.getDouble(id + ".radius", 2.5D);
            course.reward = section.getLong(id + ".reward", 100L);
            courses.put(id.toLowerCase(java.util.Locale.ROOT), course);
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Map.Entry<String, Course> entry : courses.entrySet()) {
                final String base = "courses." + entry.getKey();
                final Course course = entry.getValue();
                yaml.set(base + ".name", course.name);
                yaml.set(base + ".start", course.start);
                yaml.set(base + ".finish", course.finish);
                yaml.set(base + ".radius", course.radius);
                yaml.set(base + ".reward", course.reward);
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save parkour.yml: " + exception.getMessage());
        }
    }

    public Collection<String> getCourseIds() {
        return courses.keySet();
    }

    public boolean isReady(final String id) {
        final Course course = courses.get(id.toLowerCase(java.util.Locale.ROOT));
        return course != null && course.start != null && course.finish != null;
    }

    public void setStart(final String id, final Player admin, final String name) {
        final Course course = courses.computeIfAbsent(id.toLowerCase(java.util.Locale.ROOT), key -> new Course());
        if (course.name == null) {
            course.name = name == null || name.isBlank() ? id : name;
        }
        course.start = admin.getLocation();
        save();
    }

    public void setFinish(final String id, final Player admin, final double radius, final long reward) {
        final Course course = courses.computeIfAbsent(id.toLowerCase(java.util.Locale.ROOT), key -> new Course());
        if (course.name == null) {
            course.name = id;
        }
        course.finish = admin.getLocation();
        course.radius = Math.max(1.0D, radius);
        course.reward = Math.max(0L, reward);
        save();
    }

    public boolean remove(final String id) {
        final boolean removed = courses.remove(id.toLowerCase(java.util.Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Starts a timed run: teleports the player to the course start.
     *
     * @return null on success, otherwise a message key
     */
    public String start(final Player player, final String id) {
        final Course course = courses.get(id.toLowerCase(java.util.Locale.ROOT));
        if (course == null || course.start == null || course.finish == null) {
            return "parkour-not-ready";
        }
        player.teleportAsync(course.start);
        activeRuns.put(player.getUniqueId(), new Run(id.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis()));
        return null;
    }

    /** Sets the callback fired when a player finishes a course (player, course id). */
    public void setFinishHook(final java.util.function.BiConsumer<Player, String> hook) {
        this.finishHook = hook;
    }

    /** Checks whether the player has reached their active course's finish; rewards if so. */
    public void checkFinish(final Player player) {
        final Run run = activeRuns.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        final Course course = courses.get(run.courseId());
        if (course == null || course.finish == null || course.finish.getWorld() == null
                || !course.finish.getWorld().equals(player.getWorld())) {
            return;
        }
        if (player.getLocation().distanceSquared(course.finish) > course.radius * course.radius) {
            return;
        }

        activeRuns.remove(player.getUniqueId());
        final double seconds = (System.currentTimeMillis() - run.startMillis()) / 1000.0D;
        if (course.reward > 0) {
            final FactionType faction = factionManager.getFaction(player.getUniqueId());
            currencyManager.addToBalance(player.getUniqueId(), CurrencyType.fromFactionType(faction), course.reward);
        }
        // Quest bridge (PARKOUR_TRIAL objectives) — runs on the player's own thread.
        if (finishHook != null) {
            finishHook.accept(player, run.courseId());
        }
        player.sendMessage(messageManager.getMessage(
                "parkour-finished",
                "<gold>🏁 Teljesítetted: <yellow>{course}</yellow> &7| Idő: <white>{time}s</white> &7| Jutalom: <white>{reward}</white></gold>",
                Map.of(
                        "course", course.name,
                        "time", String.format(java.util.Locale.ROOT, "%.1f", seconds),
                        "reward", String.valueOf(course.reward)
                )));
    }

    public void cancel(final UUID playerId) {
        activeRuns.remove(playerId);
    }
}
