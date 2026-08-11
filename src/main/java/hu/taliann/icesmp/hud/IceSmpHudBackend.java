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
    private final ConcurrentHashMap<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public IceSmpHudBackend(final JavaPlugin plugin, final Predicate<UUID> resourcePackReady) {
        this.plugin = plugin;
        this.resourcePackReady = resourcePackReady;
    }

    public boolean render(final Player player, final IceSmpHudModel model, final boolean visible) {
        if (player == null || model == null || !visible || !resourcePackReady.test(player.getUniqueId())) {
            hide(player);
            return false;
        }
        final Component rendered = renderer.render(model);
        final BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> BossBar.bossBar(
                rendered, 0.0F, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
        bar.name(rendered);
        bar.progress(0.0F);
        player.showBossBar(bar);
        return true;
    }

    public void hide(final Player player) {
        if (player == null) return;
        final BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    public void close() {
        for (final Player player : plugin.getServer().getOnlinePlayers()) hide(player);
        bars.clear();
    }
}
