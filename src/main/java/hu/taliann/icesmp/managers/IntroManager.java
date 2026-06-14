package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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

    public IntroManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.introSeenKey = new NamespacedKey(plugin, "intro_seen");
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
