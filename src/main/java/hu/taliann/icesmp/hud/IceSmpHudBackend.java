package hu.taliann.icesmp.hud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Player-region-thread-owned first-party HUD delivery backend. */
public final class IceSmpHudBackend {

    private final JavaPlugin plugin;
    private final Predicate<UUID> resourcePackReady;
    private final IceSmpHudRenderer renderer = new IceSmpHudRenderer();
    private final SurvivalHudRenderer survivalRenderer = new SurvivalHudRenderer();
    private final TargetHudRenderer targetRenderer = new TargetHudRenderer();
    private final PartyHudRenderer partyRenderer = new PartyHudRenderer();
    private final ConcurrentHashMap<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastFallbackWarning = new ConcurrentHashMap<>();

    public IceSmpHudBackend(final JavaPlugin plugin, final Predicate<UUID> resourcePackReady) {
        this.plugin = plugin;
        this.resourcePackReady = resourcePackReady;
    }

    public boolean render(final Player player, final IceSmpHudModel model, final boolean visible) {
        return render(player, model, HudLayoutSnapshot.defaults(), visible);
    }

    public boolean render(final Player player, final IceSmpHudModel model,
                          final HudLayoutSnapshot layout, final boolean visible) {
        return render(player, model, layout, null, visible);
    }

    public boolean render(final Player player, final IceSmpHudModel model,
                          final HudLayoutSnapshot layout, final HudComponent highlighted,
                          final boolean visible) {
        return render(player, model, layout, highlighted, visible,
                null, null, null, false);
    }

    /** Composes class, player, target and party surfaces into one invisible carrier boss bar. */
    public boolean render(final Player player, final IceSmpHudModel model,
                          final HudLayoutSnapshot layout, final HudComponent highlighted,
                          final boolean classVisible,
                          final PlayerHudState playerState,
                          final TargetHudState targetState,
                          final PartyHudState partyState,
                          final boolean playerFrameVisible) {
        if (player == null || (!classVisible && !playerFrameVisible)
                || !resourcePackReady.test(player.getUniqueId())) {
            hide(player);
            return false;
        }
        final net.kyori.adventure.text.TextComponent.Builder composed = Component.text();
        if (classVisible && model != null) {
            try {
                composed.append(renderer.render(model, layout, highlighted));
            } catch (final RuntimeException classRenderFailure) {
                warnFallback(player, "class", classRenderFailure);
            }
        }
        if (playerFrameVisible && playerState != null) {
            try {
                composed.append(survivalRenderer.render(playerState, layout, highlighted));
            } catch (final RuntimeException survivalRenderFailure) {
                composed.append(survivalRenderer.fallback(playerState));
                warnFallback(player, "survival", survivalRenderFailure);
            }
            try {
                composed.append(targetRenderer.render(targetState, layout, highlighted));
            } catch (final RuntimeException targetRenderFailure) {
                warnFallback(player, "target", targetRenderFailure);
            }
            try {
                composed.append(partyRenderer.render(partyState, layout, highlighted));
            } catch (final RuntimeException partyRenderFailure) {
                warnFallback(player, "party", partyRenderFailure);
            }
        }
        final Component rendered = composed.build();
        final BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> BossBar.bossBar(
                rendered, 0.0F, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
        bar.name(rendered);
        bar.progress(0.0F);
        player.showBossBar(bar);
        return true;
    }

    private void warnFallback(final Player player, final String layer,
                              final RuntimeException failure) {
        final long now = System.currentTimeMillis();
        final Long previous = lastFallbackWarning.put(player.getUniqueId(), now);
        if (previous == null || now - previous >= 60_000L) {
            plugin.getLogger().warning("IceSMP " + layer + " HUD fallback ["
                    + player.getName() + "]: " + failure);
        }
    }

    public void hide(final Player player) {
        if (player == null) return;
        final BossBar bar = bars.remove(player.getUniqueId());
        lastFallbackWarning.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    public void close() {
        for (final Player player : plugin.getServer().getOnlinePlayers()) hide(player);
        bars.clear();
        lastFallbackWarning.clear();
    }
}
