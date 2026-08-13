package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pack-independent stability projection; readable with first-party HUD enabled or absent. */
public final class PrologueHudController {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueManager state;
    private final PrologueWorldAccess worldAccess;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private volatile long nextAmbientAt;
    private volatile boolean eventActive;

    public PrologueHudController(final JavaPlugin plugin, final ConfigManager config,
                                 final PrologueManager state,
                                 final PrologueWorldAccess worldAccess) {
        this.plugin = plugin;
        this.config = config;
        this.state = state;
        this.worldAccess = worldAccess;
    }

    public void setEventActive(final boolean active) { eventActive = active; }

    public void tick() {
        if (!config.getBoolean("world-events.prologue.stability-hud.enabled", true)) {
            hideAll();
            return;
        }
        final Location gate = worldAccess.gateAnchor();
        final boolean global = state.stage() == PrologueStage.COLLAPSE
                || state.state() == PrologueState.FINALE
                || state.state() == PrologueState.GATE_OPEN
                || eventActive;
        final double radius = Math.max(8.0D, config.getDouble(
                "world-events.prologue.stability-hud.radius", 96.0D));
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                final boolean show = global || PrologueWorldAccess.within(player.getLocation(), gate, radius);
                if (show) show(player); else hide(player);
            }, () -> bars.remove(player.getUniqueId()));
        }
        ambientPulse(gate);
    }

    private void show(final Player player) {
        final int stability = state.stability();
        final Component title = stability <= Math.max(0, config.getInt(
                "world-events.prologue.stability-hud.critical-threshold", 20))
                ? Component.text("Kárhozat Kapujának stabilitása: KRITIKUS", NamedTextColor.DARK_PURPLE)
                : Component.text("Kárhozat Kapujának stabilitása: " + stability + "%",
                        NamedTextColor.LIGHT_PURPLE);
        final BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored ->
                BossBar.bossBar(title, 0.0F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10));
        bar.name(title);
        bar.progress((float) Math.max(0.0D, Math.min(1.0D, stability / 100.0D)));
        player.showBossBar(bar);
    }

    private void hide(final Player player) {
        final BossBar bar = bars.get(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private void hideAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> hide(player), null);
        }
    }

    private void ambientPulse(final Location gate) {
        if (gate == null || gate.getWorld() == null || state.state().completed()) return;
        final long now = System.currentTimeMillis();
        if (now < nextAmbientAt) return;
        final long seconds = Math.max(8L, config.getLong(
                "world-events.prologue.ambient.interval-seconds", switch (state.stage()) {
                    case SILENCE -> 45L;
                    case CRACKS -> 30L;
                    case LEAK -> 18L;
                    case COLLAPSE -> 10L;
                }));
        nextAmbientAt = now + seconds * 1_000L;
        plugin.getServer().getRegionScheduler().run(plugin, gate, task -> {
            final int particles = switch (state.stage()) {
                case SILENCE -> 8;
                case CRACKS -> 16;
                case LEAK -> 28;
                case COLLAPSE -> 42;
            };
            gate.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    gate.clone().add(0.0D, 1.0D, 0.0D), particles,
                    1.2D, 1.8D, 1.2D, 0.04D);
            gate.getWorld().playSound(gate, Sound.BLOCK_PORTAL_AMBIENT,
                    state.stage() == PrologueStage.SILENCE ? 0.35F : 0.65F,
                    state.stage() == PrologueStage.COLLAPSE ? 0.55F : 0.85F);
        });
    }

    public void shutdown() {
        if (!plugin.isEnabled()) {
            bars.clear();
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.getScheduler().run(plugin, task -> {
                    final BossBar bar = bars.remove(player.getUniqueId());
                    if (bar != null) player.hideBossBar(bar);
                }, () -> bars.remove(player.getUniqueId()));
            } catch (final IllegalPluginAccessException disabledRace) {
                bars.remove(player.getUniqueId());
            }
        }
    }
}
