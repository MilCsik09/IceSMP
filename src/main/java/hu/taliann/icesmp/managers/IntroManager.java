package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;

/**
 * First-join intro sequence (ideas.md "Első belépés videó"): a vanilla-friendly
 * emulation of an intro cutscene — a timed sequence of title cards with sound,
 * scheduled on the joining player's own scheduler (Folia-correct). Plays once
 * (tracked by the 'intro_seen' PDC flag); admins can replay it.
 */
public final class IntroManager {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey introSeenKey;
    private final NamespacedKey cinematicKey;
    private final NamespacedKey prevGamemodeKey;

    public IntroManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.introSeenKey = new NamespacedKey(plugin, "intro_seen");
        this.cinematicKey = new NamespacedKey(plugin, "intro_cinematic");
        this.prevGamemodeKey = new NamespacedKey(plugin, "intro_prev_gamemode");
    }

    public boolean hasSeenIntro(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(introSeenKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /**
     * Plays the intro on first join if enabled and not seen yet.
     *
     * @param player the joining player
     */
    public void playOnFirstJoin(final Player player) {
        if (!configManager.getBoolean("world-events.intro.enabled", true) || hasSeenIntro(player)) {
            return;
        }

        player.getPersistentDataContainer().set(introSeenKey, PersistentDataType.BYTE, (byte) 1);
        play(player);
    }

    /**
     * Plays the intro title sequence now (used by first join and the admin replay).
     * Each configured line is shown after stagger-seconds * index, scheduled on
     * the player's own region scheduler.
     *
     * @param player the viewer
     */
    public void play(final Player player) {
        final List<String> lines = configManager.getStringList("world-events.intro.lines");
        if (lines.isEmpty()) {
            return;
        }

        final long staggerTicks = Math.max(1L, configManager.getLong("world-events.intro.stagger-seconds", 4L)) * 20L;
        for (int index = 0; index < lines.size(); index++) {
            final String rawLine = lines.get(index);
            final long delay = Math.max(1L, staggerTicks * index);
            player.getScheduler().runDelayed(plugin, task -> showCard(player, rawLine), null, delay);
        }

        playCinematic(player);
    }

    /**
     * Opt-in cinematic camera path: briefly puts the player in spectator and
     * flies them through configured waypoints, then restores their gamemode.
     * Disabled by default. Robust against interruption — the original gamemode is
     * stored in PDC and restored at the end AND on the next join (self-heal), so a
     * crash/disconnect can never strand a player in spectator.
     *
     * @param player the viewer
     */
    public void playCinematic(final Player player) {
        if (!configManager.getBoolean("world-events.intro.cinematic.enabled", false)) {
            return;
        }
        final List<String> waypoints = configManager.getStringList("world-events.intro.cinematic.waypoints");
        if (waypoints.isEmpty() || player.getPersistentDataContainer().has(cinematicKey, PersistentDataType.BYTE)) {
            return;
        }

        player.getPersistentDataContainer().set(prevGamemodeKey, PersistentDataType.STRING, player.getGameMode().name());
        player.getPersistentDataContainer().set(cinematicKey, PersistentDataType.BYTE, (byte) 1);
        player.setGameMode(GameMode.SPECTATOR);

        final long perTicks = Math.max(1L, configManager.getLong("world-events.intro.cinematic.point-seconds", 3L)) * 20L;
        for (int index = 0; index < waypoints.size(); index++) {
            final Location target = parseWaypoint(waypoints.get(index), player.getWorld());
            if (target == null) {
                continue;
            }
            player.getScheduler().runDelayed(plugin, task -> player.teleportAsync(target), null, Math.max(1L, perTicks * index));
        }

        // Restore at the end. If the player logs off first, the next-join guard restores instead.
        player.getScheduler().runDelayed(plugin, task -> restoreCinematicIfNeeded(player), null,
                Math.max(2L, perTicks * waypoints.size()));
    }

    /** Restores the gamemode after a cinematic (end of sequence, or on join self-heal). */
    public void restoreCinematicIfNeeded(final Player player) {
        if (!player.getPersistentDataContainer().has(cinematicKey, PersistentDataType.BYTE)) {
            return;
        }
        final String previous = player.getPersistentDataContainer().get(prevGamemodeKey, PersistentDataType.STRING);
        player.getPersistentDataContainer().remove(cinematicKey);
        player.getPersistentDataContainer().remove(prevGamemodeKey);

        GameMode mode = GameMode.SURVIVAL;
        if (previous != null) {
            try {
                mode = GameMode.valueOf(previous);
            } catch (final IllegalArgumentException ignored) {
                mode = GameMode.SURVIVAL;
            }
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(mode);
        }
    }

    private Location parseWaypoint(final String raw, final World defaultWorld) {
        final String[] parts = raw.split(",");
        try {
            if (parts.length >= 6) {
                final World world = Bukkit.getWorld(parts[0].trim());
                final World resolved = world == null ? defaultWorld : world;
                return new Location(resolved, Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim()), Float.parseFloat(parts[4].trim()), Float.parseFloat(parts[5].trim()));
            }
            if (parts.length == 5) {
                return new Location(defaultWorld, Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()), Float.parseFloat(parts[3].trim()), Float.parseFloat(parts[4].trim()));
            }
            if (parts.length >= 3) {
                return new Location(defaultWorld, Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()));
            }
        } catch (final NumberFormatException exception) {
            return null;
        }
        return null;
    }

    private void showCard(final Player player, final String rawLine) {
        // Intro lines are authored in config with legacy '&' codes as "Title||Subtitle".
        final String[] parts = rawLine.split("\\|\\|", 2);
        final Component titleLine = SECTION.deserialize(TextUtil.color(parts[0].trim()));
        final Component subtitleLine = parts.length > 1
                ? SECTION.deserialize(TextUtil.color(parts[1].trim()))
                : Component.empty();

        player.showTitle(Title.title(
                titleLine,
                subtitleLine,
                Title.Times.times(Duration.ofMillis(400L), Duration.ofMillis(3200L), Duration.ofMillis(400L))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7F, 1.2F);
    }
}
